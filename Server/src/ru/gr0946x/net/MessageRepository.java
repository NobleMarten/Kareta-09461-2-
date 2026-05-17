package ru.gr0946x.net;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Доступ к таблице messages.
 */
public class MessageRepository {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final JdbcTemplate db;

    private static final RowMapper<Message> MSG_MAPPER = (rs, n) -> {
        int receiverId = rs.getInt("receiver_id");
        Integer recv = rs.wasNull() ? null : receiverId;
        return new Message(
                rs.getInt("id"),
                rs.getInt("sender_id"),
                recv,
                rs.getString("content"),
                rs.getString("sent_at")
        );
    };

    public MessageRepository() {
        this.db = DatabaseConfig.getJdbcTemplate();
    }

    /**
     * Сохраняет сообщение и возвращает сгенерированный id.
     * receiverId == null означает broadcast.
     */
    public int save(int senderId, Integer receiverId, String content) {
        var keyHolder = new GeneratedKeyHolder();
        db.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO messages (sender_id, receiver_id, content, sent_at) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setInt(1, senderId);
            if (receiverId == null) ps.setNull(2, Types.INTEGER);
            else ps.setInt(2, receiverId);
            ps.setString(3, content);
            ps.setString(4, LocalDateTime.now().format(FMT));
            return ps;
        }, keyHolder);
        var key = keyHolder.getKey();
        return key != null ? key.intValue() : -1;
    }

    /** Помечает сообщение как доставленное получателю. */
    public void markAsReceived(int messageId) {
        db.update("UPDATE messages SET is_received = 1 WHERE id = ?", messageId);
    }

    /**
     * Последние 20 сообщений личного чата между двумя пользователями.
     * Возвращает сообщения в хронологическом порядке (от старых к новым).
     */
    public List<Message> getLast20(int userId1, int userId2) {
        return db.query("""
                SELECT * FROM (
                    SELECT id, sender_id, receiver_id, content, sent_at FROM messages
                    WHERE (sender_id = ? AND receiver_id = ?)
                       OR (sender_id = ? AND receiver_id = ?)
                    ORDER BY id DESC
                    LIMIT 20
                ) ORDER BY id ASC
                """,
                MSG_MAPPER,
                userId1, userId2,
                userId2, userId1
        );
    }

    /**
     * Последние 20 широковещательных сообщений (receiver_id IS NULL).
     */
    public List<Message> getLast20Broadcast() {
        return db.query("""
                SELECT * FROM (
                    SELECT id, sender_id, receiver_id, content, sent_at FROM messages
                    WHERE receiver_id IS NULL
                    ORDER BY id DESC
                    LIMIT 20
                ) ORDER BY id ASC
                """,
                MSG_MAPPER
        );
    }

    /**
     * Поиск по тексту в личной переписке между двумя пользователями.
     */
    public List<Message> searchInChat(int userId1, int userId2, String keyword) {
        var like = "%" + keyword + "%";
        return db.query("""
                SELECT id, sender_id, receiver_id, content, sent_at FROM messages
                WHERE ((sender_id = ? AND receiver_id = ?)
                    OR (sender_id = ? AND receiver_id = ?))
                  AND content LIKE ?
                ORDER BY id ASC
                """,
                MSG_MAPPER,
                userId1, userId2,
                userId2, userId1,
                like
        );
    }

    /**
     * Поиск по тексту в широковещательном чате.
     */
    public List<Message> searchInBroadcast(String keyword) {
        var like = "%" + keyword + "%";
        return db.query("""
                SELECT id, sender_id, receiver_id, content, sent_at FROM messages
                WHERE receiver_id IS NULL AND content LIKE ?
                ORDER BY id ASC
                """,
                MSG_MAPPER, like
        );
    }
}
