package com.caseware.pendingupdates;

import com.caseware.pendingupdates.model.ChangeRecord;
import com.caseware.pendingupdates.model.DownstreamException;
import com.caseware.pendingupdates.model.EngagementRef;
import com.caseware.pendingupdates.model.EvaluationResult;

import java.util.concurrent.atomic.AtomicInteger;

public final class CountingEvaluator implements DownstreamEvaluator {

    private final AtomicInteger calls = new AtomicInteger();
    private final boolean alwaysSucceed;

    public CountingEvaluator(boolean alwaysSucceed) {
        this.alwaysSucceed = alwaysSucceed;
    }

    @Override
    public EvaluationResult evaluate(EngagementRef engagement, ChangeRecord change)
            throws DownstreamException {
        calls.incrementAndGet();
        if (alwaysSucceed) {
            return new EvaluationResult(true, "OK");
        }
        throw new DownstreamException("simulated failure");
    }

    public int calls() {
        return calls.get();
    }
}