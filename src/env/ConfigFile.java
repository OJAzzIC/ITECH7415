package vocab;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

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
    private static final int AGE_FINISH_MIN = 0;
    private static final int AGE_FINISH_MAX = 10;

    private static final double PER_SINGLE_PARENTS_DEFAULT = 100;
    private static final double PER_SINGLE_PARENTS_MIN = 0;
    private static final double PER_SINGLE_PARENTS_MAX = 100;

    Agents agents;
    HashSet<SES> ses = new HashSet<>();
    Environment environment;
    ArrayList<ChildProfile> childProfiles = new ArrayList<>();
    ArrayList<ParentProfile> parentProfiles = new ArrayList<>();
    ArrayList<HomeProfile> homeProfiles = new ArrayList<>();
    // pool name -> directory of utterance CSV files
    HashMap<String, String> utterancePools = new HashMap<>();
    int daysPerYear = DAYS_PER_YEAR_DEFAULT;

    private static final int DAYS_PER_YEAR_DEFAULT = 365;

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
        output.environment = new Environment("./resources/utterances", "./resources/books");
        output.applyProfileDefaults();
        return output;
    }

    /*
     * Fill in default profiles/pools for anything the config file did not
     * define, so the rest of the system can always assume at least one of
     * each exists.  With no profile sections in the file, the simulation
     * behaves exactly as the original (pre-profile) model.
     */
    private void applyProfileDefaults() {
        if (childProfiles.isEmpty())
            childProfiles.add(ChildProfile.typical(agents.getChild()));
        if (parentProfiles.isEmpty())
            parentProfiles.add(ParentProfile.standard());
        if (homeProfiles.isEmpty())
            homeProfiles.add(HomeProfile.noBooks());
        // The 'default' pool always points at the main utterance directory.
        utterancePools.put("default", environment.utteranceLocation());
        // If child profiles are defined, the total child count is the sum of
        // their counts (overriding agents.child, which then only acts as the
        // fallback when no profiles are given).
        int total = childProfiles.stream().mapToInt(ChildProfile::count).sum();
        if (total != agents.getChild()) {
            System.out.println("Child profiles define " + total
                    + " children in total; overriding agents.child=" + agents.getChild() + ".");
            agents = new Agents(total, agents.getMaxSiblings(), agents.getPercentSingleParents(),
                    agents.getAgeStart(), agents.getAgeFinish());
        }
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
        // 'age_finish' must not be less than 'age_start'.
        // age_finish == age_start is valid and simulates a single year.
        if (age_finish < age_start)
            age_finish = age_start;
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

        // Optional: days per simulated year (default 365).  Mainly useful
        // for quick smoke-test runs while developing scenarios.
        output.daysPerYear = clamp_int_to_range(1,
                configurationFile.getIntOrElse("agents.days_per_year", DAYS_PER_YEAR_DEFAULT),
                366);

        // Try to parse the '[[child_profile]]' array, if present.
        List<StampedConfig> childProfilesInFile = configurationFile.get("child_profile");
        if (childProfilesInFile != null) {
            for (StampedConfig p : childProfilesInFile) {
                if (!p.contains("name")) {
                    System.out.println("Skipped a [[child_profile]] entry with no 'name'.");
                    continue;
                }
                String name = p.get("name");
                int count = p.getIntOrElse("count", 1);
                double attHeard = clamp_double_to_range(0.0,
                        p.getOrElse("attentiveness_heard",
                                p.getOrElse("attentiveness", ChildProfile.ATTENTIVENESS_DEFAULT)),
                        1.0);
                double attSeen = clamp_double_to_range(0.0,
                        p.getOrElse("attentiveness_seen",
                                p.getOrElse("attentiveness", ChildProfile.ATTENTIVENESS_DEFAULT)),
                        1.0);
                int tSeen = p.getIntOrElse("threshold_seen", ChildProfile.THRESHOLD_SEEN_DEFAULT);
                int tHeard = p.getIntOrElse("threshold_heard", ChildProfile.THRESHOLD_HEARD_DEFAULT);
                int tBoth = p.getIntOrElse("threshold_both", ChildProfile.THRESHOLD_BOTH_DEFAULT);
                output.childProfiles.add(
                        new ChildProfile(name, count, attHeard, attSeen, tSeen, tHeard, tBoth));
            }
        }

        // Try to parse the '[[parent_profile]]' array, if present.
        List<StampedConfig> parentProfilesInFile = configurationFile.get("parent_profile");
        if (parentProfilesInFile != null) {
            for (StampedConfig p : parentProfilesInFile) {
                if (!p.contains("name")) {
                    System.out.println("Skipped a [[parent_profile]] entry with no 'name'.");
                    continue;
                }
                String name = p.get("name");
                String pool = p.getOrElse("pool", "default");
                double factor = clamp_double_to_range(0.0,
                        p.getOrElse("daily_words_factor", 1.0), 2.0);
                output.parentProfiles.add(new ParentProfile(name, pool, factor));
            }
        }

        // Try to parse the '[[home_profile]]' array, if present.
        List<StampedConfig> homeProfilesInFile = configurationFile.get("home_profile");
        if (homeProfilesInFile != null) {
            for (StampedConfig p : homeProfilesInFile) {
                if (!p.contains("name")) {
                    System.out.println("Skipped a [[home_profile]] entry with no 'name'.");
                    continue;
                }
                String name = p.get("name");
                int books = clamp_int_to_range(0, p.getIntOrElse("books_per_day", 0), 10);
                output.homeProfiles.add(new HomeProfile(name, books));
            }
        }

        // Try to parse the '[[utterance_pool]]' array, if present.
        List<StampedConfig> poolsInFile = configurationFile.get("utterance_pool");
        if (poolsInFile != null) {
            for (StampedConfig p : poolsInFile) {
                if (p.contains("name") && p.contains("path")) {
                    output.utterancePools.put(p.get("name"), p.get("path"));
                } else {
                    System.out.println("Skipped a malformed [[utterance_pool]] entry (needs 'name' and 'path').");
                }
            }
        }

        output.applyProfileDefaults();
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
        sb.append("   days_per_year = " + daysPerYear + "\n");
        for (SES s : ses) {
            sb.append("[[ses]]\n");
            sb.append("   name = " + s.getName() + "\n");
            sb.append("   qty = " + s.getQty() + "\n");
        }
        for (ChildProfile p : childProfiles) {
            sb.append("[[child_profile]]\n");
            sb.append("   name = " + p.name() + ", count = " + p.count()
                    + ", attentiveness(heard/seen) = " + p.attentivenessHeard() + "/" + p.attentivenessSeen()
                    + ", thresholds(seen/heard/both) = " + p.thresholdSeen() + "/" + p.thresholdHeard()
                    + "/" + p.thresholdBoth() + "\n");
        }
        for (ParentProfile p : parentProfiles) {
            sb.append("[[parent_profile]]\n");
            sb.append("   name = " + p.name() + ", pool = " + p.pool()
                    + ", daily_words_factor = " + p.dailyWordsFactor() + "\n");
        }
        for (HomeProfile p : homeProfiles) {
            sb.append("[[home_profile]]\n");
            sb.append("   name = " + p.name() + ", books_per_day = " + p.booksPerDay() + "\n");
        }
        for (var pool : utterancePools.entrySet()) {
            sb.append("[[utterance_pool]]\n");
            sb.append("   name = " + pool.getKey() + ", path = " + pool.getValue() + "\n");
        }
        return sb.toString();
    }
}