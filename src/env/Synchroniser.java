package vocab;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

import cartago.Artifact;
import cartago.GUARD;
import cartago.OPERATION;
import cartago.ObsProperty;
import jason.runtime.RuntimeServicesFactory;

import vocab.DataLogger;

public class Synchroniser extends Artifact {
    private static int numFamily = 0;
    private static int numParents = 0;
    private static ArrayList<String> homeGroups = new ArrayList<>();
    private static int numGroupsCreated = 0;
    private static int completedYearCount = 0;
    private static int parentsFinished = 0;
    private static int childrenFinalised = 0;
    private static int cyclesCompleted = 0;
    private static int numYears = 0;
    private static int agentAge = 0;

    // Days per simulated year; 365 by default, overridable from the config
    // file (agents.days_per_year) - mainly for quick smoke-test runs.
    private static int CYCLES_PER_YEAR = 365;

    public static void setDaysPerYear(int days) {
        if (days > 0)
            CYCLES_PER_YEAR = days;
    }
    private static final ReentrantLock lock = new ReentrantLock();
    // Set once the final year has completed.  Prevents any further daily
    // cycling (a stray extra 'day' otherwise can run while the agents are
    // finalising, double-signalling 'finalise' and corrupting the results).
    private static boolean simulationEnding = false;

    void init() {
        defineObsProperty("status", "NotReady");
        defineObsProperty("year", completedYearCount + 1);
        defineObsProperty("day", cyclesCompleted + 1);
        defineObsProperty("agent_age", agentAge);
        defineObsProperty("children_finished", childrenFinalised);
    }

    public static void setAgentConfig(Agents agentConfig) {
        numFamily = agentConfig.getChild();
        numParents = (int) (numFamily * (1 + (100 - agentConfig.getPercentSingleParents()) / 100));
        agentAge = agentConfig.getAgeStart();
        numYears = agentConfig.getAgeFinish() - agentConfig.getAgeStart() + 1;
    }

    @OPERATION
    public void groupCreated(String groupName) {
        if (numGroupsCreated <= numFamily && !homeGroups.contains(groupName)) {
            homeGroups.add(groupName);
            ++numGroupsCreated;
            if (numGroupsCreated == numFamily) {
                ObsProperty status = getObsProperty("status");
                status.updateValue("Ready");
                System.out.println("Simulation ready to start.");
            }
        }
    }

    // Call this method to actually set the 'status' observable property of the
    // artefact. The calling methods are assumed to provide sanity checks to ensure
    // this method is called only when it makes sense to do so...
    private void setStatus(String newStatus) {
        ObsProperty status = getObsProperty("status");
        status.updateValue(newStatus);
    }

    // It's time to step through to the next day of the simulation
    private void endOfDay() {
        if (simulationEnding || "Finished".equals(getObsProperty("status").stringValue()))
            return;
        parentsFinished = 0;
        setStatus("HomeFinished");
        ++cyclesCompleted;
        System.out.println("Finished day " + cyclesCompleted + " of year " + (completedYearCount + 1) + ".");
        // Have we reached the end of the year?
        if (cyclesCompleted < CYCLES_PER_YEAR) {
            // More days in this year.
            ObsProperty day = getObsProperty("day");
            day.updateValue(cyclesCompleted + 1);
            setStatus("StartSchool");
        } else {
            // Finished the 'year'.
            ++completedYearCount;
            if (completedYearCount < numYears) {
                // We still have more years to work through.
                // Children record their annual stats on this signal; on the
                // final year the 'finalise' signal (below) handles it instead,
                // so the stats are guaranteed to be recorded before the CSV
                // files are written.
                signal("newYear");
                ++agentAge;
                ObsProperty age = getObsProperty("agent_age");
                age.updateValue(agentAge);
                ObsProperty year = getObsProperty("year");
                year.updateValue(completedYearCount + 1);
                cyclesCompleted = 0;
                ObsProperty day = getObsProperty("day");
                day.updateValue(cyclesCompleted + 1);
                System.out.println("Starting a new year");
                setStatus("StartSchool");
            } else {
                // All done - stop the daily cycle, then signal all agents to
                // finalise themselves.
                simulationEnding = true;
                signal("finalise");
            }
        }
    }

    private boolean isTeacher(String agentName) {
        return (agentName == null || agentName.isBlank()) ? false : agentName.equals("classroom_teacher");
    }

    // The teacher has indicated that the school is 'ready'.
    @OPERATION
    public void schoolReady() {
        if (!isTeacher(getCurrentOpAgentId().getAgentName()))
            return;
        setStatus("StartSchool");
    }

    // The teacher has indicated that they have 'started' school for the day.
    @OPERATION
    public void startSchool() {
        if (simulationEnding || !isTeacher(getCurrentOpAgentId().getAgentName()))
            return;
        setStatus("SchoolStarted");
    }

    // The teacher has indicated that they are ready for school to finish for the
    // day.
    @OPERATION
    public void finishSchool() {
        if (simulationEnding || !isTeacher(getCurrentOpAgentId().getAgentName()))
            return;
        setStatus("SchoolFinished");
        setStatus("StartHome");
    }

    /*
     * Each parent reports when they have finished their tasks for this day's cycle.
     * This then waits for all of them to have reported in.
     * As the 'end of day' only needs to happen once for each day yet ALL parent
     * agents will have called this method, we use the ReentrantLock's tryLock()
     * method to ensure that only one of them causes endOfDay() to be called.
     * tryLock() returns immediately in all situations, returning 'true' if the lock
     * was successfully obtained (or already held by this specific thread and
     * 'false' if it failed to acquire the lock.
     */
    @OPERATION
    public void finishedHome() {
        // Once the final year is over, ignore any late day-cycle activity:
        // blocking here would park a workspace thread forever and starve the
        // finalisation operations (deadlocking the results write).
        if (simulationEnding)
            return;
        ++parentsFinished;
        if (lock.tryLock()) {
            await("allParentsFinished");
            endOfDay();
            lock.unlock();
        }
    }

    @GUARD
    boolean allParentsFinished() {
        return parentsFinished == numParents;
    }

    /*
     * Daily utterance handshake between each parent and their child.
     * The child reports each utterance batch it has finished processing; the
     * parent (which knows exactly how many batches it sent) blocks until the
     * child has processed all of them before ending its day.  Doing this
     * count inside the artifact makes the handshake immune to agent-side
     * message/belief ordering, which previously left children stuck in a
     * 'Busy - Listening' state and hung the simulation.
     */
    private static HashMap<String, Integer> utteranceBatchesProcessed = new HashMap<>();

    // Called by a child agent each time it finishes processing one batch of
    // utterances.
    @OPERATION
    public void utteranceBatchProcessed() {
        String childName = getCurrentOpAgentId().getAgentName();
        utteranceBatchesProcessed.merge(childName, 1, Integer::sum);
    }

    // Called by a parent agent after it has sent all of the day's utterance
    // batches.  Blocks until the named child has processed that many batches.
    @OPERATION
    public void awaitChildHeardAll(String childName, int batchesSent) {
        // After the final year there is nothing left to coordinate; return
        // immediately so a stray late home phase cannot park a workspace
        // thread (see finishedHome above).
        if (simulationEnding)
            return;
        await("childHeardAll", childName, batchesSent);
        // Consume this day's acknowledgements so tomorrow starts from zero.
        utteranceBatchesProcessed.merge(childName, -batchesSent, Integer::sum);
    }

    @GUARD
    boolean childHeardAll(String childName, int batchesSent) {
        return utteranceBatchesProcessed.getOrDefault(childName, 0) >= batchesSent;
    }

    /*
     * At the end of the simulation, each child reports when they are definitely
     * finished. This method then checks that the synchroniser itself thinks it's
     * finished before trying to write out the results.
     * As with the finishedHome() method above, tryLock() is used to ensure only one
     * attempt to write the file is made.
     */
    @OPERATION
    public void childAgentFinalised() {
        ++childrenFinalised;
        getObsProperty("children_finished").updateValue(childrenFinalised);
        await("allChildrenFinished");
        if (!"Finished".equals(getObsProperty("status").stringValue()) && lock.tryLock()) {
            // We're finished...
            setStatus("Finished");
            // Save the logged results
            DataLogger.writeFiles();
            // Shutdown JaCaMo
            System.out.println("Shutting down environment...");
            try {
                RuntimeServicesFactory.get().stopMAS(500, true, 0);
            } catch (Exception e) {

            }
            // Tell the user that's done
            System.out.println("Shutdown complete.");
            // Tell the user where the results are - this is done after shutting down JaCaMo
            // so that the user knows where they can find their results as there is a
            // quantity of output from JaCaMo as it goes down.
            System.out.println("Summary of results saved to: " + DataLogger.outputSummaryLocation());
            System.out.println("Word count results saved to: " + DataLogger.outputWordsLocation());
            lock.unlock();
        }
    }

    @GUARD
    boolean allChildrenFinished() {
        return childrenFinalised == numFamily;
    }
}
