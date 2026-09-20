package com.hgonzalez.sentimentanalysis.ingest.api;

import com.hgonzalez.sentimentanalysis.contracts.CommentIngestedEvent;
import com.hgonzalez.sentimentanalysis.ingest.CommentEventPublisher;
import com.hgonzalez.sentimentanalysis.ingest.CommentRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CommentIngestController {

    private static final Logger log = LoggerFactory.getLogger(CommentIngestController.class);

    private final CommentRepository commentRepository;
    private final CommentEventPublisher commentEventPublisher;

    public CommentIngestController(CommentRepository commentRepository, CommentEventPublisher commentEventPublisher) {
        this.commentRepository = commentRepository;
        this.commentEventPublisher = commentEventPublisher;
    }

    @PostMapping(
            path = "/comments",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CommentIngestResponse> ingestComment(@Valid @RequestBody CommentRequest request) {
        int inserted = commentRepository.insertIfAbsent(
                request.uuid(), request.source().code(), request.userId(), request.text(), request.occurredAt());
        boolean duplicate = inserted == 0;

        log.info("Ingested comment {} (source={}, duplicate={})", request.uuid(), request.source(), duplicate);

        if (duplicate) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new CommentIngestResponse(true));
        }

        commentEventPublisher.publish(new CommentIngestedEvent(
                UUID.randomUUID().toString(),
                1,
                request.uuid(),
                request.source(),
                request.userId(),
                request.text(),
                request.occurredAt()));

        return ResponseEntity.ok().build();
    }
}
