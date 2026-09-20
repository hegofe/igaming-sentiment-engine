package com.hgonzalez.sentimentanalysis.analysis;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProcessedMessageRepository extends Repository<ProcessedMessage, UUID> {

    @Modifying
    @Query("""
            INSERT INTO analysis.processed_message (event_id, comment_id)
            VALUES (:eventId, :commentId)
            ON CONFLICT (event_id) DO NOTHING
            """)
    int insertIfAbsent(@Param("eventId") UUID eventId, @Param("commentId") UUID commentId);
}
