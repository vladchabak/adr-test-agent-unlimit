# ADR 0002: Synchronous REST Communication Between Services

**Date**: 2026-05-02  
**Status**: Accepted  
**Services Affected**: order-service, payment-service  
**ADR ID**: 0002  

## Context

With two separate services (see ADR 0001), a communication mechanism is needed for order-service to initiate payments via payment-service. Options include synchronous HTTP calls, async messaging (Kafka, RabbitMQ), or gRPC.

The system needed a simple, debuggable, and low-infrastructure integration path for the current stage of development.

## Decision

order-service calls payment-service synchronously over HTTP REST using Spring's `RestTemplate`.

**Implemented as**:
- `PaymentClient` class in order-service (`com.example.orderservice.client.PaymentClient`)
- Sends `POST /api/payments` with `{ orderId, amount }` payload
- Payment service URL is externalized to config: `payment.service.url` in `application.yml` (default: `http://localhost:8081`)
- RestTemplate configured with 3s connect / 5s read timeout (added in code-review hardening, 2026-05-02)
- No retries or circuit breaker configured at this time

## Consequences

**Positive**:
- Simple to implement, test, and debug — standard HTTP
- No additional infrastructure (no message broker to deploy)
- Request/response is synchronous — order-service immediately knows the payment outcome

**Negative**:
- order-service is blocked waiting for payment-service to respond
- If payment-service is slow, order creation latency increases directly
- No retry logic — a transient network error causes a permanent payment failure for that order
- Tight runtime coupling: both services must be running for the full create-order flow to work

**Neutral**:
- RestTemplate is the synchronous HTTP client used (not WebClient/reactive)
- No service discovery or load balancing — URL is hardcoded in config

## Cross-Service Impact

**Contract (order-service → payment-service)**:

Request (`POST /api/payments`):
```json
{
  "orderId": <Long>,
  "amount": <BigDecimal>
}
```

Response (HTTP 201):
```json
{
  "id": <Long>,
  "orderId": <Long>,
  "status": <String>
}
```

~~⚠️ **Known Contract Inconsistency**: order-service checks `status == "SUCCESS"` to mark an order as `PAYMENT_INITIATED`, but payment-service always returns `status = "COMPLETED"`. As a result, all orders currently end up with status `PAYMENT_FAILED` even when payment processing succeeds.~~

✅ **Resolved 2026-05-02**: `PaymentService.createPayment()` now sets status to `PaymentStatus.SUCCESS` (via enum, serialised as `"SUCCESS"`). order-service correctly receives `"SUCCESS"` and transitions orders to `PAYMENT_INITIATED`. Both services now use `PaymentStatus` enum to prevent future string drift.

## Alternatives Considered

- **Async Messaging (Kafka/RabbitMQ)**: Fully decoupled, fault-tolerant, naturally resilient to service downtime. Adds infrastructure complexity (broker deployment, consumer groups, offset management). Preferred for production scale but deferred.
- **gRPC**: Strongly typed, efficient binary protocol. Adds Protobuf schema management and more complex tooling. Not warranted at current scale.
- **WebClient (reactive)**: Non-blocking HTTP client. No reactive stack in place; adds complexity without clear benefit in a blocking app.

## Related Decisions

- ADR 0001: Microservices architecture
- ADR 0003: Resilient order creation with payment fallback

## Notes

The hardcoded `http://localhost:8081` URL works for local development but will need service discovery or environment-specific config for multi-environment deployment.
