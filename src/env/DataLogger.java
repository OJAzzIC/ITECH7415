package vocab;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;

import com.opencsv.CSVWriter;

import cartago.Artifact;
import cartago.OPERATION;
import vocab.WordLearntRow;
import vocab.WordsLearnt;

public class DataLogger extends Artifact {

    private static String timeStampString;
    private static ArrayList<WordCountRow> wordCountRows = new ArrayList<>();

    void init() {
    }

    // The VocabLauncher class calls this fairly early on.
    // All that's done here is to get the start time, to build the filename that
    // data will be stored in.
    public static void initialise() {
        LocalDateTime startTime = LocalDateTime.now();
        System.out.println("Starting time is: " + startTime.toString());
        timeStampString = String.format("%1$ty-%1$tm-%1$td-%1$tH-%1$tM-%1$tS", startTime);
    }

    // Agents will call this method to save-out the details about the number of
    // words they have encountered.
    @OPERATION
    public void addAnnualStats(String agentName, String ses, int age, int uniqueSeen, int uniqueHeard, int learned) {
        wordCountRows.add(new WordCountRow(agentName, ses, age, uniqueSeen, uniqueHeard, learned));
    }

    // This is called by the synchroniser artefact, to save the results to a CSV
    // file.
    public static void writeFile() {
        // Don't try to write to the file if there's nothing for us to write...
        if (wordCountRows.size() == 0)
            return;
        // File IO operations are prone to issues, so wrap all of it within a try{}
        // block.
        // If any form of Exception is thrown by the OpenCSV library, we catch it, tell
        // the user something broke & give them the error message. Potentially this
        // COULD be altered to provide a less technical error message and/or more
        // graceful handling of errors...
        try (CSVWriter writer = new CSVWriter(new FileWriter(outputSummaryLocation()))) {

            // Build the headers, and write them to the file.
            String[] headers = { "Name", "SES", "Age", "Unique_Seen", "Unique_Heard", "Words_Learned" };
            writer.writeNext(headers, false);
            // Push each row of data out to the file.
            for (WordCountRow row : wordCountRows) {
                writer.writeNext(row.toArray(), false);
            }
            // Declare that we're done.
            System.out.println("Summary data file successfully written.");
        } catch (Exception ex) {
            // Things didn't go to plan, tell the user such.
            System.out.println("Something broke while trying to save the summary data to disk.");
            System.out.println(ex.getMessage());
        }
        try (CSVWriter writer = new CSVWriter(new FileWriter(outputWordsLocation()))) {
            String[] header = { "Word", "Name", "SES", "Age" };
            writer.writeNext(header);
            for (WordLearntRow detail : WordsLearnt.getAll()) {
                writer.writeNext(
                        new String[] { detail.word(), detail.name(), detail.ses(), Integer.toString(detail.age()) });
            }
            System.out.println("Word count data file successfully written.");
        } catch (Exception ex) {
            // Things didn't go to plan, tell the user such.
            System.out.println("Something broke while trying to save the word data to disk.");
            System.out.println(ex.getMessage());
        }
    }

    // This gets called by the synchroniser artefact after shutting down the MAS.
    public static String outputSummaryLocation() {
        return timeStampString + " summary.csv";
    }

    public static String outputWordsLocation() {
        return timeStampString + " word_list.csv";
    }
}
