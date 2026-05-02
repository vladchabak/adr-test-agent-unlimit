# Architecture Decision Log

This document tracks all architecturally significant changes across our microservices ecosystem.

| # | Date | Service | Change Type | Summary | ADR |
|---|------|---------|-------------|---------|-----|
| 10 | 2026-05-02 | payment-service | Refactoring | PAY-087: PaymentValidator extracted to dedicated component; interface introduced for future composable validator implementations (open MR) | — |
| 9 | 2026-05-02 | payment-service | Feature | PAY-095: WebSocket endpoint added at /ws/payment-status for real-time payment notifications; at-most-once delivery; per-payment subscriptions (open MR) | [ADR 0007](adr/0007-websocket-payment-status-notifications.md) |
| 8 | 2026-05-02 | payment-service | API | PLAT-034: V2 payment API with refund processing, idempotency guarantee, and atomic refund+payment-status update (open MR) | [ADR 0006](adr/0006-refund-processing-v2-payment-workflow.md) |
| 7 | 2026-05-02 | order-service | API | PLAT-034: V2 order API with state machine (CREATED→CONFIRMED→PAID→SHIPPED→DELIVERED), cancellation, and refund integration (open MR) | [ADR 0005](adr/0005-v2-order-state-machine-and-cancellation.md) |
| 6 | 2026-05-02 | order-service | Feature | ORD-158: New GET /api/orders/statistics endpoint returning total count, revenue, average value, and orders-by-status breakdown (open MR) | — |
| 5 | 2026-05-02 | order-service | Cosmetic | ORD-142: Logging typo fixes and variable renaming; MR also includes Maven→Gradle migration that should have been a separate change | — |
| 4 | 2026-05-02 | order-service, payment-service | Database | H2 in-memory database chosen for both services; schema managed by Hibernate DDL auto | [ADR 0004](adr/0004-h2-in-memory-database.md) |
| 3 | 2026-05-02 | order-service | Resilience | Orders always succeed even when payment fails; PAYMENT_FAILED status used as fallback | [ADR 0003](adr/0003-resilient-order-creation-with-payment-fallback.md) |
| 2 | 2026-05-02 | order-service, payment-service | Integration | Synchronous REST via RestTemplate chosen for inter-service communication; known status contract mismatch (SUCCESS vs COMPLETED) | [ADR 0002](adr/0002-synchronous-rest-inter-service-communication.md) |
| 1 | 2026-05-02 | order-service, payment-service | Architecture | System split into two independent Spring Boot microservices (order-service on 8080, payment-service on 8081) | [ADR 0001](adr/0001-microservices-architecture.md) |

<!-- New entries should be added above this line -->
