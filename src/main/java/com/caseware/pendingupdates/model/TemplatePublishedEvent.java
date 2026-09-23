package com.caseware.pendingupdates.model;

import java.util.List;

public record TemplatePublishedEvent(
        String templateId,
        String fromVersion,
        String toVersion,
        String region,
        List<ChangeRecord> changes) {
}