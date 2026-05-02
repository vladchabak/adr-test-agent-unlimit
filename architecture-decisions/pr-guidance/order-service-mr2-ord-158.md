# PR Guidance — order-service MR !2 · ORD-158

**Branch**: `feature/ORD-158-order-statistics-endpoint`  
**Architectural assessment**: ⚠️ Mergeable with one coordination risk  
**ADR required**: No (new read endpoint, no protocol or model change)  

---

## Architectural Note for Reviewers

This MR adds `GET /api/orders/statistics` — a read-only aggregation endpoint with no cross-service dependency. Architecturally low-risk on its own.

### Coordination Risk with PLAT-034

The statistics endpoint groups orders by status using a **static list** of V1 statuses:
```
CREATED, PAYMENT_INITIATED, PAYMENT_FAILED
```

Once order-service PLAT-034 ships, orders will exist with these additional statuses:
```
CONFIRMED, PAID, SHIPPED, DELIVERED, CANCELLED
```

Those V2 orders will be silently excluded from the statistics breakdown. Consumers of this endpoint will see incorrect aggregation data without any error.

**Recommended action before merge**: Either extend the status list to include all `OrderStatus` enum values, or replace the static list with a dynamic query. The simplest fix is to iterate over `OrderStatus.values()` instead of hardcoding strings.

### Other Review Items (from MR thread, confirmed agreed)
- `totalRevenue.divide(...)` must use `RoundingMode.HALF_UP` with scale 2 — runtime `ArithmeticException` otherwise
- `sumAllTotalPrice()` JPQL query should use `COALESCE(SUM(...), 0)` to handle empty DB
- Tests for the happy path and empty-database edge case are required per the acceptance criteria

---

## Merge Readiness

| Check | Status |
|-------|--------|
| Cross-service impact | None directly |
| Deployment dependency | None |
| Blocker | ⚠️ Static status list will break once PLAT-034 ships — fix before or coordinate as same release |
| ADR | Not required |
