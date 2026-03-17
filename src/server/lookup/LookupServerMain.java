package server.lookup;

import server.repository.QuestionRepository;
import server.repositoryImpl.FileQuestionRepository;

import java.net.ServerSocket;
import java.net.Socket;


public class LookupServerMain {

    public static void main(String[] args) throws Exception {
        int port = 6000;
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }

        QuestionRepository repo = new FileQuestionRepository("src/data/questions.txt");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Lookup Server started on port " + port + "...");

            while (true) {
                Socket client = serverSocket.accept();
                new Thread(new LookupRequestHandler(client, repo)).start();
            }
        }
    }
}

