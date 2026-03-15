package server.repository;

import server.model.Score;
import java.util.List;

public interface ScoreRepository {

    List<Score> getScores();

    void saveScore(Score score);
}
