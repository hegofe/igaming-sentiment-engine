package com.hgonzalez.sentimentanalysis.analysis;

public class NonRetryableMessageException extends RuntimeException {

    public NonRetryableMessageException(String message) {
        super(message);
    }
}
