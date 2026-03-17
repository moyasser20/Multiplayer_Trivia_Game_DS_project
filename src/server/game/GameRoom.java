package server.game;

import server.model.Question;
import server.service.QuestionService;

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

    public void startGame(int numQuestions) throws IOException {
        started.set(true);

        broadcast("GAME STARTED!");
        broadcast("Team " + teamA.getTeamName() + ": " + teamA.size() + " players");
        broadcast("Team " + teamB.getTeamName() + ": " + teamB.size() + " players");

        // Fetch a per-game batch so each game has its own questions.
        // Category/difficulty are currently mixed; you can change to specific ones later.
        java.util.List<Question> questions = questionService.getBatch("*", "*", numQuestions);
        if (questions.isEmpty()) {
            broadcast("No questions available.");
            return;
        }

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);

            currentAnswers.clear();
            acceptingAnswers.set(true);

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
    }

    private void evaluate(Question q) {
        String correct = q.getCorrectAnswer();
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
