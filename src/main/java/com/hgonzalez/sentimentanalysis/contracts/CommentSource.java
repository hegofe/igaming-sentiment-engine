package com.hgonzalez.sentimentanalysis.contracts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.stream.Stream;

public enum CommentSource {
    APP_ANDROID(1),
    APP_APPLE(2),
    WEB(3);

    private final int code;

    CommentSource(int code) {
        this.code = code;
    }

    @JsonValue
    public int code() {
        return code;
    }

    @JsonCreator
    public static CommentSource fromCode(int code) {
        return Stream.of(values())
                .filter(source -> source.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown CommentSource code: " + code));
    }
}
