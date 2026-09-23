package com.caseware.pendingupdates;

import java.util.concurrent.atomic.AtomicLong;

public final class FanOutResult {

    private final String templateId;
    private final String toVersion;
    private final AtomicLong success = new AtomicLong();
    private final AtomicLong skipped = new AtomicLong();
    private final AtomicLong failure = new AtomicLong();

    public FanOutResult(String templateId, String toVersion) {
        this.templateId = templateId;
        this.toVersion = toVersion;
    }

    public void recordSuccess() {
        success.incrementAndGet();
    }

    public void recordSkipped() {
        skipped.incrementAndGet();
    }

    public void recordFailure() {
        failure.incrementAndGet();
    }

    public long successes() {
        return success.get();
    }

    public long skipped() {
        return skipped.get();
    }

    public long failures() {
        return failure.get();
    }

    public String templateId() {
        return templateId;
    }

    public String toVersion() {
        return toVersion;
    }

    @Override
    public String toString() {
        return "FanOutResult{templateId=" + templateId
                + ", toVersion=" + toVersion
                + ", success=" + successes()
                + ", skipped=" + skipped()
                + ", failure=" + failures()
                + '}';
    }
}