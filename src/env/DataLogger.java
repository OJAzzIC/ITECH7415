package vocab;

import java.io.FileWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;

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

    public static void initialise() {
        LocalDateTime startTime = LocalDateTime.now();
        System.out.println("Starting time is: " + startTime.toString());
        timeStampString = String.format("%1$ty-%1$tm-%1$td-%1$tH-%1$tM-%1$tS", startTime);
    }

    @OPERATION
    public void addAnnualStats(String agentName, String profile, String parentProfile,
            String homeProfile, String ses, int age,
            int uniqueSeen, int uniqueHeard, int uniqueEncountered, int learned) {
        System.out.println("[DataLogger] addAnnualStats called for " + agentName
            + " profile=" + profile + " SES=" + ses + " encountered=" + uniqueEncountered
            + " learned=" + learned);
        wordCountRows.add(new WordCountRow(agentName, profile, parentProfile, homeProfile, ses, age,
                uniqueSeen, uniqueHeard, uniqueEncountered, learned));
    }

    public static void writeFiles() {
        System.out.println("[DataLogger] writeFiles called. Rows to write: " + wordCountRows.size());
        if (wordCountRows.size() == 0) {
            System.out.println("[DataLogger] WARNING: No data to write! addAnnualStats was never called successfully.");
            return;
        }
        try (CSVWriter writer = new CSVWriter(new FileWriter(outputSummaryLocation()))) {
            String[] headers = { "Name", "Profile", "ParentProfile", "HomeProfile",
                                  "SES", "Age", "Unique_Seen",
                                  "Unique_Heard", "Unique_Encountered", "Words_Learned" };
            writer.writeNext(headers, false);
            for (WordCountRow row : wordCountRows) {
                writer.writeNext(row.toArray(), false);
            }
            System.out.println("Summary data file successfully written.");
        } catch (Exception ex) {
            System.out.println("Something broke while saving summary data: " + ex.getMessage());
        }
        try (CSVWriter writer = new CSVWriter(new FileWriter(outputWordsLocation()))) {
            String[] header = { "Word", "Name", "SES", "Age" };
            writer.writeNext(header);
            for (WordLearntRow detail : WordsLearnt.getAll()) {
                writer.writeNext(
                    new String[] { detail.word(), detail.name(),
                                   detail.ses(), Integer.toString(detail.age()) });
            }
            System.out.println("Word count data file successfully written.");
        } catch (Exception ex) {
            System.out.println("Something broke while saving word data: " + ex.getMessage());
        }
    }

    public static String outputSummaryLocation() {
        return timeStampString + " summary.csv";
    }

    public static String outputWordsLocation() {
        return timeStampString + " word_list.csv";
    }
}
