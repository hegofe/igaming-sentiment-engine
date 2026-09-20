package com.hgonzalez.sentimentanalysis.rag;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class InsightQueryController {

    private final RagQueryService ragQueryService;

    public InsightQueryController(RagQueryService ragQueryService) {
        this.ragQueryService = ragQueryService;
    }

    @PostMapping(
            path = "/insights/query",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public InsightAnswer query(@Valid @RequestBody InsightQueryRequest request) {
        return ragQueryService.answerQuery(request);
    }
}
