package server.service;

import server.model.Score;
import server.repository.ScoreRepository;

import java.util.List;

public class ScoreService {

    private ScoreRepository scoreRepository;

    public ScoreService(ScoreRepository scoreRepository) {
        this.scoreRepository = scoreRepository;
    }

    public void addScore(String username, int score){

        Score newScore = new Score(username, score);

        scoreRepository.saveScore(newScore);
    }

    public List<Score> getAllScores(){
        return scoreRepository.getScores();
    }

}
