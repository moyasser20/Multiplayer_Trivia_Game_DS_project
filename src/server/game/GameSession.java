package server.game;

import server.model.Question;
import server.service.QuestionService;

import java.io.PrintWriter;
import java.util.List;

public class GameSession {

    private List<PrintWriter> players;
    private QuestionService questionService;

    public GameSession(List<PrintWriter> players, QuestionService questionService) {
        this.players = players;
        this.questionService = questionService;
    }

    public void startGame(String category, String difficulty, int numQuestions) {

        for(int i = 0; i < numQuestions; i++) {

            Question q = questionService.getRandomQuestion(category, difficulty);

            if(q == null) return;

            broadcast("QUESTION: " + q.getText());

            char option = 'A';
            for(String choice : q.getChoices()){
                broadcast(option + ") " + choice);
                option++;
            }

            startTimer();
        }

        broadcast("GAME FINISHED");
    }

    private void startTimer() {

        try {

            Thread.sleep(5000);
            broadcast("10 seconds left");

            Thread.sleep(5000);
            broadcast("5 seconds left");

            Thread.sleep(5000);
            broadcast("TIME UP");

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void broadcast(String message){

        for(PrintWriter player : players){
            player.println(message);
        }
    }
}