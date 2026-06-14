package vocab;

/*
 * A named child-agent profile, loaded from a [[child_profile]] section of
 * simulation.conf.
 *
 * attentivenessHeard/Seen: probability (0.0-1.0) that the child attends to
 * any single word it hears (speech) / sees (print).  1.0 = attends to every
 * word (the original model's behaviour).
 *
 * thresholdSeen/Heard/Both: the word-learning rule parameters.  A word is
 * learned when seen > thresholdSeen, OR heard > thresholdHeard, OR both
 * seen and heard > thresholdBoth.  Defaults (20/20/12) reproduce the
 * baseline rule exactly.
 */
public record ChildProfile(String name, int count,
        double attentivenessHeard, double attentivenessSeen,
        int thresholdSeen, int thresholdHeard, int thresholdBoth) {

    public static final double ATTENTIVENESS_DEFAULT = 1.0;
    public static final int THRESHOLD_SEEN_DEFAULT = 20;
    public static final int THRESHOLD_HEARD_DEFAULT = 20;
    public static final int THRESHOLD_BOTH_DEFAULT = 12;

    public static ChildProfile typical(int count) {
        return new ChildProfile("typical", count,
                ATTENTIVENESS_DEFAULT, ATTENTIVENESS_DEFAULT,
                THRESHOLD_SEEN_DEFAULT, THRESHOLD_HEARD_DEFAULT, THRESHOLD_BOTH_DEFAULT);
    }
}
