# Code Review — main branch

**Reviewed:** order-service, payment-service  
**Branch:** main  
**Date:** 2026-05-03

---

## What Is in Good Shape

- Constructor injection used throughout — no field injection, easy to unit test
- `AppConfig` sets connect (3 s) and read (5 s) timeouts on `RestTemplate` — no unbounded blocking
- `@Valid` is wired on both controller request bodies and validation constraints are declared on DTOs
- Both `OrderStatus` and `PaymentStatus` stored as `@Enumerated(EnumType.STRING)` — safe across schema refactors
- `@PrePersist` sets `createdAt` with no public setter — creation timestamp is immutable
- `PaymentClient` logs the full exception (passes `e` as a Throwable argument to SLF4J) — stack traces are not lost
- `PaymentRepository.findByOrderId()` is a derived query — no raw JPQL that can drift from the schema
- Test suite covers happy path, failure path, null-response path at the service layer; controller slice tests cover 400/404/201/200 via `@WebMvcTest`

---

## Critical

### 1. `@Transactional` holds a DB connection across the entire HTTP call to payment-service

**File:** `order-service/src/main/java/com/example/orderservice/service/OrderService.java:27–47`

```java
@Transactional                                  // ← transaction opens, DB connection acquired
public OrderResponse createOrder(...) {
    Order savedOrder = orderRepository.save(order);   // INSERT executes
    var paymentResponse = paymentClient.initiatePayment(...);  // HTTP call — up to 5 s
    savedOrder.setStatus(...);
    return toResponse(orderRepository.save(savedOrder));       // UPDATE executes
}                                                              // transaction commits, connection released
```

The DB connection is held for the full duration of the HTTP call. With HikariCP's default pool of 10 and a 5-second read timeout, 10 concurrent slow-payment requests fully exhaust the pool. All subsequent requests block waiting for a connection — orders, reads, everything.

**Fix:** Remove `@Transactional` from `createOrder`. Save once before the HTTP call, update status in a separate short operation after:

```java
// No @Transactional — two short transactions, no long-held connection
public OrderResponse createOrder(CreateOrderRequest request) {
    Order saved = saveNewOrder(request);                                   // short tx
    var resp = paymentClient.initiatePayment(saved.getId(), saved.getTotalPrice());
    OrderStatus status = (resp != null && "SUCCESS".equals(resp.status()))
            ? OrderStatus.PAYMENT_INITIATED : OrderStatus.PAYMENT_FAILED;
    return applyStatus(saved.getId(), status);                             // short tx
}

@Transactional
private Order saveNewOrder(CreateOrderRequest req) {
    return orderRepository.save(new Order(req.customerName(), req.productName(),
            req.quantity(), req.totalPrice()));
}

@Transactional
private OrderResponse applyStatus(Long id, OrderStatus status) {
    Order o = orderRepository.findById(id).orElseThrow();
    o.setStatus(status);
    return toResponse(o);
}
```

---

### 2. No `@RestControllerAdvice` — Spring's default error format, no field-level detail on 400s

Neither service defines an exception handler. When `@Valid` rejects a request, Spring Boot returns a generic 400 body:

```json
{"timestamp":"...","status":400,"error":"Bad Request","path":"/api/orders"}
```

No indication of which field failed or why. Unhandled `RuntimeException` (DB constraint violation, NPE) returns a 500 with Spring's default `/error` body — no correlation ID, no safe error message.

Add a `@RestControllerAdvice` extending `ResponseEntityExceptionHandler` to both services:

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage,
                        (a, b) -> a));
        return ResponseEntity.badRequest().body(Map.of("status", 400, "errors", fieldErrors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return ResponseEntity.internalServerError()
                .body(Map.of("status", 500, "error", "An unexpected error occurred"));
    }
}
```

Extending `ResponseEntityExceptionHandler` is important: without it, a catch-all `@ExceptionHandler(Exception.class)` also intercepts Spring's `NoHandlerFoundException` and `HttpRequestMethodNotAllowedException`, turning 404s and 405s into 500s.

---

## High

### 3. `PaymentClient` catches `Exception` — masks programming errors

**File:** `order-service/src/main/java/com/example/orderservice/client/PaymentClient.java:30`

```java
} catch (Exception e) {
    log.error("Failed to initiate payment for order {}: ", orderId, e);
    return null;
}
```

This swallows every `Throwable` including `NullPointerException`, `IllegalArgumentException`, and coding mistakes. A bug in request construction silently returns `null` and the order is created with `PAYMENT_FAILED`, with nothing in the call site to distinguish a network failure from a bug.

**Fix:** Catch `RestClientException` (the Spring hierarchy for HTTP/IO failures) and let unexpected exceptions propagate:

```java
} catch (RestClientException e) {
    log.error("Failed to initiate payment for order {}: ", orderId, e);
    return null;
}
```

---

### 4. `PaymentStatus.PENDING` is set in the constructor but never persisted

**Files:** `payment-service/src/main/java/com/example/paymentservice/model/Payment.java:42`, `PaymentService.java:24`

```java
// Payment constructor:
this.status = PaymentStatus.PENDING;  // set here

// PaymentService.createPayment():
payment.setStatus(success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);  // immediately overwritten
return paymentRepository.save(payment);  // PENDING never reaches the DB
```

`PENDING` exists in the enum and is assigned in the constructor but is immediately overwritten before any `save()`. The enum value signals an async two-phase design (save as PENDING, process, update status), but the code does not implement that. Future developers will reason incorrectly about what states a payment can be in.

Either implement the PENDING state correctly, or remove it from the enum and the constructor.

---

### 5. No max page size — `getAllOrders` accepts unbounded `?size=N`

**File:** `order-service/src/main/java/com/example/orderservice/controller/OrderController.java:35–38`

```java
@GetMapping
public ResponseEntity<Page<OrderResponse>> getAllOrders(Pageable pageable) {
```

`Pageable` is resolved from query params. A request with `?size=1000000` causes a full table scan, loads every row into the JVM heap, and serializes it all. Add `@PageableDefault` with an explicit sort, and configure `spring.data.web.pageable.max-page-size` to cap it globally:

```java
// application.yml
spring:
  data:
    web:
      pageable:
        max-page-size: 100

// controller:
@GetMapping
public ResponseEntity<Page<OrderResponse>> getAllOrders(
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return ResponseEntity.ok(orderService.getAllOrders(pageable));
}
```

---

## Medium

### 6. Redundant second `save()` within `@Transactional` — extra SQL UPDATE on every order

**File:** `order-service/src/main/java/com/example/orderservice/service/OrderService.java:36, 46`

```java
Order savedOrder = orderRepository.save(order);   // entity becomes JPA-managed
// ...
return toResponse(orderRepository.save(savedOrder));  // redundant — dirty check flushes automatically
```

After the first `save()` inside a `@Transactional` method, `savedOrder` is a managed entity. Hibernate's dirty-checking mechanism will flush any mutations (including `setStatus`) at commit time without an explicit `save()`. The second call emits a superfluous `UPDATE` on every create. Remove it — or better yet, remove `@Transactional` entirely per finding #1.

---

### 7. No `@Transactional(readOnly = true)` on query methods

**Files:** `OrderService.getOrder()`, `OrderService.getAllOrders()`, `PaymentService.getPayment()`, `PaymentService.getPaymentByOrderId()`

None of the read methods declare `@Transactional(readOnly = true)`. This skips dirty checking and flush optimizations that Hibernate applies for read-only transactions, and prevents accidental writes:

```java
@Transactional(readOnly = true)
public Optional<OrderResponse> getOrder(Long id) {
    return orderRepository.findById(id).map(this::toResponse);
}
```

---

### 8. `PaymentService.createPayment()` is not `@Transactional`

**File:** `payment-service/src/main/java/com/example/paymentservice/service/PaymentService.java:20`

Currently a single `save()` call, so the missing annotation has no observable impact. But if a second repository operation is added later (e.g., writing to a payment audit log), the two operations will each run in their own transaction, leaving the DB inconsistent on failure.

```java
@Transactional
public Payment createPayment(CreatePaymentRequest request) { ... }
```

---

### 9. `PaymentClient` annotated `@Service` — should be `@Component`

**File:** `order-service/src/main/java/com/example/orderservice/client/PaymentClient.java:13`

`@Service` signals a bean containing business logic. `PaymentClient` is an HTTP infrastructure adapter with no business logic. The correct stereotype is `@Component`. This is a semantic issue, not functional, but it misleads readers.

---

### 10. `PaymentController.toResponse()` belongs in the service layer

**File:** `payment-service/src/main/java/com/example/paymentservice/controller/PaymentController.java:42–51`

`PaymentController` does its own DTO mapping while `OrderService` handles mapping internally. The inconsistency means `PaymentController` must know the `Payment` entity's field structure, tying the controller to the domain model. Move mapping to `PaymentService` (return `PaymentResponse` directly as `OrderService` does) and make `PaymentController` a thin HTTP adapter.

---

### 11. `OrderResponse.status` is `String` — loses type safety at the boundary

**File:** `order-service/src/main/java/com/example/orderservice/dto/OrderResponse.java:7`

```java
public record OrderResponse(... String status ...) {}
// built via:
order.getStatus().name()
```

Returning `OrderStatus` directly and letting Jackson serialize it gives API consumers the enum name while keeping the type visible in Java. If a test asserts `"PAYMENT_INITIATED"` and the enum is renamed, the string comparison silently diverges:

```java
public record OrderResponse(... OrderStatus status ...) {}
// built via:
order.getStatus()
```

Jackson serializes `OrderStatus.PAYMENT_INITIATED` as `"PAYMENT_INITIATED"` by default, so the wire format is unchanged.

---

### 12. `order-service` `PaymentResponse` missing `@JsonIgnoreProperties(ignoreUnknown = true)`

**File:** `order-service/src/main/java/com/example/orderservice/dto/PaymentResponse.java`

The order-service `PaymentResponse` captures only `id`, `orderId`, `status` — but the payment-service response includes `amount`, `paymentMethod`, and `createdAt` too. Jackson ignores unknown fields by default, so this works today. But if `spring.jackson.deserialization.fail-on-unknown-properties=true` is ever enabled globally, or a `@JsonProperty` mapping is configured centrally, deserialization will silently break:

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentResponse(Long id, Long orderId, String status) {}
```

---

### 13. `paymentMethod` hardcoded as the string `"CARD"` — no constant or type

**File:** `payment-service/src/main/java/com/example/paymentservice/model/Payment.java:43`

```java
this.paymentMethod = "CARD";
```

A bare string with no constant definition, no enum, and no enforcement. When additional payment methods are added, the string will be duplicated. Extract to a constant in the class, or introduce a `PaymentMethod` enum:

```java
private static final String DEFAULT_PAYMENT_METHOD = "CARD";

public Payment(Long orderId, BigDecimal amount) {
    this.paymentMethod = DEFAULT_PAYMENT_METHOD;
    ...
}
```

---

## Low / Code Quality

### 14. H2 console enabled without a Spring profile guard

**Files:** Both `application.yml`

```yaml
spring:
  h2:
    console:
      enabled: true  # TODO comment present
```

H2's web console provides unrestricted SQL access to the in-memory database with no authentication. Gate it behind a `dev` profile so it cannot accidentally be left on in a deployed environment:

```yaml
# application-dev.yml only
spring:
  h2:
    console:
      enabled: true
```

---

### 15. `ddl-auto: update` is production-unsafe

**Files:** Both `application.yml`

`ddl-auto: update` silently alters the live schema on startup — renamed columns become new nullable columns, dropped columns persist with stale data. Both services have TODO comments acknowledging this. Replace with `validate` backed by a migration tool (Flyway or Liquibase) before any non-dev deployment.

---

### 16. `@InjectMocks` in `OrderServiceTest` — silently fails on constructor changes

**File:** `order-service/src/test/java/com/example/orderservice/service/OrderServiceTest.java:38`

`@InjectMocks` uses reflection. If `OrderService` gains a new constructor dependency, Mockito may silently construct the object incorrectly and the test continues to pass against a misconfigured subject. Use an explicit constructor call in `@BeforeEach`:

```java
@BeforeEach
void setUp() {
    orderService = new OrderService(orderRepository, paymentClient);
}
```

---

### 17. Both saves in `OrderServiceTest` return the same mock reference

**File:** `order-service/src/test/java/com/example/orderservice/service/OrderServiceTest.java:53`

```java
when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
```

`savedOrder` is returned for both the initial save and the status-update save. The service mutates `savedOrder.status` between the two calls, so by the time the assertion runs, the shared reference already has the expected status. The test passes for the right reason today, but would continue passing even if the second save were removed (since the mutation happened on the shared object). Use separate return values or `ArgumentCaptor` to assert on the actual state passed to each `save()`.

---

### 18. `@PrePersist` is untested — `createdAt` is silently `null` in all unit tests

**Files:** Both `*ServiceTest.java`

`@PrePersist` is a JPA lifecycle callback that does not fire in unit tests (no `EntityManager`). Mocked `save()` returns the constructed entity with `createdAt = null`. No assertion in any test checks `createdAt`, so the `@PrePersist` contract is untested entirely.

Add at least one `@DataJpaTest` or `@SpringBootTest` test that persists an entity and verifies `createdAt` is populated.

---

### 19. No integration tests for the full HTTP → JPA stack

The test suite has unit tests (Mockito) and controller slice tests (`@WebMvcTest`) but no test exercises the full path: HTTP request → controller → service → JPA → H2 → response. This leaves untested:

- Actual JSON serialization of enums and `LocalDateTime`
- `@PrePersist` lifecycle callbacks
- Pagination and sort behavior against a real schema
- The payment-service call in the context of a real order creation

Add at least one `@SpringBootTest(webEnvironment = RANDOM_PORT)` test per service covering the create-and-retrieve round trip.

---

### 20. RestTemplate is in maintenance mode — `RestClient` available in Boot 3.2

**Files:** `order-service/src/main/java/com/example/orderservice/client/PaymentClient.java`, `config/AppConfig.java`

Spring 6.1 (Boot 3.2) introduced `RestClient` as the modern synchronous HTTP client. `RestTemplate` still works but receives no new features. For new work, prefer `RestClient`:

```java
RestClient restClient = RestClient.builder()
        .baseUrl(paymentServiceUrl)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();

// call:
PaymentResponse resp = restClient.post()
        .uri("/api/payments")
        .body(new PaymentRequest(orderId, amount))
        .retrieve()
        .body(PaymentResponse.class);
```

---

## Summary

| # | Severity | File | Issue |
|---|----------|------|-------|
| 1 | Critical | `OrderService.java:27` | `@Transactional` holds DB connection during 5 s HTTP call — connection pool exhaustion |
| 2 | Critical | Both services (missing) | No `@RestControllerAdvice` — 400 field errors invisible, Spring routing exceptions become 500 |
| 3 | High | `PaymentClient.java:30` | `catch (Exception e)` too broad — masks programming errors, should be `RestClientException` |
| 4 | High | `Payment.java:42`, `PaymentService.java:24` | `PENDING` status set but immediately overwritten, never reaches the DB |
| 5 | High | `OrderController.java:35` | No max page size — clients can fetch unbounded rows |
| 6 | Medium | `OrderService.java:46` | Redundant `save()` on managed entity — emits extra UPDATE every create |
| 7 | Medium | `OrderService.java`, `PaymentService.java` | Missing `@Transactional(readOnly = true)` on all query methods |
| 8 | Medium | `PaymentService.java:20` | `createPayment()` not `@Transactional` — unsafe when more DB ops are added |
| 9 | Medium | `PaymentClient.java:13` | `@Service` on HTTP adapter — should be `@Component` |
| 10 | Medium | `PaymentController.java:42` | DTO mapping in controller — belongs in service layer |
| 11 | Medium | `OrderResponse.java:7` | `status` typed as `String` instead of `OrderStatus` enum |
| 12 | Medium | `order-service/.../PaymentResponse.java` | Missing `@JsonIgnoreProperties(ignoreUnknown = true)` — fragile if Jackson config tightens |
| 13 | Medium | `Payment.java:43` | `paymentMethod` is bare `"CARD"` string — no constant or enum |
| 14 | Low | Both `application.yml` | H2 console enabled without dev-profile guard |
| 15 | Low | Both `application.yml` | `ddl-auto: update` is production-unsafe (TODOs present) |
| 16 | Low | `OrderServiceTest.java:38` | `@InjectMocks` — silently misconstructs subject on constructor changes |
| 17 | Low | `OrderServiceTest.java:53` | Both `save()` calls return the same mock reference — assertion reasons about shared mutation |
| 18 | Low | Both `*ServiceTest.java` | `@PrePersist` never fires in unit tests — `createdAt` silently null |
| 19 | Low | Both services | No integration tests — full HTTP → JPA → response path is untested |
| 20 | Low | `PaymentClient.java`, `AppConfig.java` | `RestTemplate` in maintenance mode — `RestClient` preferred in Boot 3.2 |
