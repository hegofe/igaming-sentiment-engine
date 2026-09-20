package com.hgonzalez.sentimentanalysis.rag;

import com.hgonzalez.sentimentanalysis.contracts.Aspect;
import com.hgonzalez.sentimentanalysis.contracts.SentimentLabel;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Component
public class RetrievalFilterBuilder {

    public Filter.Expression build(InsightFilters filters) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> conditions = new ArrayList<>();

        if (filters.sentiments() != null && !filters.sentiments().isEmpty()) {
            conditions.add(b.in("sentiment", filters.sentiments().stream().map(SentimentLabel::name).toArray()));
        }
        if (filters.aspects() != null && !filters.aspects().isEmpty()) {
            for (Aspect aspect : filters.aspects()) {
                conditions.add(b.in("aspects", aspect.name()));
            }
        }
        if (filters.from() != null) {
            conditions.add(b.gte("occurredDay", filters.from().atOffset(ZoneOffset.UTC).toLocalDate().toString()));
        }
        if (filters.to() != null) {
            conditions.add(b.lte("occurredDay", filters.to().atOffset(ZoneOffset.UTC).toLocalDate().toString()));
        }

        if (conditions.isEmpty()) {
            return null;
        }

        FilterExpressionBuilder.Op combined = conditions.get(0);
        for (int i = 1; i < conditions.size(); i++) {
            combined = b.and(combined, conditions.get(i));
        }
        return combined.build();
    }
}
