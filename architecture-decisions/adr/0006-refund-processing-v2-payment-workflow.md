# ADR 0006: Refund Processing and V2 Payment Workflow (PLAT-034)

**Date**: 2026-05-02  
**Status**: Proposed  
**Services Affected**: payment-service, order-service  
**ADR ID**: 0006  
**Related MRs**: payment-service MR !3, order-service MR !3  

## Context

V1 payment-service has no concept of refunds. Payments are created and marked `SUCCESS` — there is no reverse flow. PLAT-034 introduces order cancellation in order-service (ADR 0005) which requires a corresponding refund capability in payment-service.

Key requirements:
- Refunds must be idempotent — duplicate calls must not create duplicate refund records
- The refund and payment status update must be atomic — no orphaned refund records or stuck payment statuses
- The refund endpoint must be reachable by order-service as part of the cancellation flow

## Decision

Introduce a `Refund` entity and a `RefundService` in payment-service, exposed via a new `/api/v2/payments/{id}/refund` endpoint.

**Refund entity**:
- Fields: `id`, `paymentId` (FK reference, not a hard DB constraint), `reason`, `status`, `createdAt`
- `status` follows the lifecycle: `PENDING` → `PROCESSING` → `COMPLETED` / `FAILED`
- `createdAt` set via `@PrePersist`
- Default status set via field initializer only (not duplicated in constructor)
- Unique constraint on `paymentId` column to enforce single-refund-per-payment at DB level

**Idempotency**:
- `RefundService.processRefund()` checks for an existing refund by `paymentId` before creating a new one
- Duplicate call returns `409 Conflict` with a clear error message
- DB-level unique constraint on `paymentId` as a second safeguard

**Atomicity**:
- `RefundService.processRefund()` is `@Transactional`
- Refund record creation and payment status update (to `REFUNDED`) happen in the same transaction
- If either write fails, both roll back

**API contract** (called by order-service):

```
POST /api/v2/payments/{paymentId}/refund
Content-Type: application/json

{
  "paymentId": <Long>,
  "reason": <String>
}
```

Response (HTTP 201):
```json
{
  "id": <Long>,
  "paymentId": <Long>,
  "status": "COMPLETED"
}
```

Note: the response body currently omits the refund lifecycle (`PENDING` → `PROCESSING`). Intermediate states should be modelled even if the processing is synchronous, to make the contract extensible when a real payment gateway is wired in.

## Consequences

**Positive**:
- Refund and payment status update are atomic — no inconsistent states
- Idempotency prevents double-refund bugs, critical for financial correctness
- V2 payment API is additive — V1 `/api/payments` endpoints unchanged

**Negative**:
- `paymentId` in the `refunds` table is not enforced as a foreign key (services have separate databases) — referential integrity is maintained only by application logic
- Refund processing is still synchronous and simulated — real payment gateway integration will require revisiting the state transitions
- No notification to order-service when refund processing completes asynchronously (not a problem while processing is synchronous, but will be once a real gateway is used)

**Neutral**:
- `RefundRequest` DTO must be renamed `PaymentRefundRequest` to distinguish from `OrderRefundRequest` in order-service (naming alignment required per PLAT-034 review)

## Cross-Service Impact

This ADR is the payment-service side of the PLAT-034 cancellation flow documented in ADR 0005.

**Deployment dependency**: payment-service MR !3 must be deployed **before** order-service MR !3. The new `/api/v2/payments/{id}/refund` endpoint must exist before order-service's cancel flow attempts to call it.

**Contract shared between services**:
- order-service sends `PaymentRefundRequest { paymentId, reason }` to payment-service
- payment-service returns a `RefundResponse { id, paymentId, status }`
- If `status` is not `COMPLETED`, order-service cancellation must be blocked

## Alternatives Considered

- **Soft-delete payment with status REFUNDED, no separate entity**: Simpler — update payment status to `REFUNDED` and store refund metadata on the payment. Rejected — a separate `Refund` entity allows multiple refund attempts to be tracked and is a cleaner domain model for future partial-refund support.
- **Async refund via message queue**: Payment gateway refunds are inherently async in real systems. Rejected for this iteration — no broker infrastructure; deferred until real gateway integration.
- **Saga with compensating transaction**: Full distributed saga for cancel + refund + notification. Correct for high-scale systems but operationally complex. Deferred.

## Related Decisions

- ADR 0001: Microservices architecture
- ADR 0002: Synchronous REST inter-service communication
- ADR 0005: V2 order state machine and cancellation flow (order-service side of PLAT-034)

## Open Issues

- `POST /api/v2/payments/{id}/refund` currently accepts `@RequestBody(required = false)` — must be changed to required body before merge (flagged in MR !3 review)
- Refund lifecycle intermediate states (`PENDING` → `PROCESSING`) should be modelled in the response even if processing is currently synchronous
- `@Transactional` on `processRefund()` still needs to be added (flagged in MR review, agreed by Mr. Coder)
