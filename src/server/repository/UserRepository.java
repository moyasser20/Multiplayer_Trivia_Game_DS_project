package server.repository;

import server.model.User;

public interface UserRepository {

    User findByUsername(String username);

    void save(User user);

}