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

    public enum Status {
        OK,
        NONE,      // server reachable but no matches
        OFFLINE,   // connection failed
        ERROR      // protocol/parse error
    }

    public static final class Result {
        public final Status status;
        public final List<Question> questions;

        public Result(Status status, List<Question> questions) {
            this.status = status;
            this.questions = questions == null ? List.of() : questions;
        }
    }

    public LookupClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public Question getOne(String category, String difficulty) {
        List<Question> batch = getBatch(category, difficulty, 1);
        return batch.isEmpty() ? null : batch.get(0);
    }

    public List<Question> getBatch(String category, String difficulty, int count) {
        Result res = getBatchResult(category, difficulty, count);
        return res.questions;
    }

    public Result getBatchResult(String category, String difficulty, int count) {
        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {

            out.println(LookupProtocol.REQ_GET_BATCH + "|" + safe(category) + "|" + safe(difficulty) + "|" + count);

            String first = in.readLine();
            if (first == null) return new Result(Status.ERROR, List.of());
            if (first.startsWith(LookupProtocol.RES_NONE)) return new Result(Status.NONE, List.of());
            if (!first.startsWith(LookupProtocol.RES_OK)) return new Result(Status.ERROR, List.of());

            List<Question> result = new ArrayList<>();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals(LookupProtocol.RES_END)) break;
                Question q = QuestionCodec.decode(line);
                if (q != null) result.add(q);
            }
            return new Result(result.isEmpty() ? Status.NONE : Status.OK, result);
        } catch (Exception e) {
            return new Result(Status.OFFLINE, List.of());
        }
    }

    private static String safe(String s) {
        if (s == null || s.isBlank()) return "*";
        return s.trim();
    }
}

