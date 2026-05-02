# ADR Index

This file tracks all Architecture Decision Records (ADRs) created for the microservices ecosystem.

## Numbering Scheme

ADRs are numbered sequentially using 4-digit IDs: `NNNN` (e.g., 0001, 0002, 0003, etc.)

**Format**: `NNNN-short-title-kebab-case.md`

Example: `0001-rest-api-inter-service-communication.md`

## ADR Registry

| ID | Title | Status | Services | Date | File |
|---|-------|--------|----------|------|------|
| 0001 | Microservices Architecture — Two Separate Services | Accepted | order-service, payment-service | 2026-05-02 | [0001-microservices-architecture.md](0001-microservices-architecture.md) |
| 0002 | Synchronous REST Communication Between Services | Accepted | order-service, payment-service | 2026-05-02 | [0002-synchronous-rest-inter-service-communication.md](0002-synchronous-rest-inter-service-communication.md) |
| 0003 | Resilient Order Creation with Payment Fallback | Accepted | order-service | 2026-05-02 | [0003-resilient-order-creation-with-payment-fallback.md](0003-resilient-order-creation-with-payment-fallback.md) |
| 0004 | H2 In-Memory Database for Persistence | Accepted | order-service, payment-service | 2026-05-02 | [0004-h2-in-memory-database.md](0004-h2-in-memory-database.md) |

<!-- New ADRs should be added above this comment, in chronological order -->

## Status Legend

- **Proposed**: Under consideration, not yet agreed upon
- **Accepted**: Approved and in use
- **Deprecated**: No longer recommended, but historically kept for reference
- **Superseded**: Replaced by a newer ADR (note which one in the ADR file)

## Finding ADRs

1. **By Number**: Look for `NNNN-*.md` file in this directory
2. **By Service**: Check "Services" column in registry above
3. **By Status**: Filter by Status column
4. **By Topic**: See cross-references within each ADR's "Related Decisions" section

## Creating New ADRs

1. Identify that a decision is architecturally significant (see decision criteria in main README)
2. Get the **next available ADR ID** from this index
3. Copy `TEMPLATE.md` to `NNNN-your-title.md` (use kebab-case for title)
4. Fill in all sections following the template
5. Add entry to ADR Registry table above (in chronological order by date)
6. Update the Architecture Decision Log (`../ARCHITECTURE_DECISION_LOG.md`)
7. Commit to the architecture-decisions repository

## Key Principles

- **One decision per ADR**: Each ADR documents a single significant decision
- **Immutable records**: ADRs are not edited once accepted; superseded decisions link to newer ADRs
- **Cross-service awareness**: Always note impacts on other services (order-service ↔ payment-service)
- **Traceability**: Link to the MRs, commits, and issues that implement the decision
- **Team visibility**: ADRs serve as communication tool for all stakeholders
