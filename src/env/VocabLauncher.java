package vocab;

import java.nio.file.*;
import java.util.Random;
import java.util.LinkedList;
import java.util.logging.Logger;

import jacamo.infra.JaCaMoLauncher;
import jacamo.infra.JaCaMoRuntimeServices;
import jacamo.platform.Platform;
import jacamo.platform.EnvironmentWebInspector;
import jacamo.project.JaCaMoProject;
import jacamo.project.JaCaMoAgentParameters;
import jacamo.util.Config;
import jason.JasonException;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.parser.ParseException;
import jason.mas2j.AgentParameters;
import jason.mas2j.ClassParameters;
import jason.runtime.RuntimeServicesFactory;

import vocab.DataLogger;

public class VocabLauncher extends JaCaMoLauncher {
    private final String configFileName = "simulation.conf";
    private ConfigFile confFile;

    private JaCaMoProject jaCaMoProject;

    /*
     * A direct copy/paste from JaCaMoLauncher class, with the only change being
     * that a VocabLauncher object is created rather than a JaCaMoLauncher object
     */
    public static void main(String[] args) throws JasonException {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i].trim();
            if ("-h".equals(arg)) {
                System.out.println("Usage jacamo <jcm-file> -v -h --debug --log-conf <log.properties file>");
                System.exit(0);
            }
            if ("-v".equals(arg)) {
                System.out.println(Config.get().getPresentation());
                System.exit(0);
            }
        }

        logger = Logger.getLogger(JaCaMoLauncher.class.getName());
        VocabLauncher r = new VocabLauncher();
        runner = r;
        RuntimeServicesFactory.set(new JaCaMoRuntimeServices(runner));
        r.init(args);
        r.registerMBean();
        r.registerInRMI();
        r.registerWebMindInspector();
        r.create();
        r.start();
        r.waitEnd();
        r.finish(0, true, 0);
    }

    /*
     * Create environment, agents, controller
     * As for main(), a straight copy, this time with absolutely no changes.
     */
    @Override
    public void create() throws JasonException {
        createCustomPlatforms();
        createEnvironment();
        createOrganisation();
        createInstitution();
        createAgs();
        // createController();
    }

    /*
     * Yet another complete, verbatim copy/paste from JaCaMoLauncher class.
     * As this is a private method in JaCaMoLauncher, it had to be copied across.
     */
    void createCustomPlatforms() {
        boolean einsp = false;
        Platform p = null;
        for (String pId : getJaCaMoProject().getCustomPlatforms()) {
            try {
                p = (Platform) Class.forName(pId).getConstructor().newInstance();
                p.setJcmProject(getJaCaMoProject());
                p.init(getJaCaMoProject().getPlatformParameters(pId));
                platforms.add(p);
                einsp = einsp || pId.equals(EnvironmentWebInspector.class.getName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // include our own platforms
        if (!einsp) {
            p = new EnvironmentWebInspector();
            try {
                p.init(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
            platforms.add(0, p);
        }
    }

    /*
     * This is where we inject our customised functionality
     * loadCustomConfig() reads a TOML-formatted configuration file (using
     * 'defaults' for any errors encountered).
     * initialiseArtifacts() initialises the environment artifacts (the
     * datalogger and synchronsing/coordination artifacts, the databases of
     * books and utterances).
     * add___Agents() adds the various types of agents, using the configuration
     * information from loadCustomConfig().
     * Lastly, call the JaCaMoLauncher's version of this method, which will
     * use the .jcm file to configure any additional agents configured there,
     * along with the rest of the environment (workspaces and organisation(s)).
     */
    @Override
    public void createAgs() throws JasonException {
        loadCustomConfig();
        initialiseArtifacts();
        addChildAgents();
        addParentAgents();
        addSiblingAgents();
        super.createAgs();
    }

    /*
     * Process:
     * 1. Check the config file exists
     * 1.1. If it doesn't exist, say so & use defaults, go to Step 2
     * 1.2. If it does exist, say so & attempt to parse it.
     * 2. Parse the config file.
     * 2.1. If contains any invalid syntax, note all errors & replace them with
     * defaults
     * 3. Make sure each key's value is a valid value, using default values and
     * clamping to defined limits.
     */
    private void loadCustomConfig() {
        /*
         * 'pwd' should default to the base directory (i.e. where the Gradle script was
         * called from).
         */
        FileSystem fs = FileSystems.getDefault();
        Path pwd = fs.getPath("").toAbsolutePath();
        System.out.printf("Looking for config file in: %s\n", pwd.toString());
        Path configFile = Paths.get(pwd.toString(), configFileName);
        // Step 1 - Does the config file exist?
        if (!Files.exists(configFile)) {
            // Step 1.1 - Config file does not exist - use the defaults
            System.out.printf(
                    "No config file found; using default values.\nIf this is not desired, ensure a file called '%s' exists in %s.\n",
                    configFileName, pwd.toString());
            confFile = ConfigFile.createDefault();
        } else {
            // Step 1.2 - Config file exists - try to parse it
            System.out.printf("Found config file: %s\n", configFile.toString());
            System.out.println("Attempting to parse it.");
            confFile = ConfigFile.parse(configFile);
        }
        // 'confFile' now contains the settings we're running with.
        System.out.println(confFile.toString());
    }

    /*
     * Initialise the artifacts which will control the environment and provide the
     * datasets for the agents to interact with.
     *
     */
    private void initialiseArtifacts() {
        DataLogger.initialise();
        Synchroniser.setAgentConfig(confFile.agents);
        Utterances.loadUtterances(confFile.environment.utteranceLocation());
        Library.loadBookTitles(confFile.environment.bookLocation());
    }

    private void addChildAgents() {
        VocabLauncher runner = (VocabLauncher) VocabLauncher.getJaCaMoRunner();
        jaCaMoProject = runner.getJaCaMoProject();
        Agents agents = confFile.agents;
        int numFamily = agents.getChild();
        LinkedList<Integer> familyOrder = new LinkedList<>();
        Random rng = new Random();

        // Randomise allocation of child agents to their 'home' group
        while (familyOrder.size() < numFamily) {
            int r = rng.nextInt(1, numFamily + 1);
            if (!familyOrder.contains(r))
                familyOrder.add(r);
        }

        // Create a template child agent
        JaCaMoAgentParameters agChild = new JaCaMoAgentParameters(jaCaMoProject);
        agChild.name = "child";
        agChild.bbClass=new ClassParameters("vocab.HashMapBB");
        agChild.bbClass.addParameter("\"word(key,_)\"");
        try {
            agChild.setSource("child.asl");
        } catch (Exception e) {
            e.printStackTrace();
        }
        agChild.addFocus("classroom.library", null);
        agChild.addFocus("sync.synchroniser", "sync");
        agChild.addFocus("sync.datalogger", "sync");
        agChild.addFocus("sync.wordsLearnt", "learnt");
        agChild.addRole("learning_environ.village", "child");
        try {
            agChild.addInitBel(ASSyntax.parseLiteral("age(" + agents.getAgeStart() + ")"));
            agChild.addInitBel(ASSyntax.parseLiteral("activityState(\"Idle\")"));
            agChild.addInitGoal(ASSyntax.parseLiteral("join_school"));
        } catch (ParseException ex) {
        }
        // Duplicate that template agent, tell it which group to join
        for (int i = 1; i <= numFamily; ++i) {
            AgentParameters ag = agChild.copy();
            ag.name += i;
            try {
                JaCaMoAgentParameters jAg = (JaCaMoAgentParameters) ag;
                jAg.addInitGoal(ASSyntax.parseLiteral("join_home_group(home_" + familyOrder.pop() + ")"));
            } catch (ParseException ex) {
            }
            // Add the agent to the system
            jaCaMoProject.addAgent(ag);
        }
    }

    private void addParentAgents() {
        VocabLauncher runner = (VocabLauncher) VocabLauncher.getJaCaMoRunner();
        jaCaMoProject = runner.getJaCaMoProject();
        Agents agents = confFile.agents;
        int numFamily = agents.getChild();
        double percent = agents.getPercentSingleParents();
        double totalParents = numFamily * (1 + (100 - percent) / 100);
        int extraParents = (int) (totalParents - numFamily);

        CircularQueue<SES> sesQueue = new CircularQueue<>();
        sesQueue.add(confFile.getSES());
        // sesQueue will now hold all the SES categories that were loaded.
        // However, for a given collection of SES categories, they will always
        // be queued in the same order (i.e. the 'default' 3 are queued up as:
        // Working, Welfare, Professional).
        // So we will cycle through a random number of places in the queue
        Random rnd = new Random();
        int cycle = rnd.nextInt(sesQueue.getSize() * sesQueue.getSize());
        for (int i = 0; i < cycle; ++i)
            sesQueue.get();
        
        // Create a template adult agent
        JaCaMoAgentParameters agParent = new JaCaMoAgentParameters(jaCaMoProject);
        agParent.name = "parent";
        agParent.bbClass=new ClassParameters("vocab.HashMapBB");
        agParent.bbClass.addParameter("\"words_to_speak(_)\"");
        agParent.bbClass.addParameter("\"iterationsLeft(_)\"");
        try {
            agParent.setSource("parent.asl");
        } catch (Exception e) {
            e.printStackTrace();
        }
        agParent.addFocus("home_environ.utterances", "utterances");
        agParent.addFocus("sync.synchroniser", "sync");
        agParent.addRole("learning_environ.village", "adult");
        
        // Duplicate the template agent, give it its specific configuration (SES
        // category, whether to create a group or join an existing one, and how many
        // parents it is to expect if it created the group).
        for (int i = 1; i <= totalParents; ++i) {
            AgentParameters ag = agParent.copy();
            ag.name += i;
            try {
                JaCaMoAgentParameters jAg = (JaCaMoAgentParameters) ag;
                if (i <= numFamily) {
                    SES ses = sesQueue.get();
                    String sesString = String.format("home::ses(\"%s\",%d)", ses.getName(), ses.getQty());
                    jAg.addInitBel(ASSyntax.parseLiteral(sesString));
                    if (i <= extraParents)
                        jAg.addInitBel(ASSyntax.parseLiteral("home::numParents(2)"));
                    else
                        jAg.addInitBel(ASSyntax.parseLiteral("home::numParents(1)"));
                    jAg.addInitGoal(ASSyntax.parseLiteral("create_home_group(" + i + ")"));
                } else {
                    jAg.addInitGoal(ASSyntax.parseLiteral("join_home_group(home_" + (i - numFamily) + ")"));
                }
            } catch (ParseException ex) {
            }
            // Add the agent to the system.
            jaCaMoProject.addAgent(ag);
        }
    }

    private void addSiblingAgents() {
        // NOT IMPLEMENTED
    }

    @Override
    public void start() {
        super.start();
    }
}