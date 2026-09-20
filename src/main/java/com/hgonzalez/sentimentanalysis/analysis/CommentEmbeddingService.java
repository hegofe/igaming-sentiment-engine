package com.hgonzalez.sentimentanalysis.analysis;

import com.hgonzalez.sentimentanalysis.contracts.Aspect;
import com.hgonzalez.sentimentanalysis.contracts.CommentSource;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CommentEmbeddingService {

    private final VectorStore vectorStore;
    private final CommentAnalysisRepository commentAnalysisRepository;

    public CommentEmbeddingService(VectorStore vectorStore, CommentAnalysisRepository commentAnalysisRepository) {
        this.vectorStore = vectorStore;
        this.commentAnalysisRepository = commentAnalysisRepository;
    }

    public void embedAndStore(UUID commentId, CommentSource source, String userId, Instant occurredAt,
                               String text, SentimentResult result) {
        List<String> aspectNames = result.aspects().stream().map(Aspect::name).toList();
        String content = "[%s | %s | %s] %s".formatted(
                source.name(), result.label().name(),
                aspectNames.isEmpty() ? "NONE" : String.join(",", aspectNames),
                text);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("commentId", commentId.toString());
        metadata.put("source", source.name());
        metadata.put("sentiment", result.label().name());
        metadata.put("aspects", aspectNames);
        metadata.put("occurredDay", occurredAt.atOffset(ZoneOffset.UTC).toLocalDate().toString());
        if (userId != null) {
            metadata.put("userId", userId);
        }

        Document document = new Document(commentId.toString(), content, metadata);

        vectorStore.add(List.of(document));

        commentAnalysisRepository.upsert(
                commentId,
                result.label().name(),
                result.confidence(),
                aspectNames.toArray(String[]::new),
                result.rationale(),
                result.producedBy().name(),
                result.modelId());
    }
}
