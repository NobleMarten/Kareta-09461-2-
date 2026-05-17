package ru.gr0946x.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

/**
 * Инициализация SQLite-базы и предоставление JdbcTemplate для репозиториев.
 */
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);
    private static JdbcTemplate jdbcTemplate;

    public static synchronized JdbcTemplate getJdbcTemplate() {
        if (jdbcTemplate == null) {
            init();
        }
        return jdbcTemplate;
    }

    private static void init() {
        var ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:kareta.db");
        jdbcTemplate = new JdbcTemplate(ds);
        createTables();
        log.info("База данных инициализирована");
    }

    private static void createTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    password TEXT NOT NULL
                )""");

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    sender_id   INTEGER NOT NULL,
                    receiver_id INTEGER,
                    content     TEXT    NOT NULL,
                    sent_at     TEXT    NOT NULL,
                    is_received INTEGER NOT NULL DEFAULT 0
                )""");
    }
}
