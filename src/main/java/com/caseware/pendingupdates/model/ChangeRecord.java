package com.caseware.pendingupdates.model;

public record ChangeRecord(
        String changeId,
        String templateId,
        String fromVersion,
        String toVersion,
        String structuredDiff,
        String humanSummary) {
}