# ADR 0008: Code-Review Architectural Hardening

**Date**: 2026-05-02  
**Status**: Accepted  
**Services Affected**: order-service, payment-service  
**ADR ID**: 0008  

## Context

A full code review of both services (20 findings, Critical → Low) identified several structural issues that were not one-off bugs but reflected missing patterns applied consistently across the codebase. Rather than treating these as isolated fixes, the remediation established a set of architectural conventions that both services must follow going forward.

The findings and their fixes are documented in `code-review.md`. This ADR records the *patterns* established — the rules that should apply to all future code in this repository.

## Decision

The following patterns are now established and must be followed in all new and modified code.

---

### 1 — Status values must be enums, not raw strings

`OrderStatus` and `PaymentStatus` enums replace all magic string literals for status fields.

- Entities use `@Enumerated(EnumType.STRING)` — values stored as readable strings in the DB
- Service layer compares against enum values, not literals
- DTOs expose `status.name()` as a String in the API contract, not the enum type itself

**Why**: The `"SUCCESS"` vs `"COMPLETED"` contract mismatch (ADR 0002) was caused by two services independently hardcoding status strings with no shared source of truth. Enums enforce compile-time correctness within each service.

---

### 2 — API responses must use dedicated DTOs, not raw JPA entities

Controllers return response DTOs (`OrderResponse`, `PaymentResponse`), never `@Entity` classes directly.

- Entities are internal — they carry JPA annotations, lazy-load relationships, and DB concerns
- DTOs define the API contract explicitly — adding a JPA annotation does not accidentally change the API
- Mapping from entity to DTO happens in the service layer (`toResponse()` method)

**Why**: Returning JPA entities directly couples the persistence model to the API contract. Any schema change breaks the API silently.

---

### 3 — Collection endpoints must be paginated

`GET` endpoints that return unbounded lists must accept `Pageable` and return `Page<T>`.

- `GET /api/orders` returns `Page<OrderResponse>` with default Spring pagination
- Callers use `?page=0&size=20&sort=createdAt,desc` query parameters

**Why**: `findAll()` loads every row into the JVM heap. With enough records this causes OOM errors and multi-megabyte HTTP responses.

---

### 4 — `@Transactional` is required for multi-step DB writes

Any service method that performs more than one write (save + update, or save + status change) must be annotated `@Transactional`.

- `OrderService.createOrder()` — two saves (initial + status update after payment)
- `RefundService.processRefund()` (PLAT-034) — refund save + payment status update

**Why**: Without `@Transactional`, a crash between two writes leaves a partially updated record. For `createOrder()` this produced zombie `CREATED` orders; for `processRefund()` it would corrupt financial data.

Note: `@Transactional` does not cover external HTTP calls. External calls must happen *outside* the transaction boundary — commit DB state first, then call the external service.

---

### 5 — RestTemplate must have explicit timeouts

Any `RestTemplate` bean must be configured with explicit connect and read timeouts:

```java
SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
factory.setConnectTimeout(3000);  // 3s connect
factory.setReadTimeout(5000);     // 5s read
return new RestTemplate(factory);
```

**Why**: The default `RestTemplate` uses `SimpleClientHttpRequestFactory` with infinite timeouts. A hanging downstream service blocks the calling thread indefinitely and exhausts the Tomcat thread pool under load.

---

### 6 — Use SLF4J logger, not `System.err` or `System.out`

All logging must go through the SLF4J `Logger`:

```java
private static final Logger log = LoggerFactory.getLogger(MyClass.class);
log.error("Failed to do X for id {}: ", id, e);  // passes exception for stack trace
```

**Never use** `System.err.println()` or `System.out.println()` in application code.

**Why**: `System.err` bypasses the logging framework — messages do not appear in structured log aggregation, cannot be filtered by level, and the exception stack trace is lost when only `e.getMessage()` is logged.

---

### 7 — `@Value` injection must be via constructor, not field

Spring `@Value` properties must be injected through the constructor, not as field injections:

```java
// correct
public PaymentClient(RestTemplate restTemplate,
                     @Value("${payment.service.url}") String paymentServiceUrl) { ... }

// wrong
@Value("${payment.service.url}")
private String paymentServiceUrl;
```

**Why**: Field injection requires a Spring context to construct the object, making unit testing with plain `new` impossible. Constructor injection allows testing without Spring.

---

### 8 — `@PrePersist` for timestamp fields; no mutable timestamp setter

Timestamp fields (`createdAt`) must be set via `@PrePersist`, not in constructors or via setters.

```java
@PrePersist
protected void onCreate() { createdAt = LocalDateTime.now(); }
// no setCreatedAt() setter
```

**Why**: Setting `createdAt` in constructors creates inconsistency — the no-arg constructor used by JPA for hydration never sets it. Mutable setters allow `createdAt` to be accidentally overwritten after creation.

## Consequences

**Positive**:
- Consistent patterns across both services reduce the cognitive load of context-switching between codebases
- Each pattern addresses a specific class of recurring bug — enums prevent string drift, DTOs prevent model leakage, `@Transactional` prevents partial writes
- Future PRs can be reviewed against these conventions rather than rediscovering the same issues

**Negative**:
- Slightly more boilerplate (DTO mapping, constructor injection, factory for RestTemplate)
- Enum status values must be intentionally extended when new statuses are added — cannot just use an ad-hoc string

**Neutral**:
- These patterns are standard Spring Boot conventions — no new frameworks or dependencies introduced

## Cross-Service Impact

Patterns 1 (enums) and 6 (logger) have cross-service relevance: if both services use `PaymentStatus` enum values (serialised to the same strings), contract drift like the `SUCCESS`/`COMPLETED` mismatch cannot happen.

## Related Decisions

- ADR 0002: Synchronous REST communication (timeout fix, PaymentClient move)
- ADR 0003: Resilient order creation (informed @Transactional requirement)
- ADR 0006: Refund processing (@Transactional requirement directly referenced)

## Notes

The full list of individual fixes is in `code-review.md` at the project root. This ADR captures only the patterns — for the specific line-level fixes see the commit `fix: Resolve all 20 code-review findings`.
