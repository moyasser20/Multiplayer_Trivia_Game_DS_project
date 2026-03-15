package server.model;

import java.util.List;

public class Question {

    private String text;
    private String category;
    private String difficulty;
    private List<String> choices;
    private String correctAnswer;

    public Question(String text, String category, String difficulty,
                    List<String> choices, String correctAnswer) {

        this.text = text;
        this.category = category;
        this.difficulty = difficulty;
        this.choices = choices;
        this.correctAnswer = correctAnswer;
    }

    public String getText() {
        return text;
    }

    public String getCategory() {
        return category;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public List<String> getChoices() {
        return choices;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }
}
