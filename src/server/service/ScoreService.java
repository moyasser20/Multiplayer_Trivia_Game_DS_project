package server.service;

import server.model.Score;
import server.repository.ScoreRepository;

import java.util.List;

public class ScoreService {

    private final ScoreRepository repo;

    public ScoreService(ScoreRepository repo){
        this.repo = repo;
    }

    public void addScore(String username,int score){
        repo.saveScore(new Score(username,score));
    }

    public List<Score> getUserScores(String username){
        // filter scores for the given user, since ScoreRepository
        // does not expose a user-specific method
        return repo.getScores().stream()
                .filter(s -> s.getUsername().equals(username))
                .toList();
    }
}