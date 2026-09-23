package com.caseware.pendingupdates;

import com.caseware.pendingupdates.model.BackpressureException;
import com.caseware.pendingupdates.model.ChangeRecord;
import com.caseware.pendingupdates.model.DownstreamException;
import com.caseware.pendingupdates.model.EngagementRef;
import com.caseware.pendingupdates.model.EvaluationResult;
import com.caseware.pendingupdates.model.FailedEvaluation;
import com.caseware.pendingupdates.model.TemplatePublishedEvent;

import java.time.Duration;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;

/**
 * Fan-out worker for a single template publish event.
 *
 * <p>Design properties:
 * <ul>
 *   <li>Idempotent per {@code (engagementId, changeId)} via {@link PendingUpdateSink}.</li>
 *   <li>Never rehydrates engagements; relies on {@link EngagementRegistry} metadata.</li>
 *   <li>Guards downstream capacity with {@link RateLimiter}; on exhaustion throws
 *       {@link BackpressureException} and lets the caller's queue re-enqueue.</li>
 *   <li>Retries transient failures with exponential backoff + full jitter;
 *       persistent failures go to {@link DeadLetterQueue}.</li>
 * </ul>
 */
public final class TemplatePublishFanOutWorker {

    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    private final EngagementRegistry registry;
    private final PendingUpdateSink sink;
    private final DownstreamEvaluator evaluator;
    private final DeadLetterQueue dlq;
    private final RateLimiter rateLimiter;
    private final int maxAttempts;
    private final Duration baseBackoff;
    private final Random jitter;

    public TemplatePublishFanOutWorker(
            EngagementRegistry registry,
            PendingUpdateSink sink,
            DownstreamEvaluator evaluator,
            DeadLetterQueue dlq,
            RateLimiter rateLimiter,
            int maxAttempts,
            Duration baseBackoff) {
        this(registry, sink, evaluator, dlq, rateLimiter, maxAttempts, baseBackoff, new Random());
    }

    /** Visible for tests: allows injecting a seeded {@link Random}. */
    public TemplatePublishFanOutWorker(
            EngagementRegistry registry,
            PendingUpdateSink sink,
            DownstreamEvaluator evaluator,
            DeadLetterQueue dlq,
            RateLimiter rateLimiter,
            int maxAttempts,
            Duration baseBackoff,
            Random jitter) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.registry = Objects.requireNonNull(registry, "registry");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.dlq = Objects.requireNonNull(dlq, "dlq");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.maxAttempts = maxAttempts;
        this.baseBackoff = Objects.requireNonNull(baseBackoff, "baseBackoff");
        this.jitter = Objects.requireNonNull(jitter, "jitter");
    }

    /**
     * Handles a single template publish. Idempotent per
     * {@code (templateId, fromVersion, toVersion, changeId)}.
     */
    public FanOutResult handle(TemplatePublishedEvent event) {
        Objects.requireNonNull(event, "event");
        FanOutResult result = new FanOutResult(event.templateId(), event.toVersion());

        for (ChangeRecord change : event.changes()) {
            try (Stream<EngagementRef> engagements =
                         registry.findEngagementsByTemplate(event.templateId(), event.region())) {
                engagements.forEach(engagement -> {
                    if (!rateLimiter.tryAcquire()) {
                        throw new BackpressureException(engagement, change);
                    }
                    processOne(engagement, change, result);
                });
            }
        }
        return result;
    }

    private void processOne(EngagementRef engagement, ChangeRecord change, FanOutResult result) {
        boolean newlyPending = sink.markPending(engagement, change, engagement.region());
        if (!newlyPending) {
            result.recordSkipped();
            return;
        }

        DownstreamException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                EvaluationResult evaluation = evaluator.evaluate(engagement, change);
                if (evaluation != null && evaluation.isSuccess()) {
                    result.recordSuccess();
                    return;
                }
                lastFailure = new DownstreamException(
                        "non-success status: "
                                + (evaluation == null ? "null" : evaluation.status()));
            } catch (DownstreamException e) {
                lastFailure = e;
            } catch (RuntimeException e) {
                // Programming error: do not retry blindly.
                dlq.send(new FailedEvaluation(engagement, change), e);
                result.recordFailure();
                return;
            }

            if (attempt < maxAttempts) {
                sleepWithJitter(attempt);
            }
        }

        dlq.send(new FailedEvaluation(engagement, change), lastFailure);
        result.recordFailure();
    }

    private void sleepWithJitter(int attempt) {
        long base = baseBackoff.toMillis() * (1L << (attempt - 1));
        long capped = Math.min(base, MAX_BACKOFF.toMillis());
        long half = capped / 2;
        long sleepMillis = half + (long) (jitter.nextDouble() * half);
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new CancellationException("interrupted during backoff");
        }
    }
}