package com.caseware.pendingupdates;

import com.caseware.pendingupdates.model.EngagementRef;
import java.util.stream.Stream;

/**
 * Metadata-only view of engagements for a given template and region.
 *
 * <p><b>Important:</b> implementations MUST NOT use the ~1 minute engagement
 * rehydrate path. They should read from the engagement creation hook's
 * metadata store or a one-time bulk export of (engagementId, templateId,
 * templateVersion).
 */
public interface EngagementRegistry {
    Stream<EngagementRef> findEngagementsByTemplate(String templateId, String region);
}