package vocab;

import java.io.InputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.core.concurrent.StampedConfig;
import com.electronwill.nightconfig.toml.*;

public class ConfigFile {
    private static final int CHILD_AGENT_DEFAULT = 10;

    private static final int MAX_SIBLINGS_DEFAULT = 0;
    private static final int MAX_SIBLINGS_MIN = 0;
    private static final int MAX_SIBLINGS_MAX = 2;

    private static final int AGE_START_DEFAULT = 3;
    private static final int AGE_START_MIN = 0;
    private static final int AGE_START_MAX = 5;

    private static final int AGE_FINISH_DEFAULT = 8;
    private static final int AGE_FINISH_MIN = 5;
    private static final int AGE_FINISH_MAX = 10;

    private static final double PER_SINGLE_PARENTS_DEFAULT = 100;
    private static final double PER_SINGLE_PARENTS_MIN = 0;
    private static final double PER_SINGLE_PARENTS_MAX = 100;

    Agents agents;
    HashSet<SES> ses = new HashSet<>();
    Environment environment;

    private ConfigFile() {
    }

    private static int clamp_int_to_range(int min, int value, int max) {
        return (Math.max(min, Math.min(value, max)));
    }

    private static double clamp_double_to_range(double min, double value, double max) {
        return (Math.max(min, Math.min(value, max)));
    }

    public static ConfigFile createDefault() {
        ConfigFile output = new ConfigFile();
        output.agents = new Agents(CHILD_AGENT_DEFAULT, MAX_SIBLINGS_DEFAULT, PER_SINGLE_PARENTS_DEFAULT,
                AGE_START_DEFAULT, AGE_FINISH_DEFAULT);
        output.ses = SES.defaults();
        return output;
    }

    /*
     * This is the primary means to load a configuration file.
     * If any issues are encountered default values are used instead.
     */
    public static ConfigFile parse(Path configFile) {
        ConfigFile output = null;
        FileConfig configurationFile = FileConfig.of(configFile, TomlFormat.instance());
        configurationFile.load();
        // Try to read the provided file, catching exceptions & telling the user.

        // Process each of the found keys and sections, clamping values to within
        // permitted ranges if necessary
        output = new ConfigFile();
        int child, max_siblings, age_start, age_finish;
        double percent_single_parents;

        // Try to parse the value for the 'child' key
        // - if the key isn't present, or is present but isn't an integer, use the
        // default number of child agents
        child = configurationFile.getIntOrElse("agents.child", CHILD_AGENT_DEFAULT);

        // Try to parse the value for the 'max_siblings' key
        // - if not present, or ispresent but not an integer, use default, then clamp to
        // between 'MAX_SIBLINGS_MIN' and 'MAX_SIBLINGS_MAX'
        max_siblings = clamp_int_to_range(MAX_SIBLINGS_MIN,
                configurationFile.getIntOrElse("agents.max_siblings", MAX_SIBLINGS_DEFAULT),
                MAX_SIBLINGS_MAX);

        // Repeat process for 'age_start' & 'age_finish' keys also.
        age_start = clamp_int_to_range(AGE_START_MIN,
                configurationFile.getIntOrElse("agents.age_start", AGE_START_DEFAULT),
                AGE_START_MAX);
        age_finish = clamp_int_to_range(AGE_FINISH_MIN,
                configurationFile.getIntOrElse("agents.age_finish", AGE_FINISH_DEFAULT),
                AGE_FINISH_MAX);
        // 'age_finish' must be greater than 'age_start'
        if (age_finish == age_start)
            ++age_finish;
        // Repeat process for 'percent_single_parents' key as well.
        percent_single_parents = clamp_double_to_range(PER_SINGLE_PARENTS_MIN,
                configurationFile.getOrElse("agents.percent_single_parents", PER_SINGLE_PARENTS_DEFAULT),
                PER_SINGLE_PARENTS_MAX);

        // Create an 'Agents' object to store these values
        output.agents = new Agents(child, max_siblings, percent_single_parents, age_start, age_finish);

        // Try to parse the '[[ses]]' array, if it's present
        List<StampedConfig> sesInFile = configurationFile.get("ses");
        if (sesInFile == null) {
            output.ses = SES.defaults();
        } else {
            // '[[ses]]' was found, parse each entry present
            // NOTE: SES objects are equal if the 'name' field holds the same value.
            for (StampedConfig ses : sesInFile) {
                if (ses.contains("name") && ses.contains("qty")) {
                    // Parse it.
                    String name = ses.get("name");
                    int qty = ses.get("qty");
                    System.out.println(name + ": " + qty);
                    boolean result = output.ses.add(SES.create(name, qty));
                    if (!result)
                        System.out.println("Found a duplicate SES category '" + name + "', which was ignored.");
                } else {
                    System.out.println("Skipped a malformed [[ses]] entry.");
                }
            }
            if (output.ses.size() == 0) {
                System.out.println("No valid SES categories found, using defaults.");
                output.ses = SES.defaults();
            }
        }

        // Try to parse the environment section, if present
        String utteranceLocation = configurationFile.get("environment.locations.utterances");
        if (utteranceLocation == null || utteranceLocation.isBlank()) {
            System.out.println("Unable to determine location of utterances.  Using default location");
            utteranceLocation = "./resources/utterances";
        }
        String bookLocation = configurationFile.get("environment.locations.books");
        if (bookLocation == null || bookLocation.isBlank()) {
            System.out.println("Unable to determine location of book texts.  Using default location");
            bookLocation = "./resources/books";
        }
        output.environment = new Environment(utteranceLocation, bookLocation);
        return output;
    }

    public Agents getAgents() {
        return agents;
    }

    public HashSet<SES> getSES() {
        return ses;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Configuration loaded:");
        sb.append("\n");
        sb.append("[agents]\n");
        sb.append("   child = " + agents.getChild() + "\n");
        sb.append("   max_siblings = " + agents.getMaxSiblings() + "\n");
        sb.append("   percent_single_parents = " + agents.getPercentSingleParents() + "\n");
        sb.append("   age_start = " + agents.getAgeStart() + "\n");
        sb.append("   age_finish = " + agents.getAgeFinish() + "\n");
        for (SES s : ses) {
            sb.append("[[ses]]\n");
            sb.append("   name = " + s.getName() + "\n");
            sb.append("   qty = " + s.getQty() + "\n");
        }
        return sb.toString();
    }
}