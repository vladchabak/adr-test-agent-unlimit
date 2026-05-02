# Architecture Decision Log

This document tracks all architecturally significant changes across our microservices ecosystem.

| # | Date | Service | Change Type | Summary | ADR |
|---|------|---------|-------------|---------|-----|
| 4 | 2026-05-02 | order-service, payment-service | Database | H2 in-memory database chosen for both services; schema managed by Hibernate DDL auto | [ADR 0004](adr/0004-h2-in-memory-database.md) |
| 3 | 2026-05-02 | order-service | Resilience | Orders always succeed even when payment fails; PAYMENT_FAILED status used as fallback | [ADR 0003](adr/0003-resilient-order-creation-with-payment-fallback.md) |
| 2 | 2026-05-02 | order-service, payment-service | Integration | Synchronous REST via RestTemplate chosen for inter-service communication; known status contract mismatch (SUCCESS vs COMPLETED) | [ADR 0002](adr/0002-synchronous-rest-inter-service-communication.md) |
| 1 | 2026-05-02 | order-service, payment-service | Architecture | System split into two independent Spring Boot microservices (order-service on 8080, payment-service on 8081) | [ADR 0001](adr/0001-microservices-architecture.md) |

<!-- New entries should be added above this line -->
