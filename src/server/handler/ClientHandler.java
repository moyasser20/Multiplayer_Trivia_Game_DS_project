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

    // aggregated stats
    private static int totalQuestionsPlayed = 0;          // single + multiplayer
    private static int singleQuestionsPlayed = 0;
    private static int multiQuestionsPlayed = 0;
    private static int highestScoreEver = 0;              // max of single/multi
    private static int singleHighestScoreEver = 0;
    private static int multiHighestScoreEver = 0;
    private static Map<String,Integer> gameScores = new ConcurrentHashMap<>();
    // total wins per user (single or multiplayer)
    private static Map<String,Integer> wins = new ConcurrentHashMap<>();
    /** Logged-in users (connected). Added on LOGIN_SUCCESS, removed on disconnect. */
    private static Set<String> connectedUsers = ConcurrentHashMap.newKeySet();
    /** Active outputs per username (for team games). */
    private static Map<String, PrintWriter> activeOutputs = new ConcurrentHashMap<>();
    /** Active game room per username (public or team-based). */
    private static Map<String, GameRoom> userRooms = new ConcurrentHashMap<>();

    // Named teams (team-based multiplayer with known users)
    private static class NamedTeam {
        String name;
        String category;
        String difficulty;
        int numQuestions;
        List<String> players = new ArrayList<>();
        NamedTeam(String name, String category, String difficulty, int numQuestions){
            this.name = name;
            this.category = category;
            this.difficulty = difficulty;
            this.numQuestions = numQuestions;
        }
    }

    private static Map<String,NamedTeam> namedTeams = new ConcurrentHashMap<>();

    // Public rooms (min/max + lookup config loaded from config)
    private static final GameConfig gameConfig = new GameConfig("src/data/config.txt");
    private static List<GameRoom> publicRooms = new ArrayList<>();

    // Currently active game room for this client (public or team-based)
    private GameRoom currentRoom = null;

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
                        if (out != null) {
                            activeOutputs.put(username, out);
                        }
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
                    out.println("TEAM_MODE            (play with specific teams)");
                    out.println("JOIN_PUBLIC_ROOM     (public room, random teams)");
                    out.println("START_PUBLIC_GAME <numQuestions>");
                }

                else if(loggedIn && request.equalsIgnoreCase("TEAM_MODE")){
                    out.println("TEAM MULTIPLAYER OPTIONS:");
                    out.println("CREATE_TEAM <teamName> <category> <difficulty> <numQuestions>");
                    out.println("JOIN_TEAM <teamName>");
                    out.println("START_TEAM_GAME <teamA> <teamB>");
                }

                else if(loggedIn && parts[0].equalsIgnoreCase("CREATE_TEAM")){
                    handleCreateTeam(parts, out);
                }

                else if(loggedIn && parts[0].equalsIgnoreCase("JOIN_TEAM")){
                    if (parts.length < 2) { out.println("USAGE: JOIN_TEAM <teamName>"); continue; }
                    handleJoinTeam(parts[1], out);
                }

                else if(loggedIn && parts[0].equalsIgnoreCase("START_TEAM_GAME")){
                    if (parts.length < 3) { out.println("USAGE: START_TEAM_GAME <teamA> <teamB>"); continue; }
                    handleStartTeamGame(parts[1], parts[2], out);
                }

                else if(loggedIn && request.equalsIgnoreCase("JOIN_PUBLIC_ROOM")){
                    joinPublicRoom(out,in);
                }

                else if(loggedIn && request.toUpperCase().startsWith("START_PUBLIC_GAME")){
                    String[] p = request.split(" ");
                    int numQuestions = 5;
                    if (p.length >= 2) {
                        try { numQuestions = Integer.parseInt(p[1]); } catch(Exception ignored){}
                    }
                    startPublicGame(out, numQuestions);
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

                // During any active game, A-D are answers (single reader: only ClientHandler reads input)
                else if(loggedIn
                        && (currentRoom != null || userRooms.getOrDefault(currentUser, null) != null)){
                    if (currentRoom == null) {
                        currentRoom = userRooms.get(currentUser);
                    }
                    if (currentRoom != null && currentRoom.isStarted()
                            && currentRoom.isAcceptingAnswers()
                            && request != null && request.trim().matches("(?i)[A-D]")){
                        String ans = request.trim().toUpperCase();
                        currentRoom.submitAnswer(currentUser, ans);
                        out.println("Answer recorded: " + ans);
                        continue;
                    }
                }

                else out.println("UNKNOWN_COMMAND");
            }

        }catch(Exception e){
            System.out.println("Client disconnected");
        }finally{
            if(loggedIn && currentUser != null){
                connectedUsers.remove(currentUser);
                activeOutputs.remove(currentUser);
                userRooms.remove(currentUser);
                // if user was in a game room, remove from team player lists
                if (currentRoom != null) {
                    currentRoom.getTeamA().getPlayers().remove(currentUser);
                    currentRoom.getTeamB().getPlayers().remove(currentUser);
                }
            }
            try{ if(socket != null && !socket.isClosed()) socket.close(); }catch(IOException ignored){}
        }
    }

    private void joinPublicRoom(PrintWriter out, BufferedReader in){
        if (currentRoom != null && !currentRoom.isStarted()) {
            out.println("You are already in a public room. Wait for the game to start or disconnect.");
            return;
        }
        if (currentRoom != null && currentRoom.isStarted()) {
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
        currentRoom = room;
        userRooms.put(currentUser, room);
    }

    private void startPublicGame(PrintWriter out, int numQuestions){
        for(GameRoom room : publicRooms){
            if(room.isReady() && !room.isStarted()){
                final int qCount = numQuestions;
                new Thread(() -> {
                    try{
                        // public rooms use mixed category/difficulty
                        room.startGame("*", "*", qCount);
                    } catch(Exception e){
                        System.err.println("Public game error: " + e.getMessage());
                    }
                }).start();
                out.println("Public Game Started with " + numQuestions + " questions!");
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
                if (wins.isEmpty()) {
                    out.println("No wins recorded yet.");
                } else {
                    int maxWins = 0;
                    for (int w : wins.values()) {
                        if (w > maxWins) maxWins = w;
                    }
                    out.println("Player(s) with most wins (" + maxWins + "):");
                    for (Map.Entry<String,Integer> e : wins.entrySet()) {
                        if (e.getValue() == maxWins) {
                            out.println(e.getKey());
                        }
                    }
                }
                break;
            case "3":
                out.println("Total questions played (all): " + totalQuestionsPlayed);
                out.println("  Single-player questions:    " + singleQuestionsPlayed);
                out.println("  Multiplayer questions:      " + multiQuestionsPlayed);
                break;
            case "4":
                out.println("Highest score ever (all): " + highestScoreEver);
                out.println("  Single-player highest:  " + singleHighestScoreEver);
                out.println("  Multiplayer highest:    " + multiHighestScoreEver);

                // players with highest score ever (single or multi)
                if (gameScores.isEmpty()) {
                    out.println("No players have recorded scores yet.");
                } else {
                    List<String> topPlayers = new ArrayList<>();
                    int maxScore = highestScoreEver;
                    for (Map.Entry<String,Integer> e : gameScores.entrySet()) {
                        if (e.getValue() == maxScore) {
                            topPlayers.add(e.getKey());
                        }
                    }
                    out.println("Player(s) with highest score " + maxScore + ":");
                    for (String p : topPlayers) {
                        out.println("  " + p);
                    }
                }
                break;
            case "5": out.println("GOODBYE ADMIN"); try{socket.close();}catch(Exception ignored){} break;
            default: out.println("INVALID_ADMIN_COMMAND");
        }
    }

    // register a win for a given user (used from multiplayer rooms)
    public static void registerWin(String username){
        if (username == null) return;
        wins.merge(username, 1, Integer::sum);
    }

    // record a multiplayer question being played (used from GameRoom)
    public static void incrementMultiQuestionPlayed(){
        multiQuestionsPlayed++;
        totalQuestionsPlayed++;
    }

    // update multiplayer highest score and global highest score (used from GameRoom)
    public static void updateMultiHighestScore(int score){
        if (score > multiHighestScoreEver) multiHighestScoreEver = score;
        if (score > highestScoreEver) highestScoreEver = score;
    }

    // ---------- TEAM MULTIPLAYER (named teams) ----------

    private void handleCreateTeam(String[] parts, PrintWriter out){
        if (parts.length < 5){
            out.println("USAGE: CREATE_TEAM <teamName> <category> <difficulty> <numQuestions>");
            return;
        }
        String teamName = parts[1];
        String category = normalizeCategory(parts[2]);
        String difficulty = normalizeDifficulty(parts[3]);
        int numQ;
        try{ numQ = Integer.parseInt(parts[4]); }catch(Exception e){ numQ = 5; }

        if (namedTeams.containsKey(teamName)){
            out.println("TEAM_NAME_ALREADY_USED");
            return;
        }

        NamedTeam t = new NamedTeam(teamName, category, difficulty, numQ);
        t.players.add(currentUser);
        namedTeams.put(teamName, t);
        out.println("TEAM_CREATED " + teamName + " (" + category + " / " + difficulty + ", " + numQ + " questions)");
    }

    private void handleJoinTeam(String teamName, PrintWriter out){
        NamedTeam t = namedTeams.get(teamName);
        if (t == null){
            out.println("TEAM_NOT_FOUND");
            return;
        }
        if (!t.players.contains(currentUser)){
            t.players.add(currentUser);
        }
        out.println("JOINED_TEAM " + teamName);
    }

    private void handleStartTeamGame(String teamAName, String teamBName, PrintWriter out){
        NamedTeam A = namedTeams.get(teamAName);
        NamedTeam B = namedTeams.get(teamBName);
        if (A == null || B == null){
            out.println("TEAM_NOT_FOUND");
            return;
        }
        if (A.players.isEmpty() || B.players.isEmpty()){
            out.println("Both teams must have players");
            return;
        }
        if (A.players.size() != B.players.size()){
            out.println("Teams must have equal number of players");
            return;
        }

        // Build runtime teams using active outputs (only online players can participate)
        Team teamA = new Team(A.name);
        Team teamB = new Team(B.name);

        for (String u : A.players){
            PrintWriter pw = activeOutputs.get(u);
            if (pw == null){
                out.println("PLAYER_NOT_ONLINE: " + u);
                return;
            }
            teamA.addPlayer(u, pw);
        }

        for (String u : B.players){
            PrintWriter pw = activeOutputs.get(u);
            if (pw == null){
                out.println("PLAYER_NOT_ONLINE: " + u);
                return;
            }
            teamB.addPlayer(u, pw);
        }

        GameRoom room = new GameRoom(teamA, teamB, questionService,
                gameConfig.getMinRoomPlayers(), gameConfig.getMaxRoomPlayers());

        // Mark all users in this room so A-D answers are routed correctly
        for (String u : A.players) {
            userRooms.put(u, room);
        }
        for (String u : B.players) {
            userRooms.put(u, room);
        }

        new Thread(() -> {
            try{
                room.startGame(A.category, A.difficulty, A.numQuestions);
            }catch(Exception e){
                System.err.println("Team game error: " + e.getMessage());
            }
        }).start();

        out.println("TEAM_GAME_STARTED between " + teamAName + " and " + teamBName +
                " (" + A.category + " / " + A.difficulty + ", " + A.numQuestions + " questions)");
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
        if(score > singleHighestScoreEver) singleHighestScoreEver = score;
        if(score > highestScoreEver) highestScoreEver = score;
        out.println("GAME OVER! Your score = " + score);
        scoreService.addScore(currentUser,score);
        // count a win for any finished single-player game
        wins.merge(currentUser, 1, Integer::sum);
    }

    private int askQuestionWithTimer(Question q, PrintWriter out, BufferedReader in) throws IOException{
        // single-player question
        singleQuestionsPlayed++;
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