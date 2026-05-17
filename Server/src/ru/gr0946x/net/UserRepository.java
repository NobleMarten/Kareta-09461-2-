package ru.gr0946x.net;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;

/**
 * Доступ к таблице users.
 */
public class UserRepository {

    private final JdbcTemplate db;

    private static final RowMapper<User> USER_MAPPER = (rs, n) -> new User(
            rs.getInt("id"),
            rs.getString("username"),
            rs.getString("password")
    );

    public UserRepository() {
        this.db = DatabaseConfig.getJdbcTemplate();
    }

    /** Добавляет нового пользователя. username хранится в нижнем регистре. */
    public void register(String username, String passwordHash) {
        db.update(
                "INSERT INTO users (username, password) VALUES (?, ?)",
                username.toLowerCase(), passwordHash
        );
    }

    /** Возвращает пользователя по имени (регистронезависимо), или empty если не найден. */
    public Optional<User> findByUsername(String username) {
        var list = db.query(
                "SELECT id, username, password FROM users WHERE username = ?",
                USER_MAPPER,
                username.toLowerCase()
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** Возвращает пользователя по id, или empty если не найден. */
    public Optional<User> findById(int id) {
        var list = db.query(
                "SELECT id, username, password FROM users WHERE id = ?",
                USER_MAPPER, id
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** Возвращает список всех имён пользователей. */
    public List<String> getAllUsernames() {
        return db.queryForList("SELECT username FROM users", String.class);
    }
}
