package vocab;

import java.util.ArrayList;
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

    private static final int CYCLES_PER_YEAR = 365;
    private static final ReentrantLock lock = new ReentrantLock();

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
        if ("Finished".equals(getObsProperty("status").stringValue()))
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
            signal("newYear");
            ++completedYearCount;
            if (completedYearCount < numYears) {
                // We still have more years to work through.
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
                // All done, signal all agents to finalise themselves.
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
        if (!isTeacher(getCurrentOpAgentId().getAgentName()))
            return;
        setStatus("SchoolStarted");
    }

    // The teacher has indicated that they are ready for school to finish for the
    // day.
    @OPERATION
    public void finishSchool() {
        if (!isTeacher(getCurrentOpAgentId().getAgentName()))
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
        if (getObsProperty("status").stringValue() != "Finished" && lock.tryLock()) {
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
