package vocab;

// Define a read-only 'record' object
// 'record' is syntactic 'sugar' which creates a fully-fledged class type during
// compilation with appropriate getters and a single constructor using the
// parameters provided here.
// Nothing NEEDS to be given inside the '{}'s, however whatever is there will be
// included in the compiled class
record WordCountRow(String name, String ses, int age, int uniqueSeen, int uniqueHeard, int learned) {
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
