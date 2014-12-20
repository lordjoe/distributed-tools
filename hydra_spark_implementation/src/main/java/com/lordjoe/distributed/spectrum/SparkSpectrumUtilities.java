package com.lordjoe.distributed.spectrum;

import com.lordjoe.distributed.*;
import com.lordjoe.distributed.input.*;
import org.apache.spark.api.java.*;
import org.apache.spark.api.java.function.Function;
import org.systemsbiology.xtandem.*;
import org.systemsbiology.xtandem.hadoop.*;
import scala.*;

import javax.annotation.*;
import java.lang.Boolean;

/**
 * com.lordjoe.distributed.spectrum.SparkSpectrumUtilities
 * User: Steve
 * Date: 9/24/2014
 */
public class SparkSpectrumUtilities {

    /**
     * parse a Faste File returning the comment > line as the key
     * and the rest as the value
     * @param path
     * @param ctx
     * @return
     */
    @Nonnull
    public static JavaPairRDD<String, String> parseFastaFile( @Nonnull String path, @Nonnull JavaSparkContext ctx) {
        Class inputFormatClass = FastaInputFormat.class;
        Class keyClass = String.class;
        Class valueClass = String.class;

        return ctx.newAPIHadoopFile(
                path,
                inputFormatClass,
                keyClass,
                valueClass,
                ctx.hadoopConfiguration()
        );

    }


    public static class MZXMLInputFormat extends XMLTagInputFormat {
        public MZXMLInputFormat() {
            super("scan");
        }
    }

    @Nonnull
    public static JavaPairRDD<String, IMeasuredSpectrum> parseSpectrumFile( @Nonnull String path ) {
        JavaSparkContext ctx = SparkUtilities.getCurrentContext();

        //     if(path.toLowerCase().endsWith(".mgf"))
        //           return parseAsTextMGF(path,ctx);     this will fail
        if (path.toLowerCase().endsWith(".mgf"))
            return parseAsMGF(path, ctx);
        if (path.toLowerCase().endsWith(".mzxml"))
            return parseAsMZXML(path, ctx);
        throw new UnsupportedOperationException("Cannot understand extension " + path);
    }

    @Nonnull
    public static JavaPairRDD<String, IMeasuredSpectrum> parseAsMZXML( @Nonnull final String path, @Nonnull final JavaSparkContext ctx) {
        Class inputFormatClass = MZXMLInputFormat.class;
        Class keyClass = String.class;
        Class valueClass = String.class;

        JavaPairRDD<String, String>  spectraAsStrings = ctx.newAPIHadoopFile(
                path,
                inputFormatClass,
                keyClass,
                valueClass,
                SparkUtilities.getHadoopConfiguration()
        );

        // debug code
        //spectraAsStrings = SparkUtilities.realizeAndReturn(spectraAsStrings);

        // filter out MS Level 1 spectra
        spectraAsStrings = spectraAsStrings.filter( new Function<Tuple2<String,String>, Boolean>() {
           public Boolean call(Tuple2<String,String> s) {
               String s1 = s._2();
               return !s1.contains("msLevel=\"1\""); }
         });

         // debug code
        //spectraAsStrings = SparkUtilities.realizeAndReturn(spectraAsStrings);

        // parse scan tags as  IMeasuredSpectrum
        JavaPairRDD<String, IMeasuredSpectrum> parsed = spectraAsStrings.mapToPair(new MapSpectraStringToRawScan());
        return parsed;
    }

    @Nonnull
    public static JavaPairRDD<String, IMeasuredSpectrum> parseAsOldMGF( @Nonnull final String path, @Nonnull final JavaSparkContext ctx) {
        Class inputFormatClass = MGFOldInputFormat.class;
        Class keyClass = String.class;
        Class valueClass = String.class;

        JavaPairRDD<String, String> spectraAsStrings = ctx.hadoopFile(
                path,
                inputFormatClass,
                keyClass,
                valueClass
        );
           JavaPairRDD<String, IMeasuredSpectrum> spectra = spectraAsStrings.mapToPair(new MGFStringTupleToSpectrumTuple());
        return spectra;
    }

    /**
     * NOTE this will not work since Text is not serializable
     *
     * @param path
     * @param ctx
     * @return
     */
    @Nonnull
    public static JavaPairRDD<String, IMeasuredSpectrum> parseAsTextMGF( @Nonnull final String path, @Nonnull final JavaSparkContext ctx) {
        Class inputFormatClass = MGFTextInputFormat.class;
        Class keyClass = String.class;
        Class valueClass = String.class;

        JavaPairRDD<String, String> spectraAsStrings = ctx.newAPIHadoopFile(
                path,
                inputFormatClass,
                keyClass,
                valueClass,
                ctx.hadoopConfiguration()
        );
        JavaPairRDD<String, IMeasuredSpectrum> spectra = spectraAsStrings.mapToPair(new MGFStringTupleToSpectrumTuple());
        return spectra;
    }





    @Nonnull
    public static JavaPairRDD<String, IMeasuredSpectrum> parseAsMGF( @Nonnull final String path, @Nonnull final JavaSparkContext ctx) {
        Class inputFormatClass = MGFInputFormat.class;
        Class keyClass = String.class;
        Class valueClass = String.class;

        JavaPairRDD<String, String> spectraAsStrings = ctx.newAPIHadoopFile(
                path,
                inputFormatClass,
                keyClass,
                valueClass,
                ctx.hadoopConfiguration()
        );
        JavaPairRDD<String, IMeasuredSpectrum> spectra = spectraAsStrings.mapToPair(new MGFStringTupleToSpectrumTuple());
        return spectra;
    }

    /**
     * sample using the olf Hadoop api
     * @param path  path to the file
     * @param ctx  context
     * @return  contents as scans
     */
    @Nonnull
    public static JavaPairRDD<String, IMeasuredSpectrum> parseSpectrumFileOld( @Nonnull String path, @Nonnull JavaSparkContext ctx) {

        Class inputFormatClass = MGFOldInputFormat.class;
        Class keyClass = String.class;
        Class valueClass = String.class;

        JavaPairRDD<String, String> spectraAsStrings = ctx.hadoopFile(
                path,
                inputFormatClass,
                keyClass,
                valueClass
        );
        JavaPairRDD<String, IMeasuredSpectrum> spectra = spectraAsStrings.mapToPair(new MGFStringTupleToSpectrumTuple());
        return spectra;
    }

    private static class MapSpectraStringToRawScan extends AbstractLoggingPairFunction<Tuple2<String, String>, String, IMeasuredSpectrum> {
        @Override
        public Tuple2<String, IMeasuredSpectrum> doCall(final Tuple2<String, String> in) throws Exception {
            String key = in._1();
            RawPeptideScan scan = XTandemHadoopUtilities.readScan(in._2(), null);
            return new Tuple2(key,scan);
        }
    }
}
