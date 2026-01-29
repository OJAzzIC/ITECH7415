package vocab;

public class Agents {
    private int child;
    private int max_siblings;
    private int age_start;
    private int age_finish;
    private double percent_single_parents;

    public Agents(int child, int max_siblings, double percent_single_parents, int age_start, int age_finish) {
        this.child = child;
        this.max_siblings = max_siblings;
        this.age_start = age_start;
        this.age_finish = age_finish;
        this.percent_single_parents = percent_single_parents;
    }

    public int getChild() {
        return child;
    }

    public int getMaxSiblings() {
        return max_siblings;
    }

    public double getPercentSingleParents() {
        return percent_single_parents;
    }

    public int getAgeStart() {
        return age_start;
    }

    public int getAgeFinish() {
        return age_finish;
    }
}