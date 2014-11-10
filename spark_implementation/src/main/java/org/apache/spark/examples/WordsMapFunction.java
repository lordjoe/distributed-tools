package org.apache.spark.examples;

import org.apache.spark.api.java.*;
import org.apache.spark.api.java.function.*;

import java.util.*;
import java.util.regex.*;

/**
 * org.apache.spark.examples.WordsMapFunction
 * User: Steve
 * Date: 8/25/2014
 */
public class WordsMapFunction implements FlatMapFunction<String, String> {

    private static final Pattern SPACE = Pattern.compile(" ");

    private transient JavaSparkContext currentContext;

    public Iterable<String> call(String s) {
        String[] split = SPACE.split(s);
        List<String> ret = new ArrayList<String>();
        for (int i = 0; i < split.length; i++) {
            String sx = regularizeString(split[i]);
            if (sx.length() > 0)
                ret.add(sx);
        }
        return ret;
    }

    public static String dropNonLetters(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c))
                sb.append(c);
        }

        return sb.toString();
    }


    public static String regularizeString(String inp) {
        inp = inp.trim();
        inp = inp.toUpperCase();
        return dropNonLetters(inp);
    }

}
