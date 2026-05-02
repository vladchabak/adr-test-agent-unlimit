# ADR 0005: V2 API — Order State Machine and Cancellation Flow (PLAT-034)

**Date**: 2026-05-02  
**Status**: Proposed  
**Services Affected**: order-service, payment-service  
**ADR ID**: 0005  
**Related MRs**: order-service MR !3, payment-service MR !3  

## Context

The V1 order model has a flat, limited status set (`CREATED`, `PAYMENT_INITIATED`, `PAYMENT_FAILED`) with no defined lifecycle after payment. The system cannot represent the real-world order progression through fulfilment, nor does it support cancellation or refunds.

PLAT-034 introduces a V2 API that adds:
- A full order lifecycle with a formal state machine
- Order cancellation triggered by the consumer
- Refund initiation to payment-service when an order is cancelled

A key constraint: V1 API must continue to work unchanged during and after the V2 rollout.

## Decision

Introduce a parallel `/api/v2/orders` endpoint set alongside the existing `/api/v1` (unchanged). The V2 order lifecycle is governed by a formal state machine with the following transitions:

```
CREATED → CONFIRMED → PAID → SHIPPED → DELIVERED
CREATED → CANCELLED
CONFIRMED → CANCELLED
```

**State machine implementation** (`OrderWorkflowService`):
- Transitions stored as a static `Map<OrderStatus, Set<OrderStatus>>` (not a HashSet recreated per call)
- Invalid transitions return an explicit error (HTTP 409)
- All transition logic centralised in `OrderWorkflowService` — controllers do not contain transition logic

**Cancellation flow** (cross-service):
1. Consumer calls `POST /api/v2/orders/{id}/cancel`
2. `OrderWorkflowService` validates the current status allows cancellation
3. If payment exists for the order, `PaymentClient.requestRefund()` is called first (payment-service MR !3)
4. Only if refund succeeds (or no payment exists) does the order status transition to `CANCELLED`
5. If the refund call fails, the order remains in its current status and an error is returned — no partial state

The method `transitionOrderStatus()` is annotated `@Transactional`; the external refund call happens before the transaction commits so a refund failure causes a full rollback.

## Consequences

**Positive**:
- Order lifecycle is explicit, enforced, and auditable
- V1 and V2 APIs coexist — no breaking change for existing consumers
- Cancellation is safe: refund-first guarantees no cancelled orders without a corresponding refund attempt
- State machine is data-driven (static map) — easy to extend

**Negative**:
- Two controller versions to maintain (`OrderController` and `OrderControllerV2`)
- The V2 state machine uses `OrderStatus` enum values not present in V1, which may cause confusion at the DB level if both APIs share the same table
- Cancellation is synchronous — if payment-service is slow, the cancel endpoint will be slow
- No retry or compensation if refund fails after cancel has been initiated

**Neutral**:
- V2 endpoints are additive (`/confirm`, `/cancel` implemented first; `PAID`/`SHIPPED`/`DELIVERED` transitions come in a follow-up MR)

## Cross-Service Impact

**New cross-service contract** (order-service → payment-service):

Request: `POST /api/v2/payments/{paymentId}/refund`
```json
{
  "paymentId": <Long>,
  "reason": <String>
}
```

Response: `{ "id": <Long>, "paymentId": <Long>, "status": "COMPLETED" }`

**Naming alignment required**: `RefundRequest` DTO is named identically in both services but has different fields (`orderId` in order-service vs `paymentId` in payment-service). Must be renamed to `OrderRefundRequest` and `PaymentRefundRequest` respectively before merge to avoid integration confusion.

**Deployment order**: payment-service MR !3 must be deployed before order-service MR !3. order-service depends on the new `/api/v2/payments/{id}/refund` endpoint existing in payment-service.

## Alternatives Considered

- **Extend V1 statuses in place**: Add the new states to the existing V1 API. Rejected — V1 consumers would be broken by unexpected status values and the existing simple payment-status model cannot represent the full lifecycle.
- **Event-driven state transitions**: Emit events to a broker on state change, letting the state machine advance asynchronously. Rejected for this iteration — no event infrastructure exists; deferred.
- **Saga pattern for cancellation**: Distributed saga with compensating transactions for the cancel+refund flow. Correct for true microservices, but operationally complex. The current synchronous approach is acceptable at this scale; saga is a future upgrade path.

## Related Decisions

- ADR 0001: Microservices architecture
- ADR 0002: Synchronous REST inter-service communication
- ADR 0003: Resilient order creation with payment fallback
- ADR 0006: Refund processing and V2 payment workflow (payment-service side of PLAT-034)

## Open Issues

- `cancelOrder` controller method currently accepts `OrderStatusTransitionRequest` but ignores `request.status()` — must be simplified to `CancelOrderRequest(String reason)` before merge (flagged in MR !3 review)
- Remaining V2 lifecycle transitions (PAID → SHIPPED → DELIVERED) are deferred to a follow-up MR
- `OrderWorkflowService.transitionOrderStatus()` must be `@Transactional` with refund call outside the transaction boundary
