package server.repositoryImpl;

import server.model.Score;
import server.repository.ScoreRepository;

import java.io.*;
import java.util.*;

public class FileScoreRepository implements ScoreRepository {

    private String filePath;
    private List<Score> scores = new ArrayList<>();

    public FileScoreRepository(String filePath) {
        this.filePath = filePath;
        loadScores();
    }

    private void loadScores(){

        try(BufferedReader br = new BufferedReader(new FileReader(filePath))){

            String line;

            while((line = br.readLine()) != null){

                String[] parts = line.split(",");

                scores.add(new Score(parts[0], Integer.parseInt(parts[1])));
            }

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    // get all scores
    public List<Score> getScores(){
        return scores;
    }

    // get scores for specific user (THIS IS NEW)
    public List<Score> getScoresByUser(String username){

        List<Score> userScores = new ArrayList<>();

        for(Score s : scores){

            if(s.getUsername().equals(username)){
                userScores.add(s);
            }
        }

        return userScores;
    }

    public void saveScore(Score score){

        scores.add(score);

        try(PrintWriter pw = new PrintWriter(new FileWriter(filePath,true))){

            pw.println(score.getUsername()+","+score.getScore());

        }catch(Exception e){
            e.printStackTrace();
        }
    }
}