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

    // Admin
    private boolean isAdmin = false;
    private static int totalQuestionsPlayed = 0;
    private static int highestScoreEver = 0;

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

                        if(username.equalsIgnoreCase("admin")){
                            isAdmin = true;
                            showAdminPanel(out);
                        }else{
                            out.println("MENU:");
                            out.println("1) Single Player");
                            out.println("2) Multiplayer");
                            out.println("3) Score History");
                            out.println("4) Quit");
                        }
                    }
                }

                // ---------------- ADMIN COMMANDS ----------------
                else if(isAdmin){

                    switch(request){

                        case "1":
                            out.println("Total players connected: " + gameScores.size());
                            break;

                        case "2":
                            List<String> topPlayers = getTopPlayers();
                            int topScore = topPlayers.isEmpty() ? 0 : gameScores.get(topPlayers.get(0));

                            out.println("Top player(s) with score " + topScore + ":");
                            for(String p : topPlayers){
                                out.println(p);
                            }
                            break;

                        case "3":
                            out.println("Total questions played: " + totalQuestionsPlayed);
                            break;

                        case "4":
                            out.println("Highest score ever: " + highestScoreEver);
                            break;

                        case "5":
                            out.println("GOODBYE ADMIN");
                            socket.close();
                            return;

                        default:
                            out.println("INVALID_ADMIN_COMMAND");
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
                    out.println("Team Alpha size: " + teamA.size());
                }

                // ---------------- JOIN TEAM B ----------------
                else if(loggedIn && request.equalsIgnoreCase("JOIN_TEAM_B")){

                    if(!teamB.contains(currentUser)){
                        teamB.add(currentUser);
                    }

                    out.println("Joined Team Beta");
                    out.println("Team Beta size: " + teamB.size());
                }

                // ---------------- START MULTIPLAYER GAME ----------------
                else if(loggedIn && request.equalsIgnoreCase("START_GAME")){

                    if(teamA.size() == 0 || teamB.size() == 0){
                        out.println("Both teams must have players");
                        continue;
                    }

                    if(teamA.size() != teamB.size()){
                        out.println("Teams must have equal players");
                        continue;
                    }

                    out.println("GAME STARTED!");
                    startMultiplayerGame(out,in);
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

                else{
                    out.println("UNKNOWN_COMMAND");
                }
            }

            socket.close();

        }catch(Exception e){
            System.out.println("Client disconnected");
        }
    }

    // ---------------- HELPER METHODS ----------------

    private List<String> getTopPlayers(){
        List<String> topPlayers = new ArrayList<>();
        int maxScore = 0;

        for(String player : gameScores.keySet()){
            int s = gameScores.get(player);
            if(s > maxScore){
                maxScore = s;
                topPlayers.clear();
                topPlayers.add(player);
            }else if(s == maxScore){
                topPlayers.add(player);
            }
        }
        return topPlayers;
    }

    private void showAdminPanel(PrintWriter out){
        out.println("ADMIN PANEL:");
        out.println("1) Total Players Connected");
        out.println("2) Player With Highest Score");
        out.println("3) Total Questions Played");
        out.println("4) Highest Score Ever");
        out.println("5) Exit");
    }

    private void finishGame(int score, PrintWriter out){
        gameScores.put(currentUser,score);

        if(score > highestScoreEver){
            highestScoreEver = score;
        }

        out.println("GAME OVER!");
        out.println("Your score = " + score);

        scoreService.addScore(currentUser,score);
        showScoreboard(out);
    }

    private void showScoreboard(PrintWriter out){
        out.println("----- FINAL SCOREBOARD -----");
        for(String player : gameScores.keySet()){
            out.println(player + " : " + gameScores.get(player));
        }
        out.println("----------------------------");
    }

    // ---------------- SINGLE PLAYER ----------------
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

    // ---------------- RANDOM TRIVIA ----------------
    private void startRandomTrivia(PrintWriter out, BufferedReader in) throws IOException {

        out.println("Random Trivia Mode");

        out.println("How many questions?");
        int numQuestions;
        try{
            numQuestions = Integer.parseInt(in.readLine());
        }catch(Exception e){
            numQuestions = 5;
        }

        int score = 0;
        for(int i=0;i<numQuestions;i++){
            Question q = questionService.getRandomTriviaQuestion();
            if(q == null){
                out.println("No questions available.");
                break;
            }
            score += askQuestionWithTimer(q,out,in);
        }

        finishGame(score,out);
    }

    // ---------------- MULTIPLAYER GAME ----------------
    private void startMultiplayerGame(PrintWriter out, BufferedReader in) throws IOException {

        // Each player answers same number of questions in sequence
        int numQuestions = 5; // default for multiplayer
        Map<String,Integer> teamScores = new HashMap<>();

        for(String player : teamA){
            currentUser = player;
            int score = 0;
            for(int i=0;i<numQuestions;i++){
                Question q = questionService.getRandomTriviaQuestion();
                if(q == null) break;
                score += askQuestionWithTimer(q,out,in);
            }
            gameScores.put(player, score);
            teamScores.put(player, score);
            if(score > highestScoreEver) highestScoreEver = score;
        }

        for(String player : teamB){
            currentUser = player;
            int score = 0;
            for(int i=0;i<numQuestions;i++){
                Question q = questionService.getRandomTriviaQuestion();
                if(q == null) break;
                score += askQuestionWithTimer(q,out,in);
            }
            gameScores.put(player, score);
            teamScores.put(player, score);
            if(score > highestScoreEver) highestScoreEver = score;
        }

        showScoreboard(out);

        // Clear teams after game
        teamA.clear();
        teamB.clear();
    }

    // ---------------- QUESTION TIMER ----------------
    private int askQuestionWithTimer(Question q, PrintWriter out, BufferedReader in) throws IOException{

        totalQuestionsPlayed++;

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