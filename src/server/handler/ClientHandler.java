package server.handler;

import server.model.Question;
import server.model.Score;
import server.service.AuthService;
import server.service.QuestionService;
import server.service.ScoreService;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientHandler implements Runnable {

    private Socket socket;
    private AuthService authService;
    private QuestionService questionService;
    private ScoreService scoreService;

    private boolean loggedIn = false;
    private String currentUser;

    private boolean singlePlayerMenu = false;

    // Teams
    private static List<String> teamA = new ArrayList<>();
    private static List<String> teamB = new ArrayList<>();

    // Game scores
    private static Map<String,Integer> gameScores = new HashMap<>();

    public ClientHandler(Socket socket,
                         AuthService authService,
                         QuestionService questionService,
                         ScoreService scoreService){

        this.socket = socket;
        this.authService = authService;
        this.questionService = questionService;
        this.scoreService = scoreService;
    }

    @Override
    public void run(){

        try{

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            PrintWriter out = new PrintWriter(
                    socket.getOutputStream(), true);

            out.println("WELCOME TO TRIVIA SERVER");

            String request;

            while((request = in.readLine()) != null){

                if(request.equalsIgnoreCase("QUIT")){
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

                    out.println(authService.register(name,username,password));
                }

                // ---------------- LOGIN ----------------

                else if(parts[0].equalsIgnoreCase("LOGIN")){

                    if(parts.length < 3){
                        out.println("INVALID_LOGIN_FORMAT");
                        continue;
                    }

                    String username = parts[1];
                    String password = parts[2];

                    String result = authService.login(username,password);

                    out.println(result);

                    if(result.equals("LOGIN_SUCCESS")){

                        loggedIn = true;
                        currentUser = username;

                        out.println("MENU:");
                        out.println("1) Single Player");
                        out.println("2) Multiplayer");
                        out.println("3) Score History");
                        out.println("4) Quit");
                    }
                }

                // ---------------- SINGLE PLAYER MENU ----------------

                else if(loggedIn && request.equals("1") && !singlePlayerMenu){

                    singlePlayerMenu = true;

                    out.println("SINGLE PLAYER OPTIONS:");
                    out.println("1) Custom Trivia");
                    out.println("2) Random Trivia");
                }

                // ---------------- CUSTOM TRIVIA ----------------

                else if(loggedIn && singlePlayerMenu && request.equals("1")){

                    singlePlayerMenu = false;

                    out.println("Custom Trivia Mode");

                    startSinglePlayer(out,in);
                }

                // ---------------- RANDOM TRIVIA ----------------

                else if(loggedIn && singlePlayerMenu && request.equals("2")){

                    singlePlayerMenu = false;

                    out.println("Random Trivia Mode");

                    startRandomTrivia(out,in);
                }

                // ---------------- MULTIPLAYER ----------------

                else if(loggedIn && request.equals("2")){

                    out.println("MULTIPLAYER OPTIONS:");
                    out.println("JOIN_TEAM_A");
                    out.println("JOIN_TEAM_B");
                    out.println("START_GAME");
                }

                // ---------------- JOIN TEAM A ----------------

                else if(loggedIn && request.equalsIgnoreCase("JOIN_TEAM_A")){

                    if(!teamA.contains(currentUser)){
                        teamA.add(currentUser);
                    }

                    out.println("Joined Team Alpha");
                    out.println("Players in Team Alpha: " + teamA.size());
                }

                // ---------------- JOIN TEAM B ----------------

                else if(loggedIn && request.equalsIgnoreCase("JOIN_TEAM_B")){

                    if(!teamB.contains(currentUser)){
                        teamB.add(currentUser);
                    }

                    out.println("Joined Team Beta");
                    out.println("Players in Team Beta: " + teamB.size());
                }

                // ---------------- START MULTIPLAYER GAME ----------------

                else if(loggedIn && request.equalsIgnoreCase("START_GAME")){

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

                    startRandomTrivia(out,in);
                }

                // ---------------- SCORE HISTORY ----------------

                else if(loggedIn && request.equals("3")){

                    out.println("YOUR SCORE HISTORY:");

                    List<Score> history = scoreService.getUserScores(currentUser);

                    if(history.isEmpty()){
                        out.println("No previous scores");
                    }

                    for(Score s : history){
                        out.println("Score: " + s.getScore());
                    }
                }

                // ---------------- QUIT ----------------

                else if(loggedIn && request.equals("4")){

                    out.println("GOODBYE");
                    break;
                }

                // ---------------- UNKNOWN ----------------

                else{
                    out.println("UNKNOWN_COMMAND");
                }

            }

            socket.close();

        }catch(Exception e){

            System.out.println("Client disconnected");
        }
    }

    // ---------------- CUSTOM TRIVIA GAME ----------------

    private void startSinglePlayer(PrintWriter out, BufferedReader in) throws IOException{

        out.println("Enter category (Math / Science / Geography):");
        String category = in.readLine();

        out.println("Enter difficulty (easy / medium / hard):");
        String difficulty = in.readLine();

        out.println("How many questions?");
        int numQuestions;

        try{
            numQuestions = Integer.parseInt(in.readLine());
        }catch(Exception e){
            numQuestions = 5;
        }

        int score = 0;

        for(int i=0;i<numQuestions;i++){

            Question q = questionService.getRandomQuestion(category,difficulty);

            if(q == null){
                out.println("No more questions");
                break;
            }

            score += askQuestionWithTimer(q,out,in);
        }

        finishGame(score,out);
    }

    // ---------------- RANDOM TRIVIA GAME ----------------

    private void startRandomTrivia(PrintWriter out, BufferedReader in) throws IOException {

        out.println("Random Trivia Mode");

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

            Question q = questionService.getRandomTriviaQuestion();

            if(q == null){
                out.println("No questions available.");
                break;
            }

            score += askQuestionWithTimer(q, out, in);
        }

        finishGame(score, out);
    }
    // ---------------- FINISH GAME ----------------

    private void finishGame(int score, PrintWriter out){

        gameScores.put(currentUser,score);

        out.println("GAME OVER!");
        out.println("Your score = " + score);

        scoreService.addScore(currentUser,score);

        showScoreboard(out);
    }

    // ---------------- SCOREBOARD ----------------

    private void showScoreboard(PrintWriter out){

        out.println("----- FINAL SCOREBOARD -----");

        for(String player : gameScores.keySet()){
            out.println(player + " : " + gameScores.get(player));
        }

        out.println("----------------------------");
    }

    // ---------------- QUESTION TIMER ----------------

    private int askQuestionWithTimer(Question q, PrintWriter out, BufferedReader in) throws IOException{

        AtomicBoolean active = new AtomicBoolean(true);

        out.println("QUESTION: " + q.getText());

        char option = 'A';

        for(String c : q.getChoices()){
            out.println(option + ") " + c);
            option++;
        }

        out.println("You have 15 seconds!");

        Thread timer = new Thread(() -> {

            try{

                Thread.sleep(5000);
                if(active.get()) out.println("10 seconds left");

                Thread.sleep(5000);
                if(active.get()) out.println("5 seconds left");

                Thread.sleep(5000);

                if(active.get()){
                    out.println("TIME UP!");
                    active.set(false);
                }

            }catch(Exception ignored){}
        });

        timer.start();

        String answer = in.readLine();

        if(!active.get()){
            out.println("Answer ignored");
            return 0;
        }

        active.set(false);

        if(answer == null) return 0;

        answer = answer.trim();

        if(!answer.matches("[A-Da-d]")){
            out.println("INVALID ANSWER");
            return 0;
        }

        if(answer.equalsIgnoreCase(q.getCorrectAnswer())){

            out.println("CORRECT!");
            return 10;

        }else{

            out.println("WRONG!");
            out.println("Correct answer: " + q.getCorrectAnswer());

            return 0;
        }
    }
}