# ADR 0007: WebSocket Real-Time Payment Status Notifications (PAY-095)

**Date**: 2026-05-02  
**Status**: Proposed  
**Services Affected**: payment-service  
**ADR ID**: 0007  
**Related MRs**: payment-service MR !2  

## Context

The current payment-service only exposes request/response REST endpoints. Consumers wanting to know when a payment status changes must poll `GET /api/payments/{id}` repeatedly. This creates unnecessary load and introduces latency between the actual status change and the consumer becoming aware of it.

PAY-095 introduces a WebSocket endpoint to push payment status updates to connected clients in real time.

## Decision

Add a WebSocket endpoint to payment-service at `/ws/payment-status` using Spring WebSocket (`spring-boot-starter-websocket`).

**Architecture**:
- `WebSocketConfig` registers the handler at `/ws/payment-status`
- `PaymentStatusWebSocketHandler` manages active sessions and message dispatch
- `PaymentNotificationService` provides the `notifyPaymentStatusUpdate(paymentId, status)` method called by `PaymentService` after a payment is processed

**Subscription model** — per-payment filtering (not broadcast):
- On connect, clients send a subscribe message: `{ "action": "subscribe", "paymentId": <Long> }`
- `PaymentStatusWebSocketHandler` maintains a `Map<Long, Set<WebSocketSession>>` to track which sessions are subscribed to which paymentId
- Only sessions subscribed to a given paymentId receive its status updates
- On disconnect, sessions are removed from all subscription sets

**Message format** (server → client):
```json
{ "paymentId": <Long>, "status": "<PaymentStatus>" }
```
Serialised using Jackson `ObjectMapper` (not `String.format`) to ensure correct escaping.

**CORS / allowed origins**: restricted to known dev origins (`http://localhost:3000`, `http://localhost:8080`). Must be parameterized via `application.yml` before production (FIXME noted in config).

**Delivery guarantee**: at-most-once. WebSocket notifications are fire-and-forget — no retry on failed delivery. Clients that miss an update must fall back to polling the REST endpoint. This is documented explicitly.

## Consequences

**Positive**:
- Eliminates polling for payment status — lower latency for consumers watching payment outcomes
- Per-payment subscriptions prevent data leakage (only the subscribed client receives the update)
- Extends payment-service without changing any existing REST endpoints

**Negative**:
- Adds `spring-boot-starter-websocket` as a new infrastructure dependency to payment-service
- At-most-once delivery means clients can miss updates — requires a fallback polling strategy
- WebSocket connections are stateful — horizontal scaling requires sticky sessions or a shared session store (not addressed in this MR)
- Notification pipeline is currently wired as dead code: `PaymentNotificationService` is not called from `PaymentService` — integration is deferred to a follow-up ticket (filed as PAY-103) to avoid conflicts with PAY-087 changes to `PaymentService`
- No authentication on the WebSocket endpoint — any client can subscribe to any payment's updates once connected (privacy concern for production)

**Neutral**:
- Does not affect the REST API contract
- WebSocket endpoint is independent of the V2 workflow (PLAT-034)

## Cross-Service Impact

This change is currently **internal to payment-service only**. However, if order-service adopts WebSocket-based payment status updates instead of the current synchronous REST call, the inter-service communication model changes significantly. That would be a separate, high-impact ADR.

No immediate action required from order-service for this MR.

## Alternatives Considered

- **Server-Sent Events (SSE)**: Simpler, HTTP-based, unidirectional push. Easier to load-balance. Less infrastructure than WebSocket. Worth reconsidering if only one-way server → client push is needed.
- **Polling with long-poll**: No new protocol, works through proxies. Higher latency and server load than WebSocket. Rejected.
- **Message broker (Kafka/Redis Pub-Sub)**: Proper at-least-once delivery, scales horizontally. Adds infrastructure complexity. The right choice when scaling beyond a single payment-service instance; deferred.
- **Spring STOMP over WebSocket**: Higher-level protocol with topic subscriptions built in, better fit for broadcast + subscription patterns. Could replace the manual session map. Could be adopted in a follow-up.

## Related Decisions

- ADR 0001: Microservices architecture
- ADR 0002: Synchronous REST inter-service communication

## Open Issues

- `PaymentNotificationService.notifyPaymentStatusUpdate()` is not yet wired into `PaymentService` — dead code until follow-up ticket PAY-103 is implemented
- CORS `setAllowedOrigins` must be parameterised via `application.yml` before any non-local deployment
- No authentication/authorisation on WebSocket connections — any caller can subscribe to any payment's events
- Horizontal scaling requires sticky sessions or a distributed session store (not scoped in PAY-095)
- Retry mechanism for missed notifications deferred to PAY-103
