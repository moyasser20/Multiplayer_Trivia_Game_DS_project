package server.game;
import java.nio.file.*;
import java.util.*;

public class GameConfig {

    private final int minRoomPlayers;
    private final int maxRoomPlayers;
    private final String lookupHost;
    private final int lookupPort;
    private final int gamePort;

    public GameConfig(String configPath) {
        int min = 2;
        int max = 4;
        String lHost = "localhost";
        int lPort = 6000;
        int gPort = 5000;
        try {
            Path path = Paths.get(configPath);
            if (Files.exists(path)) {
                List<String> lines = Files.readAllLines(path);
                for (String line : lines) {
                    line = line.trim();
                    if (line.startsWith("#") || line.isEmpty()) continue;
                    String[] parts = line.split("=", 2);
                    if (parts.length != 2) continue;
                    String key = parts[0].trim().toLowerCase();
                    String value = parts[1].trim();
                    if (key.equals("min_room_players")) {
                        min = Integer.parseInt(value);
                    } else if (key.equals("max_room_players")) {
                        max = Integer.parseInt(value);
                    } else if (key.equals("lookup_host")) {
                        lHost = value;
                    } else if (key.equals("lookup_port")) {
                        lPort = Integer.parseInt(value);
                    } else if (key.equals("game_port")) {
                        gPort = Integer.parseInt(value);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Could not load config from " + configPath + ", using defaults: " + e.getMessage());
        }
        this.minRoomPlayers = Math.max(1, min);
        this.maxRoomPlayers = Math.max(minRoomPlayers, max);
        this.lookupHost = lHost == null || lHost.isBlank() ? "localhost" : lHost;
        this.lookupPort = lPort <= 0 ? 6000 : lPort;
        this.gamePort = gPort <= 0 ? 5000 : gPort;
    }

    public int getMinRoomPlayers() {
        return minRoomPlayers;
    }

    public int getMaxRoomPlayers() {
        return maxRoomPlayers;
    }

    public String getLookupHost() {
        return lookupHost;
    }

    public int getLookupPort() {
        return lookupPort;
    }

    public int getGamePort() {
        return gamePort;
    }
}
