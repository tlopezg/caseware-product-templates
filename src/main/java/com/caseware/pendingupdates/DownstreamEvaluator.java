package com.caseware.pendingupdates;

import com.caseware.pendingupdates.model.ChangeRecord;
import com.caseware.pendingupdates.model.EngagementRef;
import com.caseware.pendingupdates.model.EvaluationResult;
import com.caseware.pendingupdates.model.DownstreamException;

/**
 * The ~1 minute downstream call owned by another team. Rate-limited.
 */
public interface DownstreamEvaluator {
    EvaluationResult evaluate(EngagementRef engagement, ChangeRecord change)
            throws DownstreamException;
}