package server.lookup;

import server.model.Question;
import server.repository.QuestionRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class LookupRequestHandler implements Runnable {

    private final Socket socket;
    private final QuestionRepository repository;
    private final List<Question> questions;
    private final Random random = new Random();

    public LookupRequestHandler(Socket socket, QuestionRepository repository) {
        this.socket = socket;
        this.repository = repository;
        this.questions = repository.findAll();
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {

            String line = in.readLine();
            if (line == null) return;

            String[] parts = line.split("\\|");
            String cmd = parts[0].trim().toUpperCase();

            if (cmd.equals(LookupProtocol.REQ_PING)) {
                System.out.println("[LOOKUP_SERVER] PING from " + socket.getInetAddress());
                out.println(LookupProtocol.RES_PONG);
                return;
            }

            if (cmd.equals(LookupProtocol.REQ_GET_ONE)) {
                String category = parts.length > 1 ? parts[1].trim() : "*";
                String difficulty = parts.length > 2 ? parts[2].trim() : "*";
                System.out.println("[LOOKUP_SERVER] GET_ONE category=" + category + " difficulty=" + difficulty
                        + " from " + socket.getInetAddress());
                Question q = pickOne(category, difficulty);
                if (q == null) {
                    out.println(LookupProtocol.RES_NONE);
                } else {
                    out.println(LookupProtocol.RES_OK);
                    out.println(QuestionCodec.encode(q));
                    out.println(LookupProtocol.RES_END);
                }
                return;
            }

            if (cmd.equals(LookupProtocol.REQ_GET_BATCH)) {
                String category = parts.length > 1 ? parts[1].trim() : "*";
                String difficulty = parts.length > 2 ? parts[2].trim() : "*";
                int count = parts.length > 3 ? Integer.parseInt(parts[3].trim()) : 1;
                System.out.println("[LOOKUP_SERVER] GET_BATCH category=" + category + " difficulty=" + difficulty
                        + " count=" + count + " from " + socket.getInetAddress());
                List<Question> batch = pickBatch(category, difficulty, count);
                if (batch.isEmpty()) {
                    out.println(LookupProtocol.RES_NONE);
                } else {
                    out.println(LookupProtocol.RES_OK);
                    for (Question q : batch) {
                        out.println(QuestionCodec.encode(q));
                    }
                    out.println(LookupProtocol.RES_END);
                }
                return;
            }

            out.println(LookupProtocol.RES_ERR + "|UNKNOWN_COMMAND");
        } catch (Exception e) {
            // ignore per-connection failures
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private Question pickOne(String category, String difficulty) {
        List<Question> filtered = filter(category, difficulty);
        if (filtered.isEmpty()) return null;
        return filtered.get(random.nextInt(filtered.size()));
    }

    private List<Question> pickBatch(String category, String difficulty, int count) {
        List<Question> filtered = new ArrayList<>(filter(category, difficulty));
        if (filtered.isEmpty()) return List.of();

        List<Question> result = new ArrayList<>();
        for (int i = 0; i < count && !filtered.isEmpty(); i++) {
            int idx = random.nextInt(filtered.size());
            result.add(filtered.remove(idx));
        }
        return result;
    }

    private List<Question> filter(String category, String difficulty) {
        final boolean anyCategory = category == null || category.isBlank() || category.equals("*") || category.equalsIgnoreCase("MIXED");
        final boolean anyDifficulty = difficulty == null || difficulty.isBlank() || difficulty.equals("*") || difficulty.equalsIgnoreCase("MIXED");

        return questions.stream()
                .filter(q -> anyCategory || q.getCategory().equalsIgnoreCase(category))
                .filter(q -> anyDifficulty || q.getDifficulty().equalsIgnoreCase(difficulty))
                .collect(Collectors.toList());
    }
}

