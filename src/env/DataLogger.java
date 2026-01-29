package vocab;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;

import com.opencsv.CSVWriter;

import cartago.Artifact;
import cartago.OPERATION;

public class DataLogger extends Artifact {
    // Define a read-only 'record' object
    // 'record' is syntactic 'sugar' which creates a fully-fledged class type during
    // compilation with appropriate getters and a single constructor using the
    // parameters provided here.
    // Nothing NEEDS to be given inside the '{}'s, however whatever is there will be
    // included in the compiled class
    record OutputRow(String name, String ses, int age, int uniqueSeen, int uniqueHeard, int learned) {
        String[] toArray() {
            return new String[] {
                    name,
                    ses,
                    Integer.toString(age),
                    Integer.toString(uniqueSeen),
                    Integer.toString(uniqueHeard),
                    Integer.toString(learned)
            };
        }
    }

    private static String fileName;
    private static ArrayList<OutputRow> rows = new ArrayList<>();

    void init() {
    }

    // The VocabLauncher class calls this fairly early on.
    // All that's done here is to get the start time, to build the filename that
    // data will be stored in.
    public static void initialise() {
        LocalDateTime startTime = LocalDateTime.now();
        System.out.println("Starting time is: " + startTime.toString());
        fileName = String.format("%1$ty-%1$tm-%1$td-%1$tH-%1$tM-%1$tS.csv", startTime);
    }

    // Agents will call this method to save-out the details about the number of
    // words they have encountered.
    @OPERATION
    public void addAnnualStats(String agentName, String ses, int age, int uniqueSeen, int uniqueHeard, int learned) {
        rows.add(new OutputRow(agentName, ses, age, uniqueSeen, uniqueHeard, learned));
    }

    // This is called by the synchroniser artefact, to save the results to a CSV
    // file.
    public static void writeFile() {
        // Don't try to write to the file if there's nothing for us to write...
        if (rows.size() == 0)
            return;
        // File IO operations are prone to issues, so wrap all of it within a try{}
        // block.
        // If any form of Exception is thrown by the OpenCSV library, we catch it, tell
        // the user something broke & give them the error message. Potentially this
        // COULD be altered to provide a less technical error message and/or more
        // graceful handling of errors...
        try {
            // Create the object which will write to the file.
            CSVWriter writer = new CSVWriter(new FileWriter(fileName));

            // Build the headers, and write them to the file.
            String[] headers = { "Name", "SES", "Age", "Unique_Seen", "Unique_Heard", "Words_Learned" };
            writer.writeNext(headers, false);
            // Push each row of data out to the file.
            for (OutputRow row : rows) {
                writer.writeNext(row.toArray(), false);
            }
            // Close the file, freeing up resources and flushing the contents to disk.
            writer.close();
            // Declare that we're done.
            System.out.println("Data file successfully written.");
        } catch (Exception ex) {
            // Things didn't go to plan, tell the user such.
            System.out.println("Something broke while trying to save the data to disk.");
            System.out.println(ex.getMessage());
        }
    }

    // This gets called by the synchroniser artefact after shutting down the MAS.
    public static String outputLocation() {
        return fileName;
    }
}
