package server.service;

import server.model.User;
import server.repository.UserRepository;

public class AuthService {

    private UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String register(String name, String username, String password) {

        User existing = userRepository.findByUsername(username);

        if (existing != null) {
            return "ERROR_USERNAME_EXISTS";
        }

        User user = new User(name, username, password);
        userRepository.save(user);

        return "REGISTER_SUCCESS";
    }

    public String login(String username, String password) {

        User user = userRepository.findByUsername(username);

        if (user == null) {
            return "404 NOT_FOUND";
        }

        if (!user.getPassword().equals(password)) {
            return "401 UNAUTHORIZED";
        }

        return "LOGIN_SUCCESS";
    }

}