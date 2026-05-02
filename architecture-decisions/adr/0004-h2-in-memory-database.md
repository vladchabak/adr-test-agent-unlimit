# ADR 0004: H2 In-Memory Database for Persistence

**Date**: 2026-05-02  
**Status**: Accepted  
**Services Affected**: order-service, payment-service  
**ADR ID**: 0004  

## Context

Both services require a persistence layer for storing orders and payments. The choice of database affects developer setup time, test complexity, and production readiness.

For the current development/demo phase, the priority is zero-setup local development and fast test execution.

## Decision

Both services use H2 in-memory database with JPA/Hibernate (DDL auto: `update`).

**Configuration**:
- order-service: `jdbc:h2:mem:testdb`, port 8080, H2 console enabled
- payment-service: `jdbc:h2:mem:paymentdb`, port 8081, H2 console enabled
- Schema managed by Hibernate DDL auto (`update`) — no migration scripts
- No connection pool configuration (Spring Boot default HikariCP settings)

## Consequences

**Positive**:
- Zero infrastructure setup — no database to install, configure, or run
- Tests are fully isolated — each test run starts with a clean in-memory database
- H2 console available for quick inspection during development (`/h2-console`)
- Fast application startup

**Negative**:
- **All data is lost on restart** — not suitable for production
- H2 SQL dialect differs from production databases (PostgreSQL, MySQL) — queries may pass locally but fail in production
- `ddl-auto: update` is unsafe for production (can silently corrupt schema on deployment)
- No schema migration history — impossible to track schema evolution over time
- No connection pool tuning — defaults may not handle production load

**Neutral**:
- Each service has its own isolated in-memory database (no shared schema)
- H2 console is enabled in both services — should be disabled for any non-dev environment

## Cross-Service Impact

- Databases are completely independent — no cross-service joins or shared tables
- Services are linked only by the `orderId` foreign key stored in the `payments` table (not enforced by a database constraint — just a Long column)

## Alternatives Considered

- **PostgreSQL**: Production-grade database with full SQL compliance. Would require Docker or local installation for development. The right choice for production.
- **Flyway/Liquibase migrations**: Schema versioning and safe migration management. Would replace `ddl-auto: update`. Necessary before any production deployment.
- **Testcontainers**: Run a real PostgreSQL instance during tests inside Docker containers. Better test fidelity than H2 but requires Docker in the test environment.

## Related Decisions

- ADR 0001: Microservices architecture

## Notes

Before any production deployment, H2 must be replaced with a persistent database (PostgreSQL recommended) and `ddl-auto: update` must be replaced with Flyway or Liquibase migration scripts. H2 console must also be disabled.
