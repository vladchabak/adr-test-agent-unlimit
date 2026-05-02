# ADR 0003: Resilient Order Creation with Payment Fallback

**Date**: 2026-05-02  
**Status**: Accepted  
**Services Affected**: order-service, payment-service  
**ADR ID**: 0003  

## Context

When order-service synchronously calls payment-service (see ADR 0002), the payment call can fail due to:
- payment-service being down or unreachable
- Network timeout or transient error
- Unexpected exception in payment processing

The question is: should a failed payment call prevent the order from being created, or should the order persist with a degraded status?

## Decision

Order creation never fails due to a payment error. If payment-service is unavailable or returns an unexpected response, the order is still saved to the database with status `PAYMENT_FAILED`.

**Implemented in** `OrderService.createOrder()`:
1. Save the order first with status `CREATED`
2. Call `paymentClient.initiatePayment()` wrapped in try/catch
3. If payment response has `status == "SUCCESS"` → set order status to `PAYMENT_INITIATED`
4. If payment call throws any exception or returns null → set order status to `PAYMENT_FAILED`
5. Save and return the order regardless

**In `PaymentClient`**: all exceptions are caught, logged to stderr, and `null` is returned — the order-service never receives an exception from the payment layer.

## Consequences

**Positive**:
- Order creation is resilient: payment-service downtime does not cause order creation failures
- Users always receive a response from order-service (no 500 errors due to downstream failure)
- Orders with `PAYMENT_FAILED` can be retried or reconciled later

**Negative**:
- Orders exist in the database with no corresponding payment — creates data inconsistency
- No retry mechanism: a transient failure results in a permanently failed payment status
- No alerting or monitoring when `PAYMENT_FAILED` occurs
- Errors are only logged to `System.err` — no structured logging or observability
- No reconciliation process defined for `PAYMENT_FAILED` orders

**Neutral**:
- The `PAYMENT_INITIATED` state signals that payment was triggered, not that it completed
- There is no callback or webhook from payment-service to notify order-service when payment completes

## Cross-Service Impact

- order-service becomes the source of truth for order status
- payment-service has no knowledge of order failure states — it only knows about its own payment records
- A `PAYMENT_FAILED` order in order-service may or may not have a corresponding payment record in payment-service

## Alternatives Considered

- **Fail the order if payment fails**: Simpler consistency model but worse user experience and availability. Rejected — service unavailability should not break order creation.
- **Saga pattern with compensating transactions**: Full distributed saga to manage rollback if payment fails. More correct for consistency but significantly more complex. Deferred.
- **Two-Phase Commit**: Distributed transaction across both services. Operationally very complex and not suitable for microservices. Rejected.

## Related Decisions

- ADR 0001: Microservices architecture
- ADR 0002: Synchronous REST inter-service communication

## Notes

The `PAYMENT_FAILED` status requires an operational runbook or automated retry job to handle reconciliation in production. This is currently not defined.
