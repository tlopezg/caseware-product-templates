# Diagram 2 — Change graph with decline semantics

```
   v4 ──▶ v5 ──▶ v6 (withdrawn)
            │
            └──▶ v7 (contains v5's changes + new ones)

  Engagement E: applied={c1,c2}, declined={c5a}, pending={}
  Publish v7 (changes = {c5a, c7a, c7b}):
    c5a ∈ declined  → not re-offered
    c7a, c7b ∉ applied/declined → pending={c7a, c7b}
  User sees: "2 new changes pending" (v5's change stays declined)
```