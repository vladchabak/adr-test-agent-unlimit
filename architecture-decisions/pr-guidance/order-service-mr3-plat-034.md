# PR Guidance — order-service MR !3 · PLAT-034

**Branch**: `feature/PLAT-034-api-v2-workflow-rework`  
**Architectural assessment**: 🚫 Blocked — 3 required fixes + deployment dependency  
**ADR required**: ✅ Created — see ADR 0005  

---

## Architectural Note for Reviewers

This MR is the order-service side of a cross-service feature. It **cannot be deployed** until payment-service PLAT-034 (`feature/PLAT-034-api-v2-workflow-rework`) is deployed first.

---

## Blockers — Must Fix Before Merge

### 1 · DTO naming conflict with payment-service (CROSS-SERVICE)
`RefundRequest` in order-service has `(Long orderId, String reason)`.  
`RefundRequest` in payment-service has `(Long paymentId, String reason)`.

**Same class name, different schemas.** If both end up in shared documentation, Swagger, or are ever accidentally exchanged, the mismatch will cause silent data corruption or deserialization failures.

**Fix**: Rename order-service's DTO to `OrderRefundRequest`. Coordinate with the payment-service team, who must rename theirs to `PaymentRefundRequest` in the same release window.

---

### 2 · `cancelOrder` accepts and silently ignores `status` field in request body
```java
// current code in OrderControllerV2
public ResponseEntity<?> cancelOrder(@PathVariable Long id,
                                      @RequestBody OrderStatusTransitionRequest request) {
    orderWorkflowService.transitionOrderStatus(id, "CANCELLED"); // ignores request.status()
```

A caller can send `{ "status": "CONFIRMED" }` and the order will still be cancelled. This is a misleading contract that will confuse API consumers and cannot be documented coherently.

**Fix**: Replace with `CancelOrderRequest(String reason)`. Remove the `status` field entirely from the cancel endpoint.

---

### 3 · `transitionOrderStatus()` must be `@Transactional` with correct boundary
The method reads an order, validates the transition, updates status, saves, and calls `PaymentClient.requestRefund()`. The refund call is an external HTTP call and **cannot be part of the DB transaction** (network calls don't roll back).

The correct pattern:
```java
@Transactional
public void transitionOrderStatus(Long orderId, OrderStatus target) {
    // DB reads and writes here — inside transaction
    Order order = ...;
    order.setStatus(target);
    orderRepository.save(order);
    // Transaction commits here — THEN call external service
}
// After transaction:
paymentClient.requestRefund(...); // outside @Transactional scope
```

If `@Transactional` wraps the refund call, a refund failure will roll back the status update — which may or may not be the desired behaviour. Confirm the intended rollback semantics with the team.

---

## Deployment Coordination

This MR **must not be deployed** before payment-service PLAT-034 is live. The cancel flow calls `POST /api/v2/payments/{id}/refund`, which does not exist until payment-service MR !3 is deployed.

**Safe deploy order**:
1. Deploy payment-service PLAT-034
2. Smoke-test the refund endpoint: `POST /api/v2/payments/1/refund`
3. Deploy order-service PLAT-034
4. Smoke-test cancel: `POST /api/v2/orders/1/cancel`

---

## Cross-Service Contract

order-service calls payment-service with:
```
POST /api/v2/payments/{paymentId}/refund
{ "paymentId": <Long>, "reason": <String> }
```

Expected response:
```json
{ "id": <Long>, "paymentId": <Long>, "status": "COMPLETED" }
```

order-service must check `status == "COMPLETED"` before transitioning to `CANCELLED`. Any other response must leave the order in its current state and return an error to the caller.

---

## Merge Readiness

| Check | Status |
|-------|--------|
| Cross-service impact | ✅ Documented in ADR 0005 |
| Deployment dependency | 🚫 payment-service PLAT-034 must deploy first |
| Blocker B1 | 🚫 DTO naming conflict (`RefundRequest`) must be resolved |
| Blocker B3 | 🚫 `cancelOrder` body must be simplified to `CancelOrderRequest` |
| Blocker — `@Transactional` | 🚫 Transaction boundary must be confirmed |
| ADR | ✅ ADR 0005 |
