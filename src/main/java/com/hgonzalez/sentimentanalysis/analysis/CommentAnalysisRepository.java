package com.hgonzalez.sentimentanalysis.analysis;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CommentAnalysisRepository extends Repository<CommentAnalysis, UUID> {

    @Modifying
    @Query("""
            INSERT INTO analysis.comment_analysis
                (comment_id, sentiment, confidence, aspects, rationale, classifier_kind, model_id)
            VALUES (:commentId, :sentiment, :confidence, :aspects, :rationale, :classifierKind, :modelId)
            ON CONFLICT (comment_id) DO UPDATE SET
                sentiment = EXCLUDED.sentiment,
                confidence = EXCLUDED.confidence,
                aspects = EXCLUDED.aspects,
                rationale = EXCLUDED.rationale,
                classifier_kind = EXCLUDED.classifier_kind,
                model_id = EXCLUDED.model_id,
                analysed_at = now()
            """)
    int upsert(@Param("commentId") UUID commentId,
               @Param("sentiment") String sentiment,
               @Param("confidence") double confidence,
               @Param("aspects") String[] aspects,
               @Param("rationale") String rationale,
               @Param("classifierKind") String classifierKind,
               @Param("modelId") String modelId);
}
