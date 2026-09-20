package com.hgonzalez.sentimentanalysis.rag;

import com.hgonzalez.sentimentanalysis.contracts.CommentSource;

import java.util.UUID;

public record Citation(UUID id, CommentSource source, String excerpt) {
}
