package server.lookup;

import server.model.Question;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LookupClient {

    private final String host;
    private final int port;

    public LookupClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public Question getOne(String category, String difficulty) {
        List<Question> batch = getBatch(category, difficulty, 1);
        return batch.isEmpty() ? null : batch.get(0);
    }

    public List<Question> getBatch(String category, String difficulty, int count) {
        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {

            out.println(LookupProtocol.REQ_GET_BATCH + "|" + safe(category) + "|" + safe(difficulty) + "|" + count);

            String first = in.readLine();
            if (first == null) return List.of();
            if (first.startsWith(LookupProtocol.RES_NONE)) return List.of();
            if (!first.startsWith(LookupProtocol.RES_OK)) return List.of();

            List<Question> result = new ArrayList<>();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals(LookupProtocol.RES_END)) break;
                Question q = QuestionCodec.decode(line);
                if (q != null) result.add(q);
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String safe(String s) {
        if (s == null || s.isBlank()) return "*";
        return s.trim();
    }
}

