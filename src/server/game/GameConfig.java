package server.game;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Loads game config from file (e.g. min/max room players).
 * Used at server runtime; values are read once.
 */
public class GameConfig {

    private final int minRoomPlayers;
    private final int maxRoomPlayers;

    public GameConfig(String configPath) {
        int min = 2;
        int max = 4;
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
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Could not load config from " + configPath + ", using defaults: " + e.getMessage());
        }
        this.minRoomPlayers = Math.max(1, min);
        this.maxRoomPlayers = Math.max(minRoomPlayers, max);
    }

    public int getMinRoomPlayers() {
        return minRoomPlayers;
    }

    public int getMaxRoomPlayers() {
        return maxRoomPlayers;
    }
}
