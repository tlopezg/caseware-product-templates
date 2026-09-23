# Diagram 1 — End-to-end flow

```
 Template Publish ──▶ SNS/SQS ──▶ Template Change Service ──▶ ChangeRecords
                                                              │
                                                              ▼
                                              Fan-Out Worker (Part 2)
                                                              │
                              ┌───────────────────────────────┼───────────────────────────────┐
                              ▼                               ▼                               ▼
                       EU State Store                 CA State Store                  US State Store
                       + Decision Log                 + Decision Log                  + Decision Log
                              │                               │                               │
                              └──────────────┬────────────────┴───────────────┬───────────────┘
                                             ▼                                ▼
                                    Engagement Service (read indicator)   User opens engagement
                                                                          (~1 min load; lazy repair)
```