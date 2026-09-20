package com.hgonzalez.sentimentanalysis.ingest;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface CommentRepository extends Repository<Comment, UUID> {

    @Modifying
    @Query("""
            INSERT INTO ingest.comment (id, source, user_id, text, occurred_at)
            VALUES (:id, :source, :userId, :text, :occurredAt)
            ON CONFLICT (id) DO NOTHING
            """)
    int insertIfAbsent(@Param("id") UUID id,
                        @Param("source") int source,
                        @Param("userId") String userId,
                        @Param("text") String text,
                        @Param("occurredAt") Instant occurredAt);
}
