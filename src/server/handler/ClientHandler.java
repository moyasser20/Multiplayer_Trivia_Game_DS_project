package server.handler;

import server.model.Question;
import server.model.Score;
import server.service.AuthService;
import server.service.QuestionService;
import server.service.ScoreService;
import server.game.Team;
import server.game.GameRoom;
import server.game.GameConfig;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientHandler implements Runnable {

    private Socket socket;
    private AuthService authService;
    private QuestionService questionService;
    private ScoreService scoreService;

    private boolean loggedIn = false;
    private String currentUser;

    private boolean singlePlayerMenu = false;
    private boolean isAdmin = false;

    private static int totalQuestionsPlayed = 0;
    private static int highestScoreEver = 0;
    private static Map<String,Integer> gameScores = new ConcurrentHashMap<>();
    /** Logged-in users (connected). Added on LOGIN_SUCCESS, removed on disconnect. */
    private static Set<String> connectedUsers = ConcurrentHashMap.newKeySet();

    // Public rooms (min/max + lookup config loaded from config)
    private static final GameConfig gameConfig = new GameConfig("src/data/config.txt");
    private static List<GameRoom> publicRooms = new ArrayList<>();

    private GameRoom myPublicRoom = null;

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
        BufferedReader in = null;
        PrintWriter out = null;
        try{
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println("WELCOME TO TRIVIA SERVER");

            String request;
            while((request = in.readLine()) != null){

                if(request.equalsIgnoreCase("QUIT")){
                    out.println("GOODBYE");
                    break;
                }

                String[] parts = request.split(" ");

                if(parts[0].equalsIgnoreCase("REGISTER")){
                    if(parts.length < 4){ out.println("INVALID_REGISTER_FORMAT"); continue; }
                    String name = parts[1], username = parts[2], password = parts[3];
                    out.println(authService.register(name,username,password));
                }

                else if(parts[0].equalsIgnoreCase("LOGIN")){
                    if(parts.length < 3){ out.println("INVALID_LOGIN_FORMAT"); continue; }
                    String username = parts[1], password = parts[2];
                    String result = authService.login(username,password);
                    out.println(result);

                    if(result.equals("LOGIN_SUCCESS")){
                        loggedIn = true;
                        currentUser = username;
                        connectedUsers.add(username);
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

                else if(isAdmin){
                    handleAdminCommands(request,out);
                }

                else if(loggedIn && request.equals("1") && !singlePlayerMenu){
                    singlePlayerMenu = true;
                    out.println("SINGLE PLAYER OPTIONS:");
                    out.println("1) Custom Trivia");
                    out.println("2) Random Trivia");
                }

                else if(loggedIn && singlePlayerMenu && request.equals("1")){
                    singlePlayerMenu = false;
                    out.println("Custom Trivia Mode");
                    startSinglePlayer(out,in);
                }

                else if(loggedIn && singlePlayerMenu && request.equals("2")){
                    singlePlayerMenu = false;
                    startRandomTrivia(out,in);
                }

                else if(loggedIn && request.equals("2")){
                    out.println("MULTIPLAYER OPTIONS:");
                    out.println("JOIN_PUBLIC_ROOM");
                    out.println("START_PUBLIC_GAME");
                }

                else if(loggedIn && request.equalsIgnoreCase("JOIN_PUBLIC_ROOM")){
                    joinPublicRoom(out,in);
                }

                else if(loggedIn && request.equalsIgnoreCase("START_PUBLIC_GAME")){
                    startPublicGame(out);
                }

                else if(loggedIn && request.equals("3")){
                    List<Score> history = scoreService.getUserScores(currentUser);
                    out.println("YOUR SCORE HISTORY:");
                    if(history.isEmpty()) out.println("No previous scores");
                    for(Score s : history) out.println("Score: " + s.getScore());
                }

                else if(loggedIn && request.equals("4")){
                    out.println("GOODBYE"); break;
                }

                // During public game, A-D are answers (single reader: only ClientHandler reads input)
                else if(loggedIn && myPublicRoom != null && myPublicRoom.isStarted()
                        && myPublicRoom.isAcceptingAnswers()
                        && request != null && request.trim().matches("(?i)[A-D]")){
                    String ans = request.trim().toUpperCase();
                    myPublicRoom.submitAnswer(currentUser, ans);
                    out.println("Answer recorded: " + ans);
                    continue;
                }

                else out.println("UNKNOWN_COMMAND");
            }

        }catch(Exception e){
            System.out.println("Client disconnected");
        }finally{
            if(loggedIn && currentUser != null){
                connectedUsers.remove(currentUser);
            }
            try{ if(socket != null && !socket.isClosed()) socket.close(); }catch(IOException ignored){}
        }
    }

    private void joinPublicRoom(PrintWriter out, BufferedReader in){
        if (myPublicRoom != null && !myPublicRoom.isStarted()) {
            out.println("You are already in a public room. Wait for the game to start or disconnect.");
            return;
        }
        if (myPublicRoom != null && myPublicRoom.isStarted()) {
            out.println("You are already in an active game.");
            return;
        }

        GameRoom room = null;
        for(GameRoom r : publicRooms){
            if(!r.isFull() && !r.isStarted()){ room = r; break; }
        }

        if(room == null){
            Team teamA = new Team("Alpha");
            Team teamB = new Team("Beta");
            room = new GameRoom(teamA, teamB, questionService,
                    gameConfig.getMinRoomPlayers(), gameConfig.getMaxRoomPlayers());
            publicRooms.add(room);
        }

        // Balance: add to the smaller team
        if(room.getTeamA().size() <= room.getTeamB().size()){
            room.getTeamA().addPlayer(currentUser, out);
            out.println("Joined Team Alpha in public room (" + room.totalPlayers() + "/" + gameConfig.getMaxRoomPlayers() + " players)");
        } else {
            room.getTeamB().addPlayer(currentUser, out);
            out.println("Joined Team Beta in public room (" + room.totalPlayers() + "/" + gameConfig.getMaxRoomPlayers() + " players)");
        }
        myPublicRoom = room;
    }

    private void startPublicGame(PrintWriter out){
        for(GameRoom room : publicRooms){
            if(room.isReady() && !room.isStarted()){
                new Thread(() -> {
                    try{
                        room.startGame(5);
                    } catch(Exception e){
                        System.err.println("Public game error: " + e.getMessage());
                    }
                }).start();
                out.println("Public Game Started!");
                return;
            }
        }
        out.println("No ready public room. Need at least " + gameConfig.getMinRoomPlayers()
                + " players (at least one on each team). Join with JOIN_PUBLIC_ROOM.");
    }

    private void handleAdminCommands(String request, PrintWriter out){
        switch(request){
            case "1": out.println("Total players connected (logged in): " + connectedUsers.size()); break;
            case "2":
                List<String> topPlayers = getTopPlayers();
                if (topPlayers.isEmpty()) {
                    out.println("No players have completed a single-player game yet.");
                } else {
                    int topScore = gameScores.get(topPlayers.get(0));
                    out.println("Top player(s) with score " + topScore + ":");
                    for(String p : topPlayers) out.println(p);
                }
                break;
            case "3": out.println("Total questions played: " + totalQuestionsPlayed); break;
            case "4": out.println("Highest score ever: " + highestScoreEver); break;
            case "5": out.println("GOODBYE ADMIN"); try{socket.close();}catch(Exception ignored){} break;
            default: out.println("INVALID_ADMIN_COMMAND");
        }
    }

    private List<String> getTopPlayers(){
        List<String> topPlayers = new ArrayList<>();
        int maxScore = 0;
        for(String player : gameScores.keySet()){
            int s = gameScores.get(player);
            if(s > maxScore){ maxScore = s; topPlayers.clear(); topPlayers.add(player);}
            else if(s == maxScore) topPlayers.add(player);
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
        if(score > highestScoreEver) highestScoreEver = score;
        out.println("GAME OVER! Your score = " + score);
        scoreService.addScore(currentUser,score);
    }

    private int askQuestionWithTimer(Question q, PrintWriter out, BufferedReader in) throws IOException{
        totalQuestionsPlayed++;
        AtomicBoolean active = new AtomicBoolean(true);
        out.println("QUESTION: " + q.getText());
        char option = 'A';
        for(String c : q.getChoices()){ out.println(option + ") " + c); option++; }
        out.println("You have 15 seconds!");
        Thread timer = new Thread(() -> {
            try{ Thread.sleep(5000); if(active.get()) out.println("10 seconds left");
                Thread.sleep(5000); if(active.get()) out.println("5 seconds left");
                Thread.sleep(5000); if(active.get()){ out.println("TIME UP!"); active.set(false);} }catch(Exception ignored){}
        });
        timer.start();
        String answer = in.readLine();
        if(!active.get()){ out.println("Answer ignored"); return 0; }
        active.set(false);
        if(answer == null) return 0;
        answer = answer.trim();
        if(!answer.matches("[A-Da-d]")){ out.println("INVALID ANSWER"); return 0; }
        if(answer.equalsIgnoreCase(q.getCorrectAnswer())){ out.println("CORRECT!"); return 10; }
        else{ out.println("WRONG! Correct answer: " + q.getCorrectAnswer()); return 0; }
    }

    private void startSinglePlayer(PrintWriter out, BufferedReader in) throws IOException{
        out.println("Enter category (Math / Science / Geography):");
        String category = normalizeCategory(in.readLine());
        out.println("Enter difficulty (easy / medium / hard):");
        String difficulty = normalizeDifficulty(in.readLine());
        out.println("How many questions?");
        int numQuestions;
        try{ numQuestions = Integer.parseInt(in.readLine());}catch(Exception e){numQuestions = 5;}
        int score = 0;
        for(int i=0;i<numQuestions;i++){
            Question q = questionService.getRandomQuestion(category,difficulty);
            if(q == null){ out.println("No more questions for " + category + " / " + difficulty + ". Try easy/medium/hard."); break;}
            score += askQuestionWithTimer(q,out,in);
        }
        finishGame(score,out);
    }

    private String normalizeCategory(String input){
        if(input == null || input.trim().isEmpty()) return "Math";
        String s = input.trim();
        if(s.equalsIgnoreCase("math")) return "Math";
        if(s.equalsIgnoreCase("science")) return "Science";
        if(s.equalsIgnoreCase("geography")) return "Geography";
        return s;
    }

    private String normalizeDifficulty(String input){
        if(input == null || input.trim().isEmpty()) return "easy";
        String s = input.trim().toLowerCase();
        if(s.equals("easy") || s.equals("medium") || s.equals("hard")) return s;
        return "easy";
    }

    private void startRandomTrivia(PrintWriter out, BufferedReader in) throws IOException{
        out.println("Random Trivia Mode");
        out.println("How many questions?");
        int numQuestions;
        try{ numQuestions = Integer.parseInt(in.readLine());}catch(Exception e){numQuestions = 5;}
        int score = 0;
        for(int i=0;i<numQuestions;i++){
            Question q = questionService.getRandomTriviaQuestion();
            if(q == null){ out.println("No questions available."); break;}
            score += askQuestionWithTimer(q,out,in);
        }
        finishGame(score,out);
    }
}