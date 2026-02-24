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
    private static String filepath = "";
    private static HashMap<String, ArrayList<String>> utterancesMap = new HashMap<>();
    private static HashMap<String, ArrayList<String>> participantRoleToCodeMapping = new HashMap<>();
    private static int utterances_available_count;
    private static int speaker_code_count;

    // By the time init() is called, both HashMap's have been populated, so
    // this 'just works'.
    void init() {
        defineObsProperty("num_speaker_codes_available", utterancesMap.size());
        defineObsProperty("utterances_available", utterances_available_count);
    }

    // This is the method which initiates the loading of the utterances and the
    // participant data.
    public static void loadUtterances(String filePath) {
        if (utterancesMap.size() != 0) {
            return;
        }
        Utterances.filepath = filePath;
        loadUtterances();
        loadParticipants();
    }

    // Assume that the utterances are found in files containing 'utterances' in
    // their name
    // Assume that they are all CSV files.
    // Assume that fields we're after are 'speaker_code' and 'gloss', and that they
    // are present.
    private static void loadUtterances() {
        System.out.print("Loading utterances: ");
        utterancesMap = HashMapFromCSVFiles(filepath, "utterances", new String[] { "speaker_code", "gloss" });
        // This sets up the backing-field for the observable property, which agents
        // could use
        for (ArrayList<String> entry : utterancesMap.values())
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
    private static HashMap<String, ArrayList<String>> HashMapFromCSVFiles(final String basePath, final String fileNamePart,
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
        result.set((String[]) utterancesMap.keySet().toArray());
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
        Random rnd = new Random();
        String[] keys = utterancesMap.keySet().toArray(new String[0]);
        String rndSpeaker = keys[rnd.nextInt(keys.length)];
        ArrayList<String> utterances = utterancesMap.get(rndSpeaker);
        String[] rndUtterance = utterances.get(rnd.nextInt(utterances.size())).split(" ");
        speakerCode.set(rndSpeaker);
        utterance.set(rndUtterance);
        utteranceLength.set(rndUtterance.length);
    }

    @OPERATION
    void getBulkUtterances(double numWordsRequired, OpFeedbackParam<Object[]> utternaces,
            OpFeedbackParam<Integer> numWordsProvided) {
        if (numWordsRequired < 1)
            return;
        Random rnd = new Random();
        ArrayList<String[]> results = new ArrayList<>();
        int runningTally=0;
        while (numWordsRequired > 0) {
            Collection<ArrayList<String>> l1 = utterancesMap.values();
            ArrayList<String>l2=(ArrayList<String>)(l1.toArray()[rnd.nextInt(l1.size())]);
            String[]utterance=l2.get(rnd.nextInt(l2.size())).split(" ");
            runningTally+=utterance.length;
            numWordsRequired-=utterance.length;
            results.add(utterance);
        }
        utternaces.set(results.toArray());
        numWordsProvided.set(runningTally);
    }
}
