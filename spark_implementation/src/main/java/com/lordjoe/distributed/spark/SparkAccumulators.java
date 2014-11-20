package com.lordjoe.distributed.spark;

import com.lordjoe.distributed.*;
import com.lordjoe.distributed.wordcount.*;
import org.apache.spark.*;
import org.apache.spark.api.java.*;

import java.io.*;
import java.util.*;

/**
 * com.lordjoe.distributed.spark.SparkAccumulators
 * this class implements a similar ides to Hadoop Accumulators
 * User: Steve
 * Date: 11/12/2014
 */
public class SparkAccumulators implements Serializable {


    public static final int MAX_TRACKED_THREADS = 10;
    private static SparkAccumulators instance;
    private static boolean functionsLoggedByDefault = true;

    public static boolean isFunctionsLoggedByDefault() {
        return functionsLoggedByDefault;
    }

    public static void setFunctionsLoggedByDefault(final boolean pFunctionsLoggedByDefault) {
        functionsLoggedByDefault = pFunctionsLoggedByDefault;
    }

    public static SparkAccumulators getInstance() {
        return instance;
    }

    public static void createInstance() {
        instance = new SparkAccumulators();
        for (int i = 0; i < MAX_TRACKED_THREADS; i++) {
            //noinspection AccessStaticViaInstance
            instance.createAccumulator(ThreadUseLogger.getThreadAccumulatorName(i));
        }

        instance.createMachineAccumulator();
    }

    // this is a singleton and should be serialized
    private SparkAccumulators() {
    }

    /**
     * holds accumulators by name
     */
    private final Map<String, Accumulator<Long>> accumulators = new HashMap<String, Accumulator<Long>>();
    private Accumulator<Set<String>> machines;
    private transient Set<String> deliveredMessages = new HashSet<String>();

    /**
     * append lines for all accumulators to an appendable
     * NOTE - call only in the Executor
     *
     * @param out where to append
     */
    public static void showAccumulators(Appendable out) {
        try {
            SparkAccumulators me = getInstance();
            List<String> accumulatorNames = me.getAccumulatorNames();
            for (String accumulatorName : accumulatorNames) {
                Accumulator<Long> accumulator = me.getAccumulator(accumulatorName);
                Long value = accumulator.value();
                //noinspection StringConcatenationInsideStringBufferAppend
                out.append(accumulatorName + " " + value + "\n");
            }
            out.append("machines " + me.getMachineList() + "\n");
        }
        catch (IOException e) {
            throw new RuntimeException(e);

        }
    }




    /**
     * must be called in the Executor before accumulators can be used
     *
     * @param acc
     */
    public static void createAccumulator(String acc) {
        SparkAccumulators me = getInstance();
        if (me.accumulators.get(acc) != null)
            return; // already done - should an exception be thrown
        JavaSparkContext currentContext = SparkUtilities.getCurrentContext();
        Accumulator<Long> accumulator = currentContext.accumulator(0L, acc, LongAccumulableParam.INSTANCE);

        me.accumulators.put(acc, accumulator);
    }


    /**
     * append lines for all accumulators to System.out
     * NOTE - call only in the Executor
     */
    public static void showAccumulators() {
        showAccumulators(System.out);
    }


    protected void createMachineAccumulator() {
        JavaSparkContext currentContext = SparkUtilities.getCurrentContext();

        machines = currentContext.accumulator(new HashSet<String>(),"machines",StringSetAccumulableParam.INSTANCE);
    }

    public String getMachineList() {
        List<String> machinesList = new ArrayList(machines.value());
        Collections.sort(machinesList);
        StringBuilder sb = new StringBuilder();
        for (String s : machinesList) {
          if(sb.length() > 0)
              sb.append("\n");
           sb.append(s);
        }
        return sb.toString();
    }

    /**
     * return all registerd aaccumlators
     *
     * @return
     */
    public List<String> getAccumulatorNames() {
        List<String> keys = new ArrayList<String>(accumulators.keySet());
        Collections.sort(keys);  // alphapetize
        return keys;
    }

    /**
     * how much work are we spreading across threads
     */
    public void incrementThreadAccumulator() {
        int threadNumber = ThreadUseLogger.getThreadNumber();
        if (threadNumber > MAX_TRACKED_THREADS)
            return; // too many threads
        incrementAccumulator(ThreadUseLogger.getThreadAccumulatorName(threadNumber));
        incrementMachineAccumulator();
    }


    /**
     * true is an accumulator exists
     */
    public boolean isAccumulatorRegistered(String acc) {
        return accumulators.containsKey(acc);
    }

    /**
     * @param acc name of am existing accumulator
     * @return !null existing accumulator
     */
    public Accumulator<Long> getAccumulator(String acc) {
        Accumulator<Long> ret = accumulators.get(acc);
        if (ret == null) {
            String message = "Accumulators need to be created in advance in the executor - cannot get " + acc;
            if(!deliveredMessages.contains(message))   {
                System.err.println(message);
                deliveredMessages.add(message);
            }
         }
        return ret;
    }


    /**
     * add one to an existing accumulator
     *
     * @param acc
     */
    public void incrementMachineAccumulator( ) {
        Set<String> thisMachine = new HashSet<String>();
        String macAddress = SparkUtilities.getMacAddress();
        String thread = String.format("%05d", ThreadUseLogger.getThreadNumber());
        thisMachine.add(macAddress + "|" + thread);
        machines.add(thisMachine);
    }


    /**
     * add one to an existing accumulator
     *
     * @param acc
     */
    public void incrementAccumulator(String acc) {
        incrementAccumulator(acc, 1);
    }

    /**
     * add added to an existing accumulator
     *
     * @param acc   name of am existing accumulator
     * @param added amount to add
     */
    public void incrementAccumulator(String acc, long added) {
        Accumulator<Long> accumulator = getAccumulator(acc);
        if(accumulator != null)
             accumulator.add(added);
    }

}
