# AI Usage Notes

## Where AI helped
- Structuring the design document.
- Drafting the Java skeleton (records, worker loop, token bucket).
- Generating JUnit test names and enumerating failure modes.

## Where AI output was corrected or ignored
- AI initially proposed calling the ~1 min engagement load inside the fan-out
  path. Infeasible at 800k engagements; replaced with a metadata-only registry
  and a no-rehydrate fan-out.
- AI defaulted to a linear version model. Overridden with content-hash
  change-ids, required by branches and withdrawn versions.
- AI proposed per-engagement LLM summaries. Replaced with one summary per
  ChangeRecord plus read-time composition, to keep inference cost bounded by
  change volume (not engagement volume).

## How I'd guide other engineers using AI on this system
- Use AI for boilerplate, scaffolding and failure-mode brainstorming.
- Require human review for anything touching residency, audit trail,
  decline semantics, or the downstream capacity contract.

## Where AI must not be trusted in this domain
- Deciding what "declined" pins.
- Generating the human-readable summary without groundedness checks and a
  deterministic fallback.
- Any reasoning about regulatory defensibility.