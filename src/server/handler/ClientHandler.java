package server.handler;

import server.model.Question;
import server.service.AuthService;
import server.service.QuestionService;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private Socket socket;
    private AuthService authService;
    private QuestionService questionService;

    public ClientHandler(Socket socket, AuthService authService, QuestionService questionService) {
        this.socket = socket;
        this.authService = authService;
        this.questionService = questionService;
    }

    @Override
    public void run() {

        try {

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            out.println("WELCOME TO TRIVIA SERVER");

            String request;

            while ((request = in.readLine()) != null) {

                if(request.equals("-")) {
                    out.println("GOODBYE");
                    break;
                }

                String[] parts = request.split(" ");

                if (parts[0].equalsIgnoreCase("REGISTER")) {

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

                else if (parts[0].equalsIgnoreCase("LOGIN")) {

                    if(parts.length < 3){
                        out.println("INVALID_LOGIN_FORMAT");
                        continue;
                    }

                    String username = parts[1];
                    String password = parts[2];

                    String result = authService.login(username, password);

                    out.println(result);
                }

                else if (parts[0].equalsIgnoreCase("GET_QUESTION")) {

                    if(parts.length < 3){
                        out.println("INVALID_QUESTION_FORMAT");
                        continue;
                    }

                    String category = parts[1];
                    String difficulty = parts[2];

                    Question q = questionService.getRandomQuestion(category, difficulty);

                    if(q == null){
                        out.println("NO_QUESTION_FOUND");
                    }
                    else{

                        out.println("QUESTION: " + q.getText());

                        char option = 'A';

                        for(String choice : q.getChoices()){
                            out.println(option + ") " + choice);
                            option++;
                        }

                        out.println("END_QUESTION");
                    }
                }

                else if (parts[0].equalsIgnoreCase("QUIT")) {

                    out.println("DISCONNECTED");
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

}
