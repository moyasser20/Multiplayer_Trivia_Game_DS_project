package server.game;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class Team {

    private String teamName;
    private List<String> players = new ArrayList<>();
    private List<PrintWriter> outputs = new ArrayList<>();
    private int score = 0;

    public Team(String teamName) {
        this.teamName = teamName;
    }

    public void addPlayer(String username, PrintWriter out){
        players.add(username);
        outputs.add(out);
    }

    public String getTeamName() {
        return teamName;
    }

    public List<PrintWriter> getOutputs() {
        return outputs;
    }

    public void addScore(int points){
        score += points;
    }

    public int getScore(){
        return score;
    }

    public int size(){
        return players.size();
    }
}