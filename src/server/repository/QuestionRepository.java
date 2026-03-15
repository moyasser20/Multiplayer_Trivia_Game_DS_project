package server.repository;

import server.model.Question;
import java.util.List;

public interface QuestionRepository {

    List<Question> findAll();

}
