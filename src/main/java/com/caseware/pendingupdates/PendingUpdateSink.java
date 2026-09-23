package com.caseware.pendingupdates;

import com.caseware.pendingupdates.model.ChangeRecord;
import com.caseware.pendingupdates.model.EngagementRef;

/**
 * Idempotent sink for pending-update state.
 *
 * <p>Idempotency key: {@code (firmId, engagementId, changeId)}.
 */
public interface PendingUpdateSink {

    /**
     * Marks a change as pending for an engagement.
     *
     * @return {@code true} if a new pending row was created;
     *         {@code false} if the change was already pending, applied, or declined.
     */
    boolean markPending(EngagementRef engagement, ChangeRecord change, String region);
}