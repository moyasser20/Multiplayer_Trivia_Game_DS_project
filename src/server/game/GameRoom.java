package server.game;

import server.model.Question;
import server.service.QuestionService;
import server.handler.ClientHandler;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameRoom {

    private final Team teamA;
    private final Team teamB;
    private final QuestionService questionService;
    private final int minRoomPlayers;
    private final int maxRoomPlayers;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean acceptingAnswers = new AtomicBoolean(false);
    private final Map<String, String> currentAnswers = new ConcurrentHashMap<>();

    // per-question history for breakdown
    private static class QuestionResult {
        Question question;
        Map<String,String> answers = new java.util.HashMap<>();
        QuestionResult(Question q){ this.question = q; }
    }

    private final java.util.List<QuestionResult> history = new java.util.ArrayList<>();

    public GameRoom(Team teamA, Team teamB, QuestionService questionService,
                    int minRoomPlayers, int maxRoomPlayers) {
        this.teamA = teamA;
        this.teamB = teamB;
        this.questionService = questionService;
        this.minRoomPlayers = minRoomPlayers;
        this.maxRoomPlayers = maxRoomPlayers;
    }

    public Team getTeamA() {
        return teamA;
    }

    public Team getTeamB() {
        return teamB;
    }

    public boolean isStarted() {
        return started.get();
    }

    public boolean isAcceptingAnswers() {
        return acceptingAnswers.get();
    }

    public int totalPlayers() {
        return teamA.size() + teamB.size();
    }

    public boolean isFull() {
        return totalPlayers() >= maxRoomPlayers;
    }

    public boolean isReady() {
        return totalPlayers() >= minRoomPlayers
                && teamA.size() > 0
                && teamB.size() > 0;
    }

    /**
     * Called from ClientHandler when user sends A-D during a question.
     * Only one answer per user per question (first submission counts).
     */
    public void submitAnswer(String username, String answer) {
        if (!acceptingAnswers.get()) return;
        if (answer == null || !answer.trim().toUpperCase().matches("[A-D]")) return;
        currentAnswers.putIfAbsent(username, answer.trim().toUpperCase());
    }

    public void startGame(String category, String difficulty, int numQuestions) throws IOException {
        started.set(true);

        broadcast("GAME STARTED!");
        broadcast("Team " + teamA.getTeamName() + ": " + teamA.size() + " players");
        broadcast("Team " + teamB.getTeamName() + ": " + teamB.size() + " players");

        // Fetch a per-game batch so each game has its own questions.
        java.util.List<Question> questions = questionService.getBatch(category, difficulty, numQuestions);
        if (questions.isEmpty()) {
            broadcast("No questions available.");
            return;
        }

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            // multiplayer question
            ClientHandler.incrementMultiQuestionPlayed();

            currentAnswers.clear();
            acceptingAnswers.set(true);
            QuestionResult qr = new QuestionResult(q);
            history.add(qr);

            broadcast("QUESTION " + (i + 1) + ": " + q.getText());
            char option = 'A';
            for (String choice : q.getChoices()) {
                broadcast(option + ") " + choice);
                option++;
            }
            broadcast("15 seconds to answer! (Reply with A, B, C, or D)");

            // Countdown 15 -> 10 -> 5 -> TIME UP (same as single player)
            Thread timerThread = new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    broadcast("10 seconds left");
                    Thread.sleep(5000);
                    broadcast("5 seconds left");
                    Thread.sleep(5000);
                    broadcast("TIME UP!");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            timerThread.start();
            try {
                timerThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            acceptingAnswers.set(false);

            evaluate(q);
        }

        broadcast("GAME OVER");
        broadcast(teamA.getTeamName() + " score: " + teamA.getScore());
        broadcast(teamB.getTeamName() + " score: " + teamB.getScore());

        // determine wins for teams and update multiplayer highest score
        int gameHigh = Math.max(teamA.getScore(), teamB.getScore());
        ClientHandler.updateMultiHighestScore(gameHigh);
        if (teamA.getScore() > teamB.getScore()) {
            for (String u : teamA.getPlayers()) {
                ClientHandler.registerWin(u);
            }
        } else if (teamB.getScore() > teamA.getScore()) {
            for (String u : teamB.getPlayers()) {
                ClientHandler.registerWin(u);
            }
        }

        // detailed breakdown
        broadcast("----- QUESTION BREAKDOWN -----");
        for (int i = 0; i < history.size(); i++) {
            QuestionResult qr = history.get(i);
            broadcast("Q" + (i+1) + ": " + qr.question.getText());
            broadcast("Correct: " + qr.question.getCorrectAnswer());
            for (Map.Entry<String,String> e : qr.answers.entrySet()) {
                broadcast("  " + e.getKey() + " answered: " + e.getValue());
            }
        }
        broadcast("------------------------------");
    }

    private void evaluate(Question q) {
        String correct = q.getCorrectAnswer();
        QuestionResult last = history.isEmpty() ? null : history.get(history.size() - 1);
        if (last != null) {
            last.answers.putAll(currentAnswers);
        }
        for (Map.Entry<String, String> e : currentAnswers.entrySet()) {
            String username = e.getKey();
            String answer = e.getValue();
            if (answer == null || !answer.equalsIgnoreCase(correct)) continue;
            if (teamA.getPlayers().contains(username)) {
                teamA.addScore(10);
                broadcast("Team " + teamA.getTeamName() + " correct!");
            } else if (teamB.getPlayers().contains(username)) {
                teamB.addScore(10);
                broadcast("Team " + teamB.getTeamName() + " correct!");
            }
        }
        broadcast("Correct answer: " + correct);
    }

    private void broadcast(String message) {
        for (PrintWriter out : teamA.getOutputs()) {
            out.println(message);
        }
        for (PrintWriter out : teamB.getOutputs()) {
            out.println(message);
        }
    }
}
