Part 1 — Design Document
0. Scope and Assumptions
In scope: detect pending template updates per engagement file, produce a human-readable summary, expose an up-to-date "pending updates" indicator, and record accept/decline decisions. Out of scope (per prompt): actually applying template content to an engagement.

Assumptions I am making explicit (not invented — flagged as choices):

Template versions form a DAG, not a line. A firm that declined v5 may later be offered v7 that subsumes v5's changes.

"Declined" pins the engagement to a concrete set of applied change-ids, not to a version number. This is the only way to make "declined v5, offered v7 containing v5's changes" coherent.

Engagement content is client-confidential; template content is not firm-specific. Residency applies to engagement data and to any derived artifact that reveals engagement state.

We can add hooks/events to (a) template publish and (b) engagement creation/decision. We cannot change the ~1 min engagement load time.

4,000 firms, 800,000 active engagements, 40 products, ~1 publish/week/product ⇒ ~40 publishes/week, ~5.7/day.

$X is left symbolic; I show arithmetic in units so the reader can plug in $X.

1. Architecture and Data Ownership
1.1 Ownership boundaries
Data	Owner	Notes
Product templates (all versions)	Template Service (existing)	Shared across firms. Not firm-specific.
Engagement files (confidential)	Engagement Service (existing, per-firm/per-region DBs)	Stores template ID + version. Load = ~1 min.
Template change graph + change summaries	New: Template Change Service	Derived from template DB. Shared, non-confidential.
Per-engagement pending-update state	New: Engagement Update State Store	Firm-scoped, region-pinned.
Accept/decline decisions	New: Decision Log (append-only)	Firm-scoped, region-pinned, audit-grade.
Key ownership decision: the change graph (what changed between vN and vM, as structured + human-readable change records) is owned by the Template Change Service and is shared. Per-engagement state (which change-ids are pending/applied/declined for this engagement) is owned by the Engagement Update State Store and is firm-scoped and region-pinned. This split keeps the expensive-to-compute, non-confidential part shared, and the confidential part isolated and cheap.

1.2 The change graph
When a template is published (vN → vM), we compute a ChangeRecord per logical change:

ChangeRecord {
  changeId        // content hash of the diff hunk — stable across branches
  templateId
  fromVersion, toVersion
  structuredDiff  // machine
  humanSummary    // generated (see §4)
  residencyClass  // "shared"
}
changeId is a content hash, not a version. This is what makes "declined v5, offered v7 containing v5's changes" tractable: v7's diff is decomposed into change-ids; any change-id already declined by the engagement is filtered out; only genuinely new change-ids are offered.

1.3 Component diagram
                 ┌────────────────────────────┐
                 │  Template Service (existing)│
                 │  all versions, publish hook │
                 └──────────────┬─────────────┘
                                │ publish event (SNS/SQS)
                                ▼
                 ┌────────────────────────────┐
                 │  Template Change Service    │
                 │  - computes structured diff │
                 │  - emits ChangeRecords      │
                 │  - generates human summary  │
                 └──────────────┬─────────────┘
                                │ ChangeRecord published
                                ▼
        ┌───────────────────────────────────────────────┐
        │  Fan-out Worker (Part 2)                       │
        │  for each affected engagement:                 │
        │    enqueue PendingUpdateEvaluation             │
        └──────────────┬────────────────────────────────┘
                       │ (regional queues)
        ┌──────────────┴───────────────┐
        ▼                              ▼
┌──────────────────┐          ┌──────────────────┐
│ EU State Store   │          │ US/CA State Store│
│ + Decision Log   │          │ + Decision Log   │
└────────┬─────────┘          └────────┬─────────┘
         │                             │
         ▼                             ▼
┌───────────────────────────────────────────────┐
│  Engagement Service (existing, per region)     │
│  - exposes "list engagements + template ver"   │
│  - accepts decisions (apply/decline)           │
└───────────────────────────────────────────────┘
2. Correctness and Production Evolution
2.1 The "declined pins what?" problem
Declined pins the set of changeIds the user rejected, at the moment of decision. Not a version number. This is the only model that survives:

branches (v7 contains v5's change),

withdrawals (withdrawn version → its change-ids are removed from the offerable set but remain applied if already accepted),

multiple accumulating updates.

State per engagement (append-only):

EngagementUpdateState {
  engagementId, firmId, region
  appliedChangeIds:  Set<changeId>   // accepted + inherited from creation
  declinedChangeIds: Set<changeId>   // rejected by user
  pendingChangeIds:  Set<changeId>   // offered, no decision yet
  lastEvaluatedTemplateVersion
}
Decision semantics:

Apply on a pending change-id → move to appliedChangeIds.

Decline → move to declinedChangeIds.

New template version published → compute its change-ids; for each: if in applied or declined, skip; else add to pending. The user is re-offered only genuinely new changes.

This is why a user who declined v5 can later be offered v7: v7's new change-ids go to pending; v5's change-ids stay in declined and are not re-offered.

2.2 Production evolution / backfill
New engagements: the creation hook writes an EngagementUpdateState row with appliedChangeIds = all change-ids in the version used to create it. Cheap, synchronous, no engagement load.

Existing engagements (backfill): this is the hard part. We cannot read 800k engagements synchronously (~1 min each ⇒ ~15,200 hours serialized). Backfill strategy:

Bulk export path: request a one-time bulk export of (engagementId, templateId, version) from the Engagement Service's own DB (it stores these fields already). This is a metadata read, not a rehydrate. If the Engagement Service cannot export, we accept a slower backfill.
Reconcile with state store: for each row, compute appliedChangeIds from the version's change-ids. No engagement load needed for the initial state — the version number tells us which change-ids are already in.
Lazy repair: on first engagement open, the Engagement Service hook confirms the state store row; if missing, it is created then. This bounds worst-case backfill latency and guarantees existing engagements get the indicator.
Migration safety: state store is a new, additive store. Engagement Service reads it best-effort; if absent, indicator is "unknown" (not "no updates"), avoiding false negatives.

2.3 Unreliable event delivery
Publish events: at-least-once (SQS + DLQ). Worker is idempotent keyed on (templateId, fromVersion, toVersion, changeId).

ChangeRecord publishing: idempotent upsert by changeId.

Fan-out: idempotent enqueue keyed on (engagementId, changeId) — a Set add is naturally idempotent.

If the Template Change Service crashes mid-publish, the publish hook is replayed; content-hash change-ids make replay safe.

Ordering: version publishes for the same template are ordered by version sequence; cross-template order is irrelevant. We use a per-template FIFO (SQS FIFO or Kinesis partition by templateId).

2.4 Observability and SLOs
SLI	SLO
Publish → change records emitted	p99 < 10 s
Change record → pending indicator visible	p99 < 30 s (matches "within seconds")
Pending indicator read latency	p99 < 200 ms
Decision write durability	99.99%
Fan-out job success (per change)	99.9% within 24 h
Metrics: fan-out queue depth, per-engagement evaluation latency, DLQ size, summary generation failures, decision-log append rate. Alarms on DLQ > 0 and on pending-indicator staleness > 5 min.

2.5 Failure recovery
Worker: exponential backoff with jitter; DLQ after N attempts; replay tool.

State store: append-only decision log is the source of truth; the materialized EngagementUpdateState is derived and can be rebuilt by replaying decisions + template change graph.

Summary generation: if LLM fails, fall back to a deterministic templated summary (never block the indicator).

3. Scale, Cost, and Operations — Arithmetic
Given: 4,000 firms, 800,000 active engagements, 40 products, ~1 publish/week/product ⇒ ~40 publishes/week ≈ 5.7/day.

3.1 Fan-out volume
Per publish, affected engagements = engagements on that template (not all 800k). With 40 products and 800k engagements, average ~20,000 engagements/template. Skew: largest firm ~40,000 engagements, so a single publish on a popular template can touch tens of thousands of engagements.

Worst case: 40,000 engagements × 1 publish = 40,000 downstream evaluations per publish.

At ~1 min per downstream call and "limited capacity owned by another team," we must not call the ~1 min engagement load in the fan-out path. The fan-out worker enqueues a lightweight evaluation that reads the state store and template change graph — no engagement load. The ~1 min load is only used when the user opens the engagement, or for lazy repair.

This is the central cost/correctness decision: the fan-out never rehydrates engagements. It only writes to the state store using (engagementId, templateId, version) metadata it already has from the engagement creation/registration hook.

3.2 Storage
ChangeRecords: 40 products × ~52 publishes/yr × ~5 changes = ~10,400 change records/yr. Trivial.

EngagementUpdateState: 800k rows × ~200 bytes = ~160 MB. Trivial.

Decision log: append-only; ~800k decisions/yr worst case × ~150 bytes = ~120 MB/yr. Trivial.

Summaries: ~10,400/yr × ~2 KB = ~20 MB/yr. Trivial.

3.3 Compute
Fan-out: 40 publishes/week × up to 40k engagements = 1.6M enqueues/week ≈ 2.6/sec average, bursty. A small fleet (2–4 pods) handles this.

Downstream ~1 min calls: only on user open, not on fan-out. If we naively called downstream on every fan-out, 40k × 1 min = 40,000 min ≈ 667 hours per publish — infeasible. Explicitly avoided.

3.4 LLM inference
Summaries generated once per ChangeRecord, not per engagement. ~10,400/yr ⇒ ~200/week, ~29/day. At ~2k tokens in/out, this is negligible (< $1/day at typical pricing).

Do not generate per-engagement summaries. If per-engagement tailoring is needed, compose from shared ChangeRecord summaries + a deterministic template — no extra inference.

Cost is dominated by the shared summary generation, which is bounded by change volume, not engagement volume. This is what keeps us under $X.

3.5 Cost summary (symbolic)
Let c_infer = cost per summary, c_compute = hourly pod cost.

Inference: 10,400 × c_infer / yr.

Compute: 4 pods × 730 h × c_compute.

Storage: negligible (< $10/mo).
The design's cost is decoupled from engagement count for everything except the state-store row count — which is why it scales.

4. Human-Readable Summaries (Generation & Evaluation)
4.1 Generation
Input: structured JSON diff between two template versions (we have a reliable differ).

Pipeline: structured diff → normalized change units (group by template section, dedupe, order) → LLM prompt with strict schema → humanSummary.

Schema-constrained output (JSON with title, bullets[], severity, section). No free-form prose at the top level, so it is machine-checkable.

Deterministic fallback if LLM fails or output fails schema validation: templated summary from the structured diff.

Versioned prompts and models. Every ChangeRecord stores summaryPromptVersion, modelId, generatedAt, and the raw structured diff. This is what makes a March summary defensible in November.

4.2 Evaluation
Regression suite: golden set of diffs with human-approved summaries; run on every prompt/model change. Score with BLEU/ROUGE and a rubric-based LLM judge, plus human spot-checks.

Schema validation and faithfulness check: a second pass verifies every claim in the summary is supported by the structured diff (groundedness). Unsupported claims ⇒ reject and fallback.

Human-in-the-loop for high-severity changes before they are shown.

Audit trail: because the raw diff, prompt version, model id, and output are stored immutably, a regulator in November can see exactly what the user saw in March and how it was produced.

Trust boundary: the LLM summarizes; it never decides apply/decline, never mutates state, and never invents change content. All numbers and section references are traceable to the structured diff.

5. Key Tradeoffs, Assumptions, and the Requirement I'd Challenge
Tradeoffs
Shared change graph vs. per-engagement computation: we accept a shared, non-confidential derived store to avoid per-engagement inference/compute. Confidentiality is preserved by keeping engagement state separate and regional.

No engagement load in fan-out: we sacrifice "perfect freshness" of per-engagement state for feasibility. Lazy repair on open closes the gap.

Content-hash change-ids vs. version numbers: more complex graph handling, but the only correct model for branches/withdrawals.

LLM summaries shared, not per-engagement: cheaper, but less tailored. Mitigated by composition at read time.

The requirement I would challenge
"Real time — within seconds of a template being published." Seconds is achievable for the indicator (state store write), but the human-readable summary depends on an LLM call and, for high-severity changes, human review. I would challenge whether the summary must be within seconds, or whether the indicator can be within seconds and the summary within minutes/hours. If true seconds-level summaries are required, we must pre-generate summaries at diff time and accept the LLM latency/cost on the critical path, with a deterministic fallback shown first.

Riskiest part of the design
The backfill + state-store correctness for existing engagements under branches/withdrawals. Getting declinedChangeIds wrong is a correctness and audit problem, and it is invisible until a regulator or a user notices. This is where I would invest the most testing, invariants, and reconciliation.

Deliberately left out
Actual application of template content to engagements (out of scope).

Multi-region failover of the shared change service (can be added; not required by residency, since template content is not firm-specific).

UI/UX details of the indicator.

Cost modeling for $X specifics (kept symbolic).

6. Data Residency
Template change graph + summaries: shared, non-confidential — can live in a single region.

EngagementUpdateState + Decision Log: region-pinned (EU, Canada, US). Fan-out workers run per-region; queues are regional; no engagement state crosses regions.

The fan-out worker receives (engagementId, templateId, version, region) and routes to the regional state store. It never reads engagement content.