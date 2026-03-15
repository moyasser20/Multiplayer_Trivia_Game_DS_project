package server.handler;

import server.model.Question;
import server.service.AuthService;
import server.service.QuestionService;
import server.service.ScoreService;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientHandler implements Runnable {

    private Socket socket;
    private AuthService authService;
    private QuestionService questionService;
    private ScoreService scoreService;

    private boolean loggedIn = false;
    private String currentUser;

    // Teams
    private static List<String> teamA = new ArrayList<>();
    private static List<String> teamB = new ArrayList<>();

    public ClientHandler(Socket socket,
                         AuthService authService,
                         QuestionService questionService,
                         ScoreService scoreService) {

        this.socket = socket;
        this.authService = authService;
        this.questionService = questionService;
        this.scoreService = scoreService;
    }

    @Override
    public void run() {

        try {

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            out.println("WELCOME TO TRIVIA SERVER");

            String request;

            while ((request = in.readLine()) != null) {

                if(request.equals("-") || request.equalsIgnoreCase("QUIT")){
                    out.println("GOODBYE");
                    break;
                }

                String[] parts = request.split(" ");

                // ---------------- REGISTER ----------------

                if(parts[0].equalsIgnoreCase("REGISTER")){

                    if(parts.length < 4){
                        out.println("INVALID_REGISTER_FORMAT");
                        continue;
                    }

                    String name = parts[1];
                    String username = parts[2];
                    String password = parts[3];

                    out.println(authService.register(name, username, password));
                }

                // ---------------- LOGIN ----------------

                else if(parts[0].equalsIgnoreCase("LOGIN")){

                    if(parts.length < 3){
                        out.println("INVALID_LOGIN_FORMAT");
                        continue;
                    }

                    String username = parts[1];
                    String password = parts[2];

                    String result = authService.login(username, password);

                    out.println(result);

                    if(result.equals("LOGIN_SUCCESS")){

                        loggedIn = true;
                        currentUser = username;

                        out.println("MENU:");
                        out.println("1) Single Player");
                        out.println("2) Multiplayer");
                        out.println("3) Quit");
                    }
                }

                // ---------------- SINGLE PLAYER ----------------

                else if(loggedIn && parts[0].equals("1")){

                    out.println("Single Player Mode Activated");

                    startSinglePlayer(out, in);
                }

                // ---------------- MULTIPLAYER MENU ----------------

                else if(loggedIn && parts[0].equals("2")){

                    out.println("MULTIPLAYER OPTIONS:");
                    out.println("JOIN_TEAM_A");
                    out.println("JOIN_TEAM_B");
                    out.println("START_GAME");
                }

                // ---------------- JOIN TEAM A ----------------

                else if(loggedIn && parts[0].equalsIgnoreCase("JOIN_TEAM_A")){

                    if(!teamA.contains(currentUser)){
                        teamA.add(currentUser);
                    }

                    out.println("Joined Team Alpha");
                    out.println("Team Alpha players: " + teamA.size());
                }

                // ---------------- JOIN TEAM B ----------------

                else if(loggedIn && parts[0].equalsIgnoreCase("JOIN_TEAM_B")){

                    if(!teamB.contains(currentUser)){
                        teamB.add(currentUser);
                    }

                    out.println("Joined Team Beta");
                    out.println("Team Beta players: " + teamB.size());
                }

                // ---------------- START MULTIPLAYER GAME ----------------

                else if(loggedIn && parts[0].equalsIgnoreCase("START_GAME")){

                    if(teamA.size() == 0 || teamB.size() == 0){
                        out.println("Both teams must have players");
                        continue;
                    }

                    if(teamA.size() != teamB.size()){
                        out.println("Teams must have equal number of players");
                        continue;
                    }

                    out.println("GAME STARTED!");
                    out.println("Team Alpha: " + teamA);
                    out.println("Team Beta: " + teamB);

                    // For now use same single player engine
                    startSinglePlayer(out, in);
                }

                // ---------------- QUIT ----------------

                else if(loggedIn && parts[0].equals("3")){

                    out.println("GOODBYE");
                    break;
                }

                else{
                    out.println("UNKNOWN_COMMAND");
                }
            }

            socket.close();

        } catch (Exception e) {

            System.out.println("Client disconnected unexpectedly");
        }
    }

    // ---------------- SINGLE PLAYER GAME ----------------

    private void startSinglePlayer(PrintWriter out, BufferedReader in) throws IOException {

        out.println("Enter category (Math / Science / Geography):");
        String category = in.readLine();

        out.println("Enter difficulty (easy / medium / hard):");
        String difficulty = in.readLine();

        out.println("How many questions?");
        int numQuestions;

        try{
            numQuestions = Integer.parseInt(in.readLine());
        }catch(Exception e){
            out.println("INVALID_NUMBER, defaulting to 5");
            numQuestions = 5;
        }

        int score = 0;

        for(int i = 0; i < numQuestions; i++){

            Question q = questionService.getRandomQuestion(category, difficulty);

            if(q == null){
                out.println("No more questions available.");
                break;
            }

            score += askQuestionWithTimer(q, out, in);
        }

        out.println("GAME OVER! Your score = " + score);

        scoreService.addScore(currentUser, score);
    }

    // ---------------- QUESTION WITH TIMER ----------------

    private int askQuestionWithTimer(Question q, PrintWriter out, BufferedReader in) throws IOException {

        AtomicBoolean questionActive = new AtomicBoolean(true);

        out.println("QUESTION: " + q.getText());

        char option = 'A';

        for(String choice : q.getChoices()){
            out.println(option + ") " + choice);
            option++;
        }

        out.println("You have 15 seconds to answer!");

        Thread timer = new Thread(() -> {

            try {

                Thread.sleep(5000);
                if(questionActive.get()) out.println("10 seconds left");

                Thread.sleep(5000);
                if(questionActive.get()) out.println("5 seconds left");

                Thread.sleep(5000);

                if(questionActive.get()){
                    out.println("TIME UP!");
                    questionActive.set(false);
                }

            } catch (InterruptedException ignored) {}
        });

        timer.start();

        String answer = in.readLine();

        if(!questionActive.get()){
            out.println("Answer ignored (time finished)");
            return 0;
        }

        questionActive.set(false);

        if(answer == null) return 0;

        answer = answer.trim();

        if(answer.equalsIgnoreCase(q.getCorrectAnswer())){

            out.println("CORRECT!");
            return 10;

        }else{

            out.println("WRONG! Correct answer: " + q.getCorrectAnswer());
            return 0;
        }
    }
}