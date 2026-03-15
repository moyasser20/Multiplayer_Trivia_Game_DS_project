package server.handler;

import server.model.Question;
import server.service.AuthService;
import server.service.QuestionService;
import server.service.ScoreService;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private Socket socket;
    private AuthService authService;
    private QuestionService questionService;
    private ScoreService scoreService;

    private boolean loggedIn = false;
    private String currentUser;

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

                if(parts[0].equalsIgnoreCase("REGISTER")){

                    if(parts.length < 4){
                        out.println("INVALID_REGISTER_FORMAT");
                        continue;
                    }

                    String name = parts[1];
                    String username = parts[2];
                    String password = parts[3];

                    String result = authService.register(name, username, password);
                    out.println(result);
                }

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
                        out.println("2) Multiplayer (Coming Later)");
                        out.println("3) Quit");
                    }
                }

                else if(loggedIn && parts[0].equals("1")){

                    out.println("Single Player Mode Activated");
                    startSinglePlayer(out, in);
                }

                else if(loggedIn && parts[0].equals("2")){

                    out.println("Multiplayer Mode will be implemented later.");
                }

                else if(loggedIn && parts[0].equals("3")){

                    out.println("GOODBYE");
                    break;
                }

                else if(loggedIn && parts[0].equalsIgnoreCase("GET_QUESTION")){

                    if(parts.length < 3){
                        out.println("INVALID_QUESTION_FORMAT");
                        continue;
                    }

                    String category = parts[1];
                    String difficulty = parts[2];

                    sendQuestion(out, category, difficulty, in);
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

            score += askQuestion(q, out, in);
        }

        out.println("GAME OVER! Your score = " + score);

        scoreService.addScore(currentUser, score);
    }


    private int askQuestion(Question q, PrintWriter out, BufferedReader in) throws IOException {

        out.println("QUESTION: " + q.getText());

        char option = 'A';

        for(String choice : q.getChoices()){
            out.println(option + ") " + choice);
            option++;
        }

        out.println("END_QUESTION");

        out.println("Enter your answer (A/B/C/D):");

        String answer = in.readLine();

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


    private void sendQuestion(PrintWriter out,
                              String category,
                              String difficulty,
                              BufferedReader in) throws IOException {

        Question q = questionService.getRandomQuestion(category, difficulty);

        if(q == null){
            out.println("NO_QUESTION_FOUND");
            return;
        }

        out.println("QUESTION: " + q.getText());

        char option = 'A';

        for(String choice : q.getChoices()){
            out.println(option + ") " + choice);
            option++;
        }

        out.println("END_QUESTION");

        out.println("Enter your answer (A/B/C/D):");

        String answer = in.readLine();

        if(answer == null) return;

        answer = answer.trim();

        if(answer.equalsIgnoreCase(q.getCorrectAnswer())){

            out.println("CORRECT!");

            scoreService.addScore(currentUser, 10);

        }else{

            out.println("WRONG! Correct answer: " + q.getCorrectAnswer());
        }
    }
}
