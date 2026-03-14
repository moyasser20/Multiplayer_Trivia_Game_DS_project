package server.handler;

import server.service.AuthService;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private Socket socket;
    private AuthService authService;

    public ClientHandler(Socket socket, AuthService authService) {
        this.socket = socket;
        this.authService = authService;
    }

    @Override
    public void run() {

        try {

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            out.println("WELCOME");

            String request;

            while ((request = in.readLine()) != null) {

                String[] parts = request.split(" ");

                if (parts[0].equals("REGISTER")) {

                    String name = parts[1];
                    String username = parts[2];
                    String password = parts[3];

                    String result = authService.register(name, username, password);

                    out.println(result);

                }

                else if (parts[0].equals("LOGIN")) {

                    String username = parts[1];
                    String password = parts[2];

                    String result = authService.login(username, password);

                    out.println(result);

                }

            }

        } catch (Exception e) {
            System.out.println("Client disconnected");
        }

    }

}