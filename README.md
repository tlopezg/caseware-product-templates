# caseware — Pending Template Updates

Take-home architecture design exercise: detecting and surfacing pending
product-template updates across ~4,000 firms and ~800,000 active engagement
files, with human-readable summaries and audit-defensible accept/decline.

## Contents

- [`docs/design-document.md`](docs/design-document.md) — Part 1: architecture,
  correctness, scale/cost, summaries, tradeoffs.
- [`docs/diagrams/`](docs/diagrams/) — diagrams referenced from the design doc.
- [`docs/ai-usage.md`](docs/ai-usage.md) — where AI helped, where it was
  corrected, and where it must not be trusted.
- [`src/`](src/) — Part 2: Java implementation of the template-publish
  fan-out worker.
- [`README-implementation.md`](README-implementation.md) — implementation
  tradeoffs and how to build/test.

## Highlights

- Change-ids are **content hashes**, not version numbers — the only model that
  survives branches and withdrawn versions.
- "Declined" pins a **set of change-ids**, not a version.
- The fan-out path **never rehydrates engagements** (~1 min load is a hard
  constraint and is avoided on the hot path).
- Human-readable summaries are generated **once per ChangeRecord**, not per
  engagement — this is what keeps inference cost under `$X`.

## Build & test

```bash
mvn -q test