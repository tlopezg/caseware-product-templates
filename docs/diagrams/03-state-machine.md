# Diagram 3 — State machine per engagement/change

```
  (none) ──publish──▶ PENDING ──user applies──▶ APPLIED
                        │
                        └──user declines──▶ DECLINED
                        │
                        └──version withdrawn──▶ PENDING removed
                                                 (APPLIED / DECLINED retained)
```