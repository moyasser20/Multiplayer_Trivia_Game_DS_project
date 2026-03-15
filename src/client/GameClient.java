package client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class GameClient {

    public static void main(String[] args) throws Exception {

        Socket socket = new Socket("localhost", 5000);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));

        PrintWriter out = new PrintWriter(
                socket.getOutputStream(), true);

        Scanner scanner = new Scanner(System.in);

        Thread serverListener = new Thread(() -> {
            try {
                String response;
                while ((response = in.readLine()) != null) {
                    System.out.println("SERVER: " + response);
                }
            } catch (Exception e) {
                System.out.println("Disconnected from server");
            }
        });

        serverListener.start();

        while (true) {

            String input = scanner.nextLine();

            out.println(input);

            if(input.equals("-") || input.equalsIgnoreCase("quit")){
                break;
            }
        }

        socket.close();
    }
}
