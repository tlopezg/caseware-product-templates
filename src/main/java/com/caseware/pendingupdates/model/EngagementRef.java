package com.caseware.pendingupdates.model;

public record EngagementRef(
        String engagementId,
        String firmId,
        String templateId,
        String templateVersion,
        String region) {
}