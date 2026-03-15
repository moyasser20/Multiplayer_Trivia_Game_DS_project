package server.service;

import server.model.Question;
import server.repository.QuestionRepository;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class QuestionService {

    private QuestionRepository repository;
    private List<Question> questions;

    public QuestionService(QuestionRepository repository) {

        this.repository = repository;
        this.questions = repository.findAll();
    }

    public Question getRandomQuestion(String category, String difficulty) {

        List<Question> filtered = questions.stream()
                .filter(q -> q.getCategory().equalsIgnoreCase(category))
                .filter(q -> q.getDifficulty().equalsIgnoreCase(difficulty))
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            return null;
        }

        Random rand = new Random();

        return filtered.get(rand.nextInt(filtered.size()));
    }

}
