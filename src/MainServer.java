import server.handler.ClientHandler;
import server.repository.ScoreRepository;
import server.repository.UserRepository;
import server.repository.QuestionRepository;
import server.repositoryImpl.FileScoreRepository;
import server.repositoryImpl.FileUserRepository;
import server.repositoryImpl.FileQuestionRepository;
import server.service.AuthService;
import server.service.QuestionService;
import server.service.ScoreService;
import server.game.GameConfig;

import java.net.ServerSocket;
import java.net.Socket;

public class MainServer {

    public static void main(String[] args) throws Exception {

        GameConfig config = new GameConfig("src/data/config.txt");

        ServerSocket serverSocket = new ServerSocket(config.getGamePort());

        UserRepository userRepo = new FileUserRepository("src/data/users.txt");
        // In lookup_only mode, the game server should not read questions locally.
        QuestionRepository questionRepo = config.isLookupOnly() ? null : new FileQuestionRepository("src/data/questions.txt");
        ScoreRepository scoreRepo = new FileScoreRepository("src/data/scores.txt");

        AuthService authService = new AuthService(userRepo);
        QuestionService questionService = new QuestionService(
                questionRepo,
                config.getLookupHost(),
                config.getLookupPort(),
                config.isLookupOnly()
        );
        ScoreService scoreService = new ScoreService(scoreRepo);

        System.out.println("Trivia Server started on port " + config.getGamePort() + "...");
        System.out.println("Lookup server: " + config.getLookupHost() + ":" + config.getLookupPort()
                + " (lookup_only=" + config.isLookupOnly() + ")");

        while (true) {

            Socket client = serverSocket.accept();

            System.out.println("New client connected: " + client.getInetAddress());

            ClientHandler handler = new ClientHandler(
                    client,
                    authService,
                    questionService,
                    scoreService
            );

            new Thread(handler).start();
        }
    }
}
