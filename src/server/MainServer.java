package server;

import server.handler.ClientHandler;
import server.repository.UserRepository;
import server.repositoryImpl.FileUserRepository;
import server.service.AuthService;

import java.net.ServerSocket;
import java.net.Socket;

public class MainServer {

    public static void main(String[] args) throws Exception {

        ServerSocket serverSocket = new ServerSocket(5000);

        UserRepository repo = new FileUserRepository("src/data/users.txt");

        AuthService authService = new AuthService(repo);

        System.out.println("Server started...");

        while (true) {

            Socket client = serverSocket.accept();

            ClientHandler handler = new ClientHandler(client, authService);

            new Thread(handler).start();

        }

    }

}