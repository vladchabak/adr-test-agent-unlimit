# PR Guidance — payment-service MR !2 · PAY-095

**Branch**: `feature/PAY-095-websocket-payment-notifications`  
**Architectural assessment**: ⚠️ Mergeable but ships as dead code — track PAY-103  
**ADR required**: ✅ Created — see ADR 0007  

---

## Architectural Note for Reviewers

Adding WebSocket-based push notifications is a sound architectural choice for reducing polling load and improving status-update latency. The per-payment subscription model (rather than broadcast) correctly addresses the data-privacy concern raised in review.

The MR can be merged and deployed without breaking any existing functionality because:
1. No existing REST endpoints are changed
2. The WebSocket endpoint is additive
3. `PaymentService` does not yet call `PaymentNotificationService` — the pipeline is inert

However, merging infrastructure that is intentionally not wired up has a real cost: it adds surface area to the codebase, can mislead developers about what's live, and creates technical debt if PAY-103 is deprioritised.

### Critical Gap — Notification Pipeline is Dead Code
`PaymentNotificationService.notifyPaymentStatusUpdate()` is defined but never called. Payment status changes happen, but no WebSocket message is ever sent. The feature as shipped does nothing.

**Recommendation**: File PAY-103 before this MR merges, attach a direct link in the MR description, and add a prominent `// TODO PAY-103: wire this call in PaymentService.createPayment()` comment at the call site so it cannot be overlooked.

### Security — No Auth on WebSocket Endpoint
Any process that can reach port 8081 can connect to `/ws/payment-status` and subscribe to any payment's status updates. In a production environment this is a data leakage risk.

This is out of scope for PAY-095, but must be tracked. Add a `// FIXME: add auth before any non-local deployment` in `WebSocketConfig` alongside the CORS restriction.

### Infrastructure Note — Horizontal Scaling
WebSocket sessions are held in-memory in `PaymentStatusWebSocketHandler`. A load-balanced deployment with multiple payment-service instances will have each instance maintaining its own session map — a client connected to instance A will not receive updates that go through instance B.

This is acceptable while payment-service is a single instance. Document it as a known limitation and track it as a prerequisite for any scaling work.

### Items Agreed in MR Review
- Restrict `setAllowedOrigins` to `localhost:3000`, `localhost:8080` (not `"*"`) — confirmed
- Use Jackson `ObjectMapper` instead of `String.format` for JSON — confirmed
- Replace `System.err` with SLF4J logger in `PaymentNotificationService` — confirmed
- Add per-payment subscription filtering (not broadcast) — confirmed
- Add integration test with `MockWebSocketSession` — required before merge

---

## Merge Readiness

| Check | Status |
|-------|--------|
| Cross-service impact | None (payment-service internal) |
| Deployment dependency | None |
| Dead code gap | ⚠️ PAY-103 must be filed before merge |
| Security — auth | ⚠️ Track as follow-up; FIXME comment required |
| Security — CORS | ⚠️ Restrict from `"*"` to known origins (agreed) |
| Scaling limitation | ⚠️ Document as known limitation |
| ADR | ✅ ADR 0007 |
