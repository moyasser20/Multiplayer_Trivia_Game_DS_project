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
    private final boolean lookupOnly;
    private final ThreadLocal<LookupClient.Status> lastLookupStatus = ThreadLocal.withInitial(() -> LookupClient.Status.ERROR);

    /**
     * Game server will call the lookup server to retrieve questions.
     * Local repository is used as fallback when lookup server is down.
     */
    public QuestionService(QuestionRepository repository, String lookupHost, int lookupPort, boolean lookupOnly) {
        this.repository = repository;
        this.lookupOnly = lookupOnly;
        this.localQuestions = (lookupOnly || repository == null) ? List.of() : repository.findAll();
        this.lookupClient = new LookupClient(lookupHost, lookupPort);
    }

    // Normal question (category + difficulty)

    public Question getRandomQuestion(String category, String difficulty) {
        // try lookup server first
        LookupClient.Result res = lookupClient.getBatchResult(category, difficulty, 1);
        lastLookupStatus.set(res.status);
        Question remote = res.questions.isEmpty() ? null : res.questions.get(0);
        if (remote != null) {
            System.out.println("[LOOKUP] one category=" + category + " difficulty=" + difficulty);
            return remote;
        }

        if (lookupOnly) {
            System.out.println("[LOOKUP_ONLY] lookup unavailable or no matches for category=" + category + " difficulty=" + difficulty);
            return null;
        }

        // fallback: local file
        lastLookupStatus.set(LookupClient.Status.ERROR);
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
        LookupClient.Result res = lookupClient.getBatchResult("*", "*", 1);
        lastLookupStatus.set(res.status);
        Question remote = res.questions.isEmpty() ? null : res.questions.get(0);
        if (remote != null) {
            System.out.println("[LOOKUP] one category=* difficulty=*");
            return remote;
        }

        if (lookupOnly) {
            System.out.println("[LOOKUP_ONLY] lookup unavailable or no matches for mixed trivia");
            return null;
        }

        if(localQuestions.isEmpty()){
            return null;
        }

        Random rand = new Random();

        lastLookupStatus.set(LookupClient.Status.ERROR);
        return localQuestions.get(rand.nextInt(localQuestions.size()));
    }

    /** Fetch a per-game batch (so each game can have its own list). */
    public List<Question> getBatch(String category, String difficulty, int count) {
        LookupClient.Result res = lookupClient.getBatchResult(category, difficulty, count);
        lastLookupStatus.set(res.status);
        List<Question> remote = res.questions;
        if (!remote.isEmpty()) {
            System.out.println("[LOOKUP] batch category=" + category + " difficulty=" + difficulty + " count=" + count);
            return remote;
        }

        if (lookupOnly) {
            System.out.println("[LOOKUP_ONLY] lookup unavailable or no matches for batch category=" + category + " difficulty=" + difficulty + " count=" + count);
            return List.of();
        }

        // fallback: local pick without replacement
        lastLookupStatus.set(LookupClient.Status.ERROR);
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

    public LookupClient.Status getLastLookupStatus() {
        return lastLookupStatus.get();
    }
}