package vocab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Stream;

import cartago.Artifact;
import cartago.OPERATION;
import cartago.OpFeedbackParam;
import vocab.WordLearntRow;

public class WordsLearnt extends Artifact {
    private record AgentDetails(String name, String ses, int age, String word) {
    }

    // HashMap, keyed on the words
    private static HashMap<String, ArrayList<AgentDetails>> wordAoA = new HashMap<>();
    // HashMap, keyed on the agent names
    private static HashMap<String, ArrayList<AgentDetails>> agentAoA = new HashMap<>();

    @OPERATION
    public void hasLearntWord(String agentName, String word, OpFeedbackParam<Boolean> result) {
        // Get the list of agents who know 'word', or an empty list
        // Convert it to a stream, to then filter it to only include entries relate to
        // 'agentName', then see how many entries there are.
        result.set(wordAoA.getOrDefault(word, new ArrayList<>())
                .stream()
                .filter(detail -> (detail.name().equals(agentName)))
                .count() != 0);
    }

    @OPERATION
    public void learnWord(String agentName, String ses, int age, String word) {
        // Get the list of agents who already know this word, or an empty list
        ArrayList<AgentDetails> learntBy = wordAoA.getOrDefault(word, new ArrayList<>());
        // Filter that list based on 'agentName', count the number of entries which
        // match. If exactly 0 entries matched, the given agent hasn't yet learnt the
        // word.
        if (learntBy.stream().filter(detail -> (detail.name().equals(agentName))).count() == 0) {
            AgentDetails newEntry = new AgentDetails(agentName, ses, age, word);
            learntBy.add(newEntry);
            // Add it to the word-keyed HashMap
            wordAoA.put(word, learntBy);
            // Add it to the agent-keyed HashMap
            if (agentAoA.containsKey(agentName)) {
                // This agent already knows some words
                agentAoA.get(agentName).add(newEntry);
            } else {
                // This is the 1st time the agent has learnt a word
                agentAoA.put(agentName, new ArrayList<>(Arrays.asList(newEntry)));
            }
        }
    }

    @OPERATION
    public void numWordsKnown(String agent, OpFeedbackParam<Integer> result) {
        result.set(agentAoA.getOrDefault(agent, new ArrayList<>()).size());
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
