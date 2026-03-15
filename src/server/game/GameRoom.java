package server.game;

import server.model.Question;
import server.service.QuestionService;

import java.io.BufferedReader;
import java.io.PrintWriter;

public class GameRoom {

    private Team teamA;
    private Team teamB;
    private QuestionService questionService;

    public GameRoom(Team teamA, Team teamB, QuestionService questionService) {
        this.teamA = teamA;
        this.teamB = teamB;
        this.questionService = questionService;
    }

    public void startGame(String category, String difficulty, int numQuestions,
                          BufferedReader inA, BufferedReader inB) throws Exception {

        broadcast("GAME STARTED!");
        broadcast("Team A: " + teamA.getTeamName());
        broadcast("Team B: " + teamB.getTeamName());

        for(int i = 0; i < numQuestions; i++){

            Question q = questionService.getRandomQuestion(category, difficulty);

            broadcast("QUESTION: " + q.getText());

            char option = 'A';

            for(String choice : q.getChoices()){
                broadcast(option + ") " + choice);
                option++;
            }

            broadcast("15 seconds to answer!");

            long endTime = System.currentTimeMillis() + 15000;

            String answerA = null;
            String answerB = null;

            if(inA.ready()) answerA = inA.readLine();
            if(inB.ready()) answerB = inB.readLine();

            while(System.currentTimeMillis() < endTime){

                if(answerA == null && inA.ready())
                    answerA = inA.readLine();

                if(answerB == null && inB.ready())
                    answerB = inB.readLine();
            }

            evaluate(q, answerA, answerB);
        }

        broadcast("GAME OVER");

        broadcast(teamA.getTeamName() + " score: " + teamA.getScore());
        broadcast(teamB.getTeamName() + " score: " + teamB.getScore());
    }

    private void evaluate(Question q, String a, String b){

        if(a != null && a.equalsIgnoreCase(q.getCorrectAnswer())){
            teamA.addScore(10);
            broadcast("Team " + teamA.getTeamName() + " correct!");
        }

        if(b != null && b.equalsIgnoreCase(q.getCorrectAnswer())){
            teamB.addScore(10);
            broadcast("Team " + teamB.getTeamName() + " correct!");
        }
    }

    private void broadcast(String message){

        for(PrintWriter out : teamA.getOutputs())
            out.println(message);

        for(PrintWriter out : teamB.getOutputs())
            out.println(message);
    }
}