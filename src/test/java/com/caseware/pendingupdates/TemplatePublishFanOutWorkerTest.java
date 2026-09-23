package com.caseware.pendingupdates;

import com.caseware.pendingupdates.model.BackpressureException;
import com.caseware.pendingupdates.model.ChangeRecord;
import com.caseware.pendingupdates.model.EngagementRef;
import com.caseware.pendingupdates.model.TemplatePublishedEvent;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplatePublishFanOutWorkerTest {

    private static final EngagementRef ENG =
            new EngagementRef("eng-1", "firm-1", "tmpl-1", "v4", "EU");

    private static final ChangeRecord CHANGE =
            new ChangeRecord("c5a", "tmpl-1", "v4", "v5", "{}", "summary");

    private static EngagementRegistry registryOf(EngagementRef... engagements) {
        return (templateId, region) -> Stream.of(engagements);
    }

    private static DeadLetterQueue noopDlq() {
        return (failed, cause) -> { /* no-op */ };
    }

    private static TemplatePublishFanOutWorker worker(
            PendingUpdateSink sink,
            DownstreamEvaluator evaluator,
            DeadLetterQueue dlq,
            RateLimiter limiter) {
        return new TemplatePublishFanOutWorker(
                registryOf(ENG), sink, evaluator, dlq, limiter,
                3, Duration.ofMillis(1), new Random(42));
    }

    private static TemplatePublishedEvent eventWith(ChangeRecord change) {
        return new TemplatePublishedEvent("tmpl-1", "v4", "v5", "EU", List.of(change));
    }

    @Test
    void idempotentWhenChangeAlreadyPending() {
        InMemorySink sink = new InMemorySink();
        sink.markPending(ENG, CHANGE, "EU");

        TemplatePublishFanOutWorker worker = worker(
                sink,
                new CountingEvaluator(true),
                noopDlq(),
                new TokenBucketRateLimiter(100, 100));

        FanOutResult result = worker.handle(eventWith(CHANGE));

        assertEquals(1, result.skipped());
        assertEquals(0, result.successes());
        assertEquals(0, result.failures());
    }

    @Test
    void retriesThenDlqOnPersistentFailure() {
        InMemoryDlq dlq = new InMemoryDlq();
        CountingEvaluator evaluator = new CountingEvaluator(false);

        TemplatePublishFanOutWorker worker = worker(
                new InMemorySink(), evaluator, dlq,
                new TokenBucketRateLimiter(100, 100));

        FanOutResult result = worker.handle(eventWith(CHANGE));

        assertEquals(1, result.failures());
        assertEquals(0, result.successes());
        assertEquals(1, dlq.size());
        assertEquals(3, evaluator.calls(), "should retry maxAttempts times");
    }

    @Test
    void doesNotCallDownstreamWhenRateLimited() {
        CountingEvaluator evaluator = new CountingEvaluator(true);
        RateLimiter exhausted = new TokenBucketRateLimiter(1, 0);
        // Drain the single token so the first acquire fails.
        exhausted.tryAcquire();

        TemplatePublishFanOutWorker worker = worker(
                new InMemorySink(), evaluator, noopDlq(), exhausted);

        assertThrows(BackpressureException.class,
                () -> worker.handle(eventWith(CHANGE)));
        assertEquals(0, evaluator.calls());
    }

    @Test
    void successfulEvaluationRecordsSuccess() {
        TemplatePublishFanOutWorker worker = worker(
                new InMemorySink(),
                new CountingEvaluator(true),
                noopDlq(),
                new TokenBucketRateLimiter(100, 100));

        FanOutResult result = worker.handle(eventWith(CHANGE));

        assertEquals(1, result.successes());
        assertEquals(0, result.failures());
        assertEquals(0, result.skipped());
    }

    @Test
    void tokenBucketRefillsOverTime() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1000);
        assertTrue(limiter.tryAcquire(), "first acquire should succeed");
        Thread.sleep(5);
        assertTrue(limiter.tryAcquire(), "token should have refilled");
    }
}