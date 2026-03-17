package server.service;

import server.model.Question;
import server.repository.QuestionRepository;
import server.lookup.LookupClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class QuestionService {

    private final QuestionRepository repository;
    private final List<Question> localQuestions;
    private final LookupClient lookupClient;

    /**
     * Game server will call the lookup server to retrieve questions.
     * Local repository is used as fallback when lookup server is down.
     */
    public QuestionService(QuestionRepository repository, String lookupHost, int lookupPort) {
        this.repository = repository;
        this.localQuestions = repository.findAll();
        this.lookupClient = new LookupClient(lookupHost, lookupPort);
    }

    // Normal question (category + difficulty)

    public Question getRandomQuestion(String category, String difficulty) {
        // try lookup server first
        Question remote = lookupClient.getOne(category, difficulty);
        if (remote != null) return remote;

        // fallback: local file
        List<Question> filtered = localQuestions.stream()
                .filter(q -> q.getCategory().equalsIgnoreCase(category))
                .filter(q -> q.getDifficulty().equalsIgnoreCase(difficulty))
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            return null;
        }

        Random rand = new Random();

        return filtered.get(rand.nextInt(filtered.size()));
    }

    // ---------------- RANDOM TRIVIA ----------------

    public Question getRandomTriviaQuestion(){
        Question remote = lookupClient.getOne("*", "*");
        if (remote != null) return remote;

        if(localQuestions.isEmpty()){
            return null;
        }

        Random rand = new Random();

        return localQuestions.get(rand.nextInt(localQuestions.size()));
    }

    /** Fetch a per-game batch (so each game can have its own list). */
    public List<Question> getBatch(String category, String difficulty, int count) {
        List<Question> remote = lookupClient.getBatch(category, difficulty, count);
        if (!remote.isEmpty()) return remote;

        // fallback: local pick without replacement
        List<Question> filtered = localQuestions.stream()
                .filter(q -> category == null || category.isBlank() || category.equals("*") || q.getCategory().equalsIgnoreCase(category))
                .filter(q -> difficulty == null || difficulty.isBlank() || difficulty.equals("*") || q.getDifficulty().equalsIgnoreCase(difficulty))
                .collect(Collectors.toList());

        if (filtered.isEmpty()) return List.of();

        Random rand = new Random();
        List<Question> result = new ArrayList<>();
        for (int i = 0; i < count && !filtered.isEmpty(); i++) {
            int idx = rand.nextInt(filtered.size());
            result.add(filtered.remove(idx));
        }
        return result;
    }
}