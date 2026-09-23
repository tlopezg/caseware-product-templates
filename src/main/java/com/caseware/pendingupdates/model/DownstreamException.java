package com.caseware.pendingupdates.model;

public class DownstreamException extends Exception {
    public DownstreamException(String message) {
        super(message);
    }

    public DownstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}