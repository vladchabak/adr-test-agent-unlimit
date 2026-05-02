# PR Guidance — payment-service MR !3 · PLAT-034

**Branch**: `feature/PLAT-034-api-v2-workflow-rework`  
**Architectural assessment**: 🚫 Blocked — 3 required fixes before merge  
**ADR required**: ✅ Created — see ADR 0006  

---

## Architectural Note for Reviewers

This MR introduces the refund capability that order-service PLAT-034 depends on. It must be **deployed before** order-service MR !3. The implementation is mostly correct but has three issues that must be resolved before merge.

---

## Blockers — Must Fix Before Merge

### 1 · `RefundService.processRefund()` is not `@Transactional` (FINANCIAL CORRECTNESS)
This method:
1. Creates a `Refund` record (`refundRepository.save(refund)`)
2. Updates payment status to `REFUNDED` (`paymentRepository.save(payment)`)

Without `@Transactional`, a failure between step 1 and step 2 produces:
- An orphaned `Refund` record with no corresponding payment status update
- Or a payment marked `REFUNDED` with no `Refund` record

Both scenarios corrupt financial data and are invisible to the caller.

**Fix**: Add `@Transactional` to `processRefund()`.  
*(Mr. Coder agreed to add this — verify it is present in the latest push.)*

---

### 2 · DTO naming conflict with order-service (CROSS-SERVICE)
`RefundRequest` in payment-service has `(Long paymentId, String reason)`.  
`RefundRequest` in order-service has `(Long orderId, String reason)`.

**Fix**: Rename to `PaymentRefundRequest`. The order-service team must rename theirs to `OrderRefundRequest` in the same release window. Both teams must coordinate this rename — it affects the API contract documentation and any OpenAPI specs.

---

### 3 · Refund endpoint accepts optional body with fallback constructor (API CONTRACT)
```java
@PostMapping("/{id}/refund")
public ResponseEntity<?> createRefund(@PathVariable Long id,
                                       @RequestBody(required = false) RefundRequest request) {
    if (request == null) request = new RefundRequest(id, "No reason provided");
```

This creates two valid ways to call the endpoint — with or without a body. Both are accepted and produce different `RefundRequest` objects. This:
- Cannot be cleanly documented in OpenAPI/Swagger
- Hides the required `reason` field from consumers
- Makes the endpoint behave non-deterministically depending on whether a body is provided

**Fix**: Make the body required. Remove the null fallback.  
*(Mr. Coder agreed to fix this — verify it is present.)*

---

## Idempotency — Verify Before Merge
The MR thread flagged that `processRefund()` should check for an existing refund before creating a new one. Duplicate refund calls must return `409 Conflict`, not create two records.

Verify that the following check is present in `RefundService.processRefund()`:
```java
if (refundRepository.findByPaymentId(request.paymentId()).isPresent()) {
    throw new IllegalStateException("Refund already exists for payment " + request.paymentId());
}
```

Also verify: a **unique constraint on `paymentId`** exists in the `Refund` entity as a DB-level safeguard (`@Column(unique = true)`).

---

## Refund Lifecycle — Recommendation
The current implementation jumps directly from `PENDING` to `COMPLETED` without modelling `PROCESSING`. When a real payment gateway is integrated, you will need to add `PROCESSING` to the flow. Modelling it now — even if the `PROCESSING` state is transient and synchronous — makes the contract extensible and avoids a breaking change to the response schema later.

---

## Deployment Coordination

This MR **must be deployed before** order-service PLAT-034.

**Smoke test after deployment**:
```bash
curl -X POST http://localhost:8081/api/v2/payments/1/refund \
  -H "Content-Type: application/json" \
  -d '{"paymentId": 1, "reason": "customer request"}'
# Expected: HTTP 201, { "id": ..., "paymentId": 1, "status": "COMPLETED" }
```

Only after this passes should order-service PLAT-034 be deployed.

---

## Merge Readiness

| Check | Status |
|-------|--------|
| Cross-service impact | ✅ Documented in ADR 0006 |
| Deployment dependency | Must deploy before order-service PLAT-034 |
| Blocker B2 | 🚫 `@Transactional` missing on `processRefund()` |
| Blocker B1 | 🚫 DTO naming conflict (`RefundRequest`) must be resolved |
| Blocker B4 | 🚫 Request body must be required |
| Idempotency check | ⚠️ Verify idempotency check + unique DB constraint are present |
| Rebase dependency | ⚠️ Must be rebased on top of PAY-087 after that MR merges |
| ADR | ✅ ADR 0006 |
