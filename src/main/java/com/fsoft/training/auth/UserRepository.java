package com.fsoft.training.auth;

import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    public record UserRow(long id, String username, String passwordHash) {
    }

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRow> findByUsername(String username) {
        return jdbc.sql("select id, username, password_hash from app.users where username = :username")
                .param("username", username)
                .query((rs, n) -> new UserRow(rs.getLong("id"), rs.getString("username"),
                        rs.getString("password_hash")))
                .optional();
    }

    /** @throws UsernameTakenException when the username is taken */
    public long create(String username, String passwordHash) {
        try {
            return jdbc.sql("insert into app.users(username, password_hash) values (:username, :hash) returning id")
                    .param("username", username)
                    .param("hash", passwordHash)
                    .query(Long.class)
                    .single();
        } catch (DuplicateKeyException e) {
            throw new UsernameTakenException(username);
        }
    }
}
