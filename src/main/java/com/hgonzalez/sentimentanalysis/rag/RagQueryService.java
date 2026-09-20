package com.hgonzalez.sentimentanalysis.rag;

import com.hgonzalez.sentimentanalysis.contracts.Aspect;
import com.hgonzalez.sentimentanalysis.contracts.CommentSource;
import com.hgonzalez.sentimentanalysis.contracts.SentimentLabel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RagQueryService {

    private static final Logger log = LoggerFactory.getLogger(RagQueryService.class);
    private static final int TOP_ASPECTS_LIMIT = 5;
    private static final int DEFAULT_TOP_K = 3;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final VectorStore vectorStore;
    private final RetrievalFilterBuilder retrievalFilterBuilder;
    private final ChatClient chatClient;

    public RagQueryService(NamedParameterJdbcTemplate jdbcTemplate, VectorStore vectorStore,
                            RetrievalFilterBuilder retrievalFilterBuilder, ChatClient.Builder chatClientBuilder,
                            @Value("classpath:prompts/insight-system.st") Resource systemPrompt) {
        this.jdbcTemplate = jdbcTemplate;
        this.vectorStore = vectorStore;
        this.retrievalFilterBuilder = retrievalFilterBuilder;
        this.chatClient = chatClientBuilder.defaultSystem(systemPrompt).build();
    }

    public InsightAnswer answerQuery(InsightQueryRequest request) {
        InsightFilters filters = toInsightFilters(request.filters());

        Aggregates aggregates = computeAggregates(filters);
        List<Document> evidence = retrieveEvidence(request.question(), filters, DEFAULT_TOP_K);

        try {
            LlmInsightAssessment assessment = chatClient.prompt()
                    .options(ChatOptions.builder().temperature(0.0).maxTokens(300))
                    .user(buildUserMessage(request.question(), aggregates, evidence))
                    .call()
                    .entity(LlmInsightAssessment.class);

            List<Citation> citations = buildCitations(assessment.citedCommentIds(), evidence);

            return new InsightAnswer(assessment.answer(), citations, aggregates);
        } catch (Exception ex) {
            log.warn("Insight generation failed, returning aggregates-only answer: {}", ex.getMessage());
            String answer = "Unable to generate a narrative answer right now due to a model error; "
                    + "the aggregate statistics below are still accurate.";
            return new InsightAnswer(answer, List.of(), aggregates);
        }
    }

    public List<Document> retrieveEvidence(String question, InsightFilters filters, int topK) {
        Filter.Expression filterExpression = retrievalFilterBuilder.build(filters);

        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(question)
                .topK(topK);
        if (filterExpression != null) {
            requestBuilder.filterExpression(filterExpression);
        }

        return vectorStore.similaritySearch(requestBuilder.build());
    }

    public Aggregates computeAggregates(InsightFilters filters) {
        String sentimentClause = sentimentClause(filters.sentiments());
        String aspectClause = aspectClause(filters.aspects());
        String dateClause = """
                AND (:fromInstant::timestamptz IS NULL OR c.occurred_at >= :fromInstant::timestamptz)
                AND (:toInstant::timestamptz IS NULL OR c.occurred_at <= :toInstant::timestamptz)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fromInstant", toOffsetDateTime(filters.from()))
                .addValue("toInstant", toOffsetDateTime(filters.to()));

        String totalsSql = """
                SELECT count(*) AS total_comments,
                       count(*) FILTER (WHERE ca.sentiment = 'NEGATIVE') AS negative_count
                FROM analysis.comment_analysis ca
                JOIN ingest.comment c ON c.id = ca.comment_id
                WHERE %s AND %s
                %s
                """.formatted(sentimentClause, aspectClause, dateClause);

        Map<String, Object> totals = jdbcTemplate.queryForMap(totalsSql, params);
        long totalComments = ((Number) totals.get("total_comments")).longValue();
        long negativeCount = ((Number) totals.get("negative_count")).longValue();
        double negativeShare = totalComments == 0 ? 0.0 : (double) negativeCount / totalComments;

        String topAspectsSql = """
                SELECT aspect, count(*) AS cnt
                FROM analysis.comment_analysis ca
                JOIN ingest.comment c ON c.id = ca.comment_id
                CROSS JOIN LATERAL unnest(ca.aspects) AS aspect
                WHERE %s AND %s
                %s
                GROUP BY aspect
                ORDER BY cnt DESC
                LIMIT %d
                """.formatted(sentimentClause, aspectClause, dateClause, TOP_ASPECTS_LIMIT);

        List<AspectCount> topAspects = jdbcTemplate.query(topAspectsSql, params, (rs, rowNum) ->
                new AspectCount(Aspect.valueOf(rs.getString("aspect")), rs.getLong("cnt")));

        return new Aggregates(totalComments, negativeShare, topAspects);
    }

    private InsightFilters toInsightFilters(InsightQueryFilters filters) {
        if (filters == null) {
            return new InsightFilters(null, null, null, null);
        }
        Instant from = filters.from() != null ? filters.from().atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant to = filters.to() != null
                ? filters.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1) : null;
        return new InsightFilters(filters.sentiments(), filters.aspects(), from, to);
    }

    private String buildUserMessage(String question, Aggregates aggregates, List<Document> evidence) {
        String topAspectsText = aggregates.topAspects().isEmpty()
                ? "none"
                : aggregates.topAspects().stream()
                        .map(a -> a.aspect().name() + "=" + a.count())
                        .collect(Collectors.joining(", "));

        String evidenceText = evidence.isEmpty()
                ? "(no evidence retrieved)"
                : evidence.stream()
                        .map(d -> "- id=%s source=%s text=\"%s\"".formatted(
                                d.getMetadata().get("commentId"), d.getMetadata().get("source"), d.getText()))
                        .collect(Collectors.joining("\n"));

        return """
                Question: %s

                Aggregates:
                - totalComments: %d
                - negativeShare: %.2f
                - topAspects: %s

                Evidence (%d comments):
                %s
                """.formatted(question, aggregates.totalComments(), aggregates.negativeShare(),
                        topAspectsText, evidence.size(), evidenceText);
    }

    private List<Citation> buildCitations(List<UUID> citedCommentIds, List<Document> evidence) {
        if (citedCommentIds == null || citedCommentIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, Document> evidenceById = evidence.stream().collect(Collectors.toMap(
                d -> UUID.fromString((String) d.getMetadata().get("commentId")),
                d -> d,
                (first, second) -> first));

        return citedCommentIds.stream()
                .filter(evidenceById::containsKey)
                .map(id -> {
                    Document document = evidenceById.get(id);
                    CommentSource source = CommentSource.valueOf((String) document.getMetadata().get("source"));
                    return new Citation(id, source, document.getText());
                })
                .toList();
    }

    private String sentimentClause(List<SentimentLabel> sentiments) {
        if (sentiments == null || sentiments.isEmpty()) {
            return "TRUE";
        }
        String values = sentiments.stream().map(s -> "'" + s.name() + "'").collect(Collectors.joining(","));
        return "ca.sentiment IN (" + values + ")";
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private String aspectClause(List<Aspect> aspects) {
        if (aspects == null || aspects.isEmpty()) {
            return "TRUE";
        }
        String values = aspects.stream().map(a -> "'" + a.name() + "'").collect(Collectors.joining(","));
        return "ca.aspects && ARRAY[" + values + "]::text[]";
    }
}
