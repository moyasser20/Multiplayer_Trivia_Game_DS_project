package server.repositoryImpl;

import server.model.User;
import server.repository.UserRepository;

import java.io.*;
import java.util.*;

public class FileUserRepository implements UserRepository {

    private File file;

    public FileUserRepository(String filePath) {
        this.file = new File(filePath);
    }

    @Override
    public User findByUsername(String username) {

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {

            String line;

            while ((line = reader.readLine()) != null) {

                String[] parts = line.split(",");

                if (parts[1].equals(username)) {
                    return new User(parts[0], parts[1], parts[2]);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public void save(User user) {

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {

            writer.write(user.getName() + "," + user.getUsername() + "," + user.getPassword());
            writer.newLine();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}