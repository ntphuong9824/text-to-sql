package com.fsoft.training.conversation;

import com.fsoft.training.conversation.ConversationDtos.ConversationSummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ConversationRepository {

    public record Row(UUID id, String title) {
    }

    private final JdbcClient jdbc;

    public ConversationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void create(UUID id, long userId, String title) {
        jdbc.sql("insert into app.conversations(id, user_id, title) values (:id, :user, :title)")
                .param("id", id).param("user", userId).param("title", title).update();
    }

    public Optional<Row> findOwned(UUID id, long userId) {
        return jdbc.sql("select id, title from app.conversations where id = :id and user_id = :user")
                .param("id", id).param("user", userId)
                .query((rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("title")))
                .optional();
    }

    /** Row-locks the conversation until the transaction ends; false if it is gone or not the user's. */
    public boolean lockOwned(UUID id, long userId) {
        return jdbc.sql("select 1 from app.conversations where id = :id and user_id = :user for update")
                .param("id", id).param("user", userId).query(Integer.class).optional().isPresent();
    }

    public List<ConversationSummary> list(long userId) {
        return jdbc.sql("select id, title, updated_at from app.conversations where user_id = :user "
                        + "order by updated_at desc, created_at desc")
                .param("user", userId)
                .query((rs, n) -> new ConversationSummary(rs.getObject("id", UUID.class), rs.getString("title"),
                        rs.getObject("updated_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    public void touch(UUID id) {
        jdbc.sql("update app.conversations set updated_at = now() where id = :id").param("id", id).update();
    }

    public boolean delete(UUID id, long userId) {
        return jdbc.sql("delete from app.conversations where id = :id and user_id = :user")
                .param("id", id).param("user", userId).update() > 0;
    }
}
