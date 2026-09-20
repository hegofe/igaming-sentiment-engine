package com.hgonzalez.sentimentanalysis.rag;

import java.util.List;

public record Aggregates(long totalComments, double negativeShare, List<AspectCount> topAspects) {
}
