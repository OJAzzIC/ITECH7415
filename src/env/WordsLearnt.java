package vocab;

import java.util.ArrayList;
import java.util.HashMap;

import cartago.Artifact;
import cartago.OPERATION;

import vocab.WordLearntRow;

public class WordsLearnt extends Artifact {
    private record AgentDetails(String name, String ses, int age, String word) {
    }

    // HashMap, keyed on the words
    private static HashMap<String, ArrayList<AgentDetails>> wordAoA = new HashMap<>();

    @OPERATION
    public void addLearnedWords(String agentName, String ses, Object[] wordAoa) {
        if (wordAoa.length != 0) {
            for (var obj : wordAoa) {
                String temp = ((String) obj);
                String[] tmpArray = temp.split("[()),\"]");
                String word = tmpArray[2];
                String age = tmpArray[4];
                if (word.isEmpty() || age.isEmpty())
                    continue;
                // Get the list of agents who already know this word, or an empty list
                ArrayList<AgentDetails> learntBy = wordAoA.getOrDefault(word, new ArrayList<>());
                // Filter that list based on 'agentName', count the number of entries which
                // match. If exactly 0 entries matched, the given agent hasn't yet learnt the
                // word.
                if (learntBy.stream().filter(detail -> (detail.name().equals(agentName))).count() == 0) {
                    AgentDetails newEntry = new AgentDetails(agentName, ses, Integer.parseInt(age), word);
                    learntBy.add(newEntry);
                    // Add it to the word-keyed HashMap
                    wordAoA.put(word, learntBy);
                }
            }
        }
    }

    public static ArrayList<WordLearntRow> getAll() {
        ArrayList<WordLearntRow> result = new ArrayList<>();
        for (ArrayList<AgentDetails> AoAentry : wordAoA.values()) {
            for (AgentDetails detail : AoAentry) {
                result.add(new WordLearntRow(detail.name, detail.ses, detail.word, detail.age));
            }
        }
        return result;
    }
}
