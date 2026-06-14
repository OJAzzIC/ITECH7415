package vocab;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.exceptions.CsvException;

import cartago.*;

public class Utterances extends Artifact {
    private static final Random rnd = new Random();
    private static String filepath = "";
    // Named pools of utterances: pool name -> (speaker_code -> utterances).
    // The "default" pool is the main corpus; additional pools (e.g. an ESL
    // corpus) are declared with [[utterance_pool]] sections in
    // simulation.conf and assigned to parents via [[parent_profile]].
    private static final String DEFAULT_POOL = "default";
    private static HashMap<String, HashMap<String, ArrayList<String[]>>> pools = new HashMap<>();
    private static HashMap<String, ArrayList<String>> participantRoleToCodeMapping = new HashMap<>();
    private static int utterances_available_count;
    private static int speaker_code_count;

    // By the time init() is called, the pools have been populated, so
    // this 'just works'.
    void init() {
        defineObsProperty("num_speaker_codes_available",
                pools.getOrDefault(DEFAULT_POOL, new HashMap<>()).size());
        defineObsProperty("utterances_available", utterances_available_count);
    }

    // This is the method which initiates the loading of the default pool's
    // utterances and the participant data.
    public static void loadUtterances(String filePath) {
        Utterances.filepath = filePath;
        loadPool(DEFAULT_POOL, filePath);
        loadParticipants();
    }

    // Load a named pool of utterances from a directory of CSV files.
    // Assume that the utterances are found in files containing 'utterances' in
    // their name
    // Assume that they are all CSV files.
    // Assume that fields we're after are 'speaker_code' and 'gloss', and that they
    // are present.
    public static void loadPool(String poolName, String path) {
        if (pools.containsKey(poolName)) {
            return;
        }
        System.out.print("Loading utterance pool '" + poolName + "': ");
        // Get the raw data from the utterance CSV files.
        // HashMap returned is keyed by speaker_code, which each value being an
        // ArrayList<String> where each String is an entire utterance.
        HashMap<String, ArrayList<String>> raw = HashMapFromCSVFiles(path, "utterances",
                new String[] { "speaker_code", "gloss" });
        HashMap<String, ArrayList<String[]>> utterancesMap = new HashMap<>();
        // Need to pre-split the returned strings - saves many op's later in
        // getBulkUtterances().
        for (var entry : raw.entrySet()) {
            ArrayList<String[]> splitList = new ArrayList<>(entry.getValue().size());
            for (String utterance : entry.getValue()) {
                ArrayList<String> temp = new ArrayList<>();
                for (String subString : utterance.split(" ")) {
                    if (!subString.isBlank())
                        temp.add(subString.toLowerCase());
                }
                splitList.add(temp.toArray(new String[temp.size()]));
            }
            utterancesMap.put(entry.getKey(), splitList);
        }
        pools.put(poolName, utterancesMap);
        // This sets up the backing-field for the observable property, which agents
        // could use
        if (DEFAULT_POOL.equals(poolName))
            for (ArrayList<String[]> entry : utterancesMap.values())
                utterances_available_count += entry.size();
    }

    // Assume that the participant meta-data is found in files containing
    // 'participants' in their name
    // Assume that they are all CSV files.
    // Assume that the fields we're after are 'role' and 'id', and that they are
    // present.
    private static void loadParticipants() {
        System.out.print("Loading participant metadata: ");
        participantRoleToCodeMapping = HashMapFromCSVFiles(filepath, "participants", new String[] { "role", "id" });
    }

    // Produce a HashMap<String, ArrayList<String>> from all CSV files found.
    private static HashMap<String, ArrayList<String>> HashMapFromCSVFiles(final String basePath,
            final String fileNamePart,
            final String[] headers) {
        // Some sanity checks - if these fail, the rest is pointless to attempt, so we
        // throw Exceptions.
        if (basePath == null || basePath.isBlank())
            throw new IllegalArgumentException("'basePath' must contain something");
        final File folder = new File(basePath);
        if (!folder.exists())
            throw new IllegalArgumentException("'basePath' does not exist");
        if (!folder.isDirectory())
            throw new IllegalArgumentException("'basePath' must be a directory");

        // The sanity checks passed, so we should be 'good to go' from here
        final String fileNameFilter = (fileNamePart == null) ? "" : fileNamePart;
        HashMap<String, ArrayList<String>> result = new HashMap<>();
        int fileCounter = 0;
        int entryCounter = 0;
        // This implements the requirements that we have a CSV file and that the file's
        // name contains the requested 'fileNamePart'
        final FileFilter filter = new FileFilter() {
            public boolean accept(File file) {
                return (file.getName().endsWith(".csv") && file.getName().contains(fileNameFilter));
            }
        };

        // Time to process the files we found...
        // The OpenCSV library handles the intricacies of parsing the CSV file,
        // allowing us to just use the output.
        long startTime = System.currentTimeMillis();
        for (File file : folder.listFiles(filter)) {
            try (CSVReaderHeaderAware reader = new CSVReaderHeaderAware(new FileReader(file))) {
                // Unfortunately, it isn't a typical iterator, so doesn't
                // provide us with as hasNext() method.
                boolean finished = false;
                String[] lineRead;
                ArrayList<String> temp;
                while (!finished) {
                    if (headers == null || headers.length == 0)
                        // No headers given, so just read the next line in full.
                        lineRead = reader.readNext();
                    else
                        // Some headers were given, so read just those fields.
                        lineRead = reader.readNext(headers);
                    // lineRead contains the line in full, the requested fields, or is null.
                    if (lineRead == null)
                        // Reached the end of the file, so break out of the 'while' loop
                        finished = true;
                    else {
                        lineRead[0] = lineRead[0].toLowerCase();
                        lineRead[1] = lineRead[1].toLowerCase();
                        // Assume that the 1st value in the 'lineRead' array is the 'key' field for
                        // HashMap.
                        // Assume that the 2nd value in the 'lineRead' array is the ONLY 'value' field
                        // required for the HashMap.
                        if (result.containsKey(lineRead[0])) {
                            // If we've seen this key before, add the value to the ArrayList<String>
                            // associated with it.
                            temp = result.get(lineRead[0]);
                            temp.add(lineRead[1]);
                        } else {
                            // If we haven't seen this key before, create a new ArrayList<String>, add the
                            // found value to it, and then associate this ArrayList<> with the key.
                            temp = new ArrayList<String>();
                            temp.add(lineRead[1]);
                            result.put(lineRead[0], temp);
                        }
                        ++entryCounter;
                    }
                }
                ++fileCounter;
            } catch (IOException | CsvException e) {
                System.out.println("Something broke, here's the stack trace for you:");
                e.printStackTrace();
            }
        }
        // Give the user some diagnostic information
        System.out.printf("%d entries from %d files in %,dms.\n", entryCounter, fileCounter,
                System.currentTimeMillis() - startTime);
        // Lastly, handoff the HashMap that was built here.
        return result;
    }

    @OPERATION
    void getSpeakerCodes(OpFeedbackParam<String[]> result) {
        result.set((String[]) pools.get(DEFAULT_POOL).keySet().toArray());
    }

    // Provide a random utterance from the database.
    // Returns: speakerCode - a string representing the identifier from the data
    // files of the person who made the selected utterance
    // utterance - an array of words representing the utterance that was
    // selected
    // utteranceLength - an Integer indicating the total number of words in
    // the utterance that was selected
    @OPERATION
    void getRandomUtterance(OpFeedbackParam<String> speakerCode, OpFeedbackParam<String[]> utterance,
            OpFeedbackParam<Integer> utteranceLength) {
        HashMap<String, ArrayList<String[]>> utterancesMap = pools.get(DEFAULT_POOL);
        String[] keys = utterancesMap.keySet().toArray(new String[0]);
        String rndSpeaker = keys[rnd.nextInt(keys.length)];
        ArrayList<String[]> utterances = utterancesMap.get(rndSpeaker);
        String[] rndUtterance = utterances.get(rnd.nextInt(utterances.size()));
        speakerCode.set(rndSpeaker);
        utterance.set(rndUtterance);
        utteranceLength.set(rndUtterance.length);
    }

    // Backwards-compatible form: sample from the default pool.
    @OPERATION
    void getBulkUtterances(double numWordsRequired, OpFeedbackParam<Object[]> utternaces,
            OpFeedbackParam<Integer> numWordsProvided) {
        getBulkUtterances(DEFAULT_POOL, numWordsRequired, utternaces, numWordsProvided);
    }

    // Sample utterances totalling at least numWordsRequired words from the
    // named pool.  Unknown pool names fall back to the default pool (with a
    // console warning) rather than failing the calling agent's plan.
    @OPERATION
    void getBulkUtterances(String poolName, double numWordsRequired, OpFeedbackParam<Object[]> utternaces,
            OpFeedbackParam<Integer> numWordsProvided) {
        HashMap<String, ArrayList<String[]>> utterancesMap = pools.get(poolName);
        if (utterancesMap == null || utterancesMap.isEmpty()) {
            System.out.println("WARNING: utterance pool '" + poolName
                    + "' is unknown or empty; using the default pool instead.");
            utterancesMap = pools.get(DEFAULT_POOL);
        }
        if (numWordsRequired < 1) {
            // The feedback parameters must always be set; leaving them unset
            // makes the calling agent's plan fail, which stalls the daily
            // synchronisation cycle (the day never finishes).
            utternaces.set(new Object[] {});
            numWordsProvided.set(0);
            return;
        }
        ArrayList<String[]> results = new ArrayList<>();
        int runningTally = 0;
        Object[] values = utterancesMap.values().toArray();
        while (numWordsRequired > 0) {
            @SuppressWarnings("unchecked")
            ArrayList<String[]> l2 = (ArrayList<String[]>) values[rnd.nextInt(values.length)];
            String[] utterance = l2.get(rnd.nextInt(l2.size()));
            runningTally += utterance.length;
            numWordsRequired -= utterance.length;
            results.add(utterance);
        }
        utternaces.set(results.toArray());
        numWordsProvided.set(runningTally);
    }
}
