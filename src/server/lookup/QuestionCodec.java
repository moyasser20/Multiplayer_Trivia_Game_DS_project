package server.lookup;

import server.model.Question;

import java.util.Arrays;
import java.util.List;

public final class QuestionCodec {
    private QuestionCodec() {}

    public static String encode(Question q) {
        String choices = String.join(";", q.getChoices());
        return q.getText() + "|" + q.getCategory() + "|" + q.getDifficulty() + "|" + choices + "|" + q.getCorrectAnswer();
    }

    public static Question decode(String line) {
        if (line == null) return null;
        String[] parts = line.split("\\|");
        if (parts.length < 5) return null;
        String text = parts[0];
        String category = parts[1];
        String difficulty = parts[2];
        List<String> choices = Arrays.asList(parts[3].split(";"));
        String correct = parts[4];
        return new Question(text, category, difficulty, choices, correct);
    }
}

