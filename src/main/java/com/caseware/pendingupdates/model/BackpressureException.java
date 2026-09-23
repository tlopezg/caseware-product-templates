package com.caseware.pendingupdates.model;

public class BackpressureException extends RuntimeException {
    public final transient EngagementRef engagement;
    public final transient ChangeRecord change;

    public BackpressureException(EngagementRef engagement, ChangeRecord change) {
        super("downstream capacity exhausted");
        this.engagement = engagement;
        this.change = change;
    }
}