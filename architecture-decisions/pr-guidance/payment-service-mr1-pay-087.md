# PR Guidance — payment-service MR !1 · PAY-087

**Branch**: `refactor/PAY-087-extract-validation`  
**Architectural assessment**: ✅ Approved with minor items  
**ADR required**: No (internal refactoring, no API or protocol change)  

---

## Architectural Note for Reviewers

Extracting validation to a dedicated `PaymentValidator` component is a sound architectural move. It separates business rule enforcement from service orchestration and makes the validation logic independently testable.

### Recommended Improvement — Interface Now

`PaymentValidator` is a stateless `@Component` with two checks. The MR review flagged that PAY-112 will add card-format validation. Introducing a `PaymentValidator` interface now (with the current implementation as `DefaultPaymentValidator`) would allow:
- Composing validators without modifying `PaymentService`
- Swapping or decorating validators in tests
- A natural extension point for PAY-112

This is not a blocker, but the incremental cost to add the interface at creation time is minimal versus retrofitting it later.

### Required Items (agreed in MR review)
- Add `null` check for `orderId` before the `<= 0` comparison — a null `Long` will throw `NullPointerException` on auto-unbox (inconsistent with `amount` check which already handles null)
- Add `@ControllerAdvice` or `@ExceptionHandler` to map `IllegalArgumentException` → HTTP 400; without it the validation produces a 500 response
- Add unit tests for `PaymentValidator` covering: valid request, zero amount, negative orderId, null amount, null orderId
- Fix inline BigDecimal import to a top-level `import java.math.BigDecimal`

### Merge Order Dependency
**Merge PAY-087 before PLAT-034 (payment-service MR !3).** Both MRs modify `PaymentService`. Merging in reverse order will cause conflicts and risks losing the validator injection.

---

## Merge Readiness

| Check | Status |
|-------|--------|
| Cross-service impact | None |
| Deployment dependency | None — but must merge before PLAT-034 |
| Blocker — null check on orderId | ⚠️ Fix required (NPE risk) |
| Blocker — exception handler | ⚠️ Fix required (500 vs 400) |
| Blocker — tests | ⚠️ Required per acceptance criteria |
| ADR | Not required |
