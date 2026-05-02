# ADR 0001: Microservices Architecture — Two Separate Services

**Date**: 2026-05-02  
**Status**: Accepted  
**Services Affected**: order-service, payment-service  
**ADR ID**: 0001  

## Context

The system needs to handle order management and payment processing. The core question was whether to build a single monolithic application or separate the two concerns into independent deployable services.

Payment processing and order management have different:
- Scaling requirements (payment calls may spike independently)
- Failure domains (payment provider outages should not bring down order browsing)
- Development lifecycles (payment integrations change independently of order workflows)

## Decision

Implement order management and payment processing as two independent Spring Boot microservices:
- **order-service** — owns the order lifecycle, persists orders, orchestrates payment initiation
- **payment-service** — owns payment processing, persists payment records, exposes a payment API

Each service has its own independent database, codebase, and deployment unit.

## Consequences

**Positive**:
- Services can be scaled independently based on load
- Failure in payment-service does not crash order-service (resilience boundary)
- Teams can develop and deploy each service independently
- Each service owns its own data model without shared schema coupling

**Negative**:
- Distributed system complexity: network calls between services can fail, timeout, or return partial results
- Data consistency is eventual rather than transactional — an order can be saved while its corresponding payment is lost
- Two separate deployments, configurations, and build pipelines to manage

**Neutral**:
- Each service runs on a separate port (order-service: 8080, payment-service: 8081)
- Each service maintains its own H2 in-memory database

## Cross-Service Impact

- order-service is the **orchestrator**: it creates the order, then synchronously calls payment-service
- payment-service is a **pure provider**: it has no knowledge of order-service and can serve any caller
- There is a one-way dependency: order-service → payment-service (not circular)

## Alternatives Considered

- **Monolith**: Single Spring Boot application with both order and payment logic. Simpler to develop and deploy, but prevents independent scaling and creates a single point of failure. Rejected in favor of resilience and team autonomy.
- **Event-Driven Architecture**: Services communicate via message broker (Kafka, RabbitMQ). Fully decoupled, but adds infrastructure complexity for the current scale. Can be adopted later (see ADR 0002).

## Related Decisions

- ADR 0002: Synchronous REST communication between services
- ADR 0003: Resilient order creation with payment fallback

## Notes

Both services currently use H2 in-memory databases. For production this would need to be replaced with persistent databases (see ADR 0004).
