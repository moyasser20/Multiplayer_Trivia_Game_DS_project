package client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class GameClient {

    public static void main(String[] args) throws Exception {

        Socket socket = new Socket("localhost", 5000);

        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        Scanner scanner = new Scanner(System.in);

        System.out.println(in.readLine());

        while (true) {

            String input = scanner.nextLine();

            out.println(input);

            String response = in.readLine();

            System.out.println("SERVER: " + response);

        }

    }

}