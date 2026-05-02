# Cross-Service Coordination Report

**Generated**: 2026-05-02  
**Scope**: All open MRs across order-service and payment-service  
**Owner**: Architecture Decision Agent

---

## Summary

There are 6 open MRs across 2 services. Three of them form a tightly coupled cross-service release (PLAT-034). The remaining three are independent and can be merged in any order.

**PLAT-034 is the highest-risk change in flight.** It introduces a new inter-service API contract, requires a strict deployment order, and has two shared DTOs with a naming conflict that will cause runtime bugs if not resolved before merge.

---

## Dependency Graph

```
Independent (can merge anytime):
  order-service   MR !1  ORD-142   [logging typos]
  order-service   MR !2  ORD-158   [statistics endpoint] ──────────────┐ Note ①
  payment-service MR !1  PAY-087   [validation extraction] ─────────┐  |
  payment-service MR !2  PAY-095   [websocket notifications]        |  |
                                                                     ↓  ↓
Coupled (strict deploy order):
  payment-service MR !3  PLAT-034  [refund + V2 payment API]  ←── must merge AFTER PAY-087
       ↓ must deploy first
  order-service   MR !3  PLAT-034  [state machine + cancel]   ←── must deploy AFTER payment MR !3
```

**Note ①**: ORD-158 hardcodes V1 statuses in its statistics endpoint. If order-service PLAT-034 merges first, the statistics will silently omit `CONFIRMED`, `PAID`, `SHIPPED`, `DELIVERED`, `CANCELLED`.

---

## Required Merge / Deploy Order

| Step | Action | Reason |
|------|--------|--------|
| 1 | Merge PAY-087 | Establishes `PaymentValidator` before PLAT-034 adds to `PaymentService` |
| 2 | Merge ORD-142, ORD-158, PAY-095 | Independent; any order |
| 3 | Merge + **deploy** payment-service PLAT-034 | The `/api/v2/payments/{id}/refund` endpoint must be live |
| 4 | Merge + **deploy** order-service PLAT-034 | `PaymentClient.requestRefund()` requires step 3 to be live |

⚠️ Deploying order-service PLAT-034 before payment-service PLAT-034 will cause all order cancellations to fail with a connection error.

---

## Shared API Contract (PLAT-034)

Both services must agree on this contract before either MR is merged:

**Request** — order-service → payment-service:
```
POST /api/v2/payments/{paymentId}/refund
Content-Type: application/json

{
  "paymentId": <Long>,   // from PaymentRefundRequest
  "reason":    <String>
}
```

**Response** — payment-service → order-service:
```json
{
  "id":        <Long>,
  "paymentId": <Long>,
  "status":    "COMPLETED" | "FAILED"
}
```

**order-service cancellation rule**: cancel proceeds only if `status == "COMPLETED"`. Any other status or network error leaves the order in its pre-cancel state.

---

## Blockers — Must Fix Before Merge

### B1 · DTO Naming Conflict (PLAT-034 — BOTH services)
Both services define a class named `RefundRequest` with different schemas:
- order-service: `RefundRequest(Long orderId, String reason)` — wraps the orderId
- payment-service: `RefundRequest(Long paymentId, String reason)` — wraps the paymentId

**Risk**: When order-service deserialises a payment-service response, or when the contract is documented in Swagger/OpenAPI, the identical name with different semantics causes confusion and integration bugs.

**Required fix**:
- order-service: rename to `OrderRefundRequest`
- payment-service: rename to `PaymentRefundRequest`

Both teams must coordinate the rename simultaneously.

---

### B2 · `@Transactional` Missing on `RefundService.processRefund()` (payment-service PLAT-034)
The method creates a refund record AND updates payment status. Without `@Transactional`, a failure between the two writes leaves an orphaned refund or a stuck payment status. This is a financial correctness bug.

**Required fix**: Add `@Transactional` to `RefundService.processRefund()` before merge.  
*(Mr. Coder agreed to fix this in MR review — must be verified in the next push.)*

---

### B3 · `cancelOrder` accepts misleading request body (order-service PLAT-034)
`POST /api/v2/orders/{id}/cancel` currently accepts `OrderStatusTransitionRequest` which has a `status` field — but the implementation hardcodes `"CANCELLED"` and ignores whatever `status` the caller sends. This is a confusing API that will mislead consumers.

**Required fix**: Replace `OrderStatusTransitionRequest` with `CancelOrderRequest(String reason)`.  
*(Mr. Coder agreed to fix this in MR review — must be verified in the next push.)*

---

### B4 · Refund body is `required = false` (payment-service PLAT-034)
`POST /api/v2/payments/{id}/refund` accepts an optional request body and falls back to constructing the request from the path variable. This creates two valid call patterns, is impossible to document cleanly, and hides contract requirements from callers.

**Required fix**: Remove the fallback; require the body.  
*(Mr. Coder agreed to fix this in MR review — must be verified in the next push.)*

---

## Risks — Should Fix Before Merge

### R1 · ORD-158 statistics hardcodes V1 statuses
`GET /api/orders/statistics` groups orders by status using a static `Map.of("CREATED", ..., "PAYMENT_INITIATED", ..., "PAYMENT_FAILED", ...)`. Once PLAT-034 ships new statuses (`CONFIRMED`, `PAID`, `SHIPPED`, `DELIVERED`, `CANCELLED`), those orders will not appear in the statistics breakdown.

**Recommendation**: Update statistics to query distinct statuses dynamically, or extend the static map in the same sprint as PLAT-034.

---

### R2 · PAY-095 WebSocket notification pipeline is dead code
`PaymentNotificationService.notifyPaymentStatusUpdate()` is not called from `PaymentService`. The WebSocket infrastructure exists but no payment status updates are broadcast. The feature is non-functional until follow-up ticket PAY-103 wires the call.

**Recommendation**: Either wire the call in this MR (avoids a second touch of `PaymentService`) or gate the MR merge on PAY-103 being filed and scoped. The dead code should not merge as-is without a tracking ticket.

---

### R3 · PAY-087 merges into `PaymentService` — conflicts with PLAT-034
PAY-087 adds `PaymentValidator` injection into `PaymentService`. PLAT-034 also modifies `PaymentService` to call `PaymentNotificationService` (when PAY-103 is done). These MRs must be merged in sequence (PAY-087 first) and PLAT-034 rebased on top to avoid conflicts.

**Recommendation**: Communicate clearly to both MR authors — do not merge PLAT-034 before PAY-087.

---

### R4 · Cancellation is synchronous — cancel endpoint latency tied to payment-service
`POST /api/v2/orders/{id}/cancel` calls `PaymentClient.requestRefund()` synchronously inside `@Transactional`. If payment-service is slow (e.g., real gateway integration later), cancel will be slow. RestTemplate has a 5s read timeout (fixed in code-review), so worst case is a 5-second cancel response.

**Recommendation**: Document the at-most-5s latency bound for the cancel endpoint. Revisit with async refund when real gateway is integrated.

---

### R5 · V2 order statuses share the same DB table as V1
`OrderStatus` enum now includes `CONFIRMED`, `PAID`, `SHIPPED`, `DELIVERED`, `CANCELLED` alongside the V1 values. Both V1 and V2 write to the same `orders` table. H2's DDL-auto will add the new enum values transparently, but a V1 query that assumes only 3 possible statuses (e.g., the ORD-158 statistics endpoint) will silently ignore V2 records.

**Recommendation**: Treat ORD-158 (R1) and PLAT-034 as a coordinated release — merge them together or update ORD-158 first.

---

## PAY-095 — Independent but Incomplete

PAY-095 (WebSocket) has no cross-service dependency and can be merged and deployed independently. However:
- It ships with dead code (R2 above)
- The allowed origins are hardcoded for dev (`localhost:3000`, `localhost:8080`) — must be parameterised before any other environment
- No auth on the WebSocket endpoint — any process that can reach port 8081 can subscribe to any payment's updates

PAY-095 is low-risk for the services, but should not be considered "complete" until PAY-103 wires the notification call.

---

## Action Items by Team

### order-service team
- [ ] **B3**: Simplify `cancelOrder` to `CancelOrderRequest(String reason)`
- [ ] **B1**: Rename `RefundRequest` → `OrderRefundRequest` (coordinate with payment-service team)
- [ ] **R1**: Decide: update ORD-158 statistics to cover V2 statuses before PLAT-034 ships
- [ ] **R5**: Confirm V1 API consumers are unaffected by new enum values in the shared table
- [ ] Rebase order-service PLAT-034 on top of main after payment-service PLAT-034 is deployed

### payment-service team
- [ ] **B2**: Add `@Transactional` to `RefundService.processRefund()`
- [ ] **B4**: Make refund request body required (remove fallback)
- [ ] **B1**: Rename `RefundRequest` → `PaymentRefundRequest` (coordinate with order-service team)
- [ ] **R2**: Wire `PaymentNotificationService` in `PaymentService` (or file PAY-103 and gate MR on it)
- [ ] **R3**: Merge PAY-087 before PLAT-034; rebase PLAT-034 on PAY-087
- [ ] Parameterise WebSocket CORS origins via `application.yml`
