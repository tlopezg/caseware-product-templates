# Part 2 — Fan-Out Worker: Implementation Notes

## Key tradeoffs

1. **Fan-out never rehydrates engagements.** Relies on the engagement
   registry's metadata (template ID + version) captured at creation time or
   via bulk export. This is what makes the ~1 min load constraint irrelevant
   to the hot path.
2. **Idempotency at `(engagementId, changeId)`.** Conditional writes make
   replays safe; skipped rows are counted.
3. **Downstream capacity guarded by a token bucket.** On exhaustion the
   worker throws `BackpressureException`; the caller's queue re-enqueues.
   It never blocks fan-out threads on the ~1 min downstream.
4. **Retries** use exponential backoff with full jitter, capped at 5 min.
   Programming errors (non-`DownstreamException` runtime errors) go straight
   to DLQ.
5. **DLQ is first-class.** Failed evaluations are inspectable and replayable.
6. **Per-template FIFO ordering** so withdrawn versions and subsequent
   publishes are processed in version order.
7. **Change-ids are content hashes**, not versions.
8. **Region routing per engagement**; no engagement state crosses regions.

## Build

```bash
mvn -q test