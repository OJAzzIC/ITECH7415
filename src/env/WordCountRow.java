package vocab;

record WordCountRow(String name, String profile, String parentProfile, String homeProfile,
        String ses, int age,
        int uniqueSeen, int uniqueHeard, int uniqueEncountered, int learned) {
    String[] toArray() {
        return new String[] {
                name,
                profile,
                parentProfile,
                homeProfile,
                ses,
                Integer.toString(age),
                Integer.toString(uniqueSeen),
                Integer.toString(uniqueHeard),
                Integer.toString(uniqueEncountered),
                Integer.toString(learned)
        };
    }
}
