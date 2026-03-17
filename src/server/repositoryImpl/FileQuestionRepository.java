package server.repositoryImpl;

import server.model.Question;
import server.repository.QuestionRepository;

import java.io.*;
import java.util.*;

public class FileQuestionRepository implements QuestionRepository {

    private File file;

    public FileQuestionRepository(String path) {
        this.file = new File(path);
    }

    @Override
    public List<Question> findAll() {

        List<Question> questions = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {

            String line;

            while ((line = reader.readLine()) != null) {

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\|");

                // Expected format:
                // text|category|difficulty|choice1;choice2;choice3;choice4|correctLetter
                if (parts.length < 5) {
                    // skip malformed line instead of crashing the server
                    continue;
                }

                String text = parts[0];
                String category = parts[1];
                String difficulty = parts[2];

                List<String> choices = Arrays.asList(parts[3].split(";"));

                String correct = parts[4];

                questions.add(new Question(text, category, difficulty, choices, correct));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return questions;
    }
}
