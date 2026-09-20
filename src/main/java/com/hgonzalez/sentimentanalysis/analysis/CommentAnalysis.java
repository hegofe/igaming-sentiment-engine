package com.hgonzalez.sentimentanalysis.analysis;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Table("analysis.comment_analysis")
public record CommentAnalysis(
        @Id UUID commentId,
        String sentiment,
        BigDecimal confidence,
        String[] aspects,
        String rationale,
        String classifierKind,
        String modelId,
        Instant analysedAt) {
}
