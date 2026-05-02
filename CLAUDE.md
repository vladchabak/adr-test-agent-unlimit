# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

This is a microservices ecosystem consisting of:
- **order-service**: REST API for order management (port 8080)
- **payment-service**: REST API for payment processing (port 8081)
- **architecture-decisions**: Architecture Decision Log (ADL) and Records (ADRs)
- **architecture-decision-agent**: Agent system for managing architectural decisions

The order-service and payment-service are tightly coupled: when an order is created, the order-service automatically calls the payment-service to initiate payment processing.

## Common Commands

### Order Service

```bash
# From order-service/ directory
gradle clean build             # Full build
gradle clean compileJava       # Compile only
gradle bootRun                 # Run service (http://localhost:8080)
gradle test                    # Run all tests
gradle test --tests="ClassName" # Run specific test class
```

### Payment Service

```bash
# From payment-service/ directory
gradle clean build             # Full build
gradle clean compileJava       # Compile only
gradle bootRun                 # Run service (http://localhost:8081)
gradle test                    # Run all tests
gradle test --tests="ClassName" # Run specific test class
```

### Running Both Services Together

Both services use H2 in-memory databases with automatic initialization, so they can be started independently without setup. For integration testing, start payment-service first (it has no external dependencies):

```bash
# Terminal 1: Start payment service
cd payment-service
gradle bootRun

# Terminal 2: Start order service
cd order-service
gradle bootRun
```

The order-service has a timeout and fallback: if payment-service is unavailable, orders are created with status `PAYMENT_FAILED`.

### Architecture Decisions

The architecture-decisions directory tracks significant technical decisions:

```bash
# View the single-file log
cat architecture-decisions/ARCHITECTURE_DECISION_LOG.md

# Detailed ADRs are in
ls architecture-decisions/adr/
```

## Architecture & Code Structure

### Service Architecture Pattern

Both services follow a layered architecture:
- **Controller**: REST endpoints (handle HTTP, route to service layer)
- **Service**: Business logic layer (PaymentService, OrderService, PaymentClient)
- **Repository**: Spring Data JPA repositories (database abstraction)
- **Model**: JPA entity beans with @Entity annotations
- **DTO**: Request/response objects (decouples API contracts from models)
- **Config**: Spring configuration beans (AppConfig for REST templates, etc.)

### Inter-Service Communication

**OrderService → PaymentService**: The order-service contains a `PaymentClient` (com.example.orderservice.service.PaymentClient) that makes REST calls to the payment-service. This client:
- Calls `POST /api/payments` to create payments
- Handles timeouts gracefully (orders proceed with PAYMENT_FAILED status if payment-service is down)
- Uses Spring's RestTemplate

**API Contract**: When an order is created, order-service sends:
```json
{
  "orderId": <order-id>,
  "amount": <total-price>
}
```

The payment-service responds with:
```json
{
  "id": <payment-id>,
  "orderId": <order-id>,
  "status": "SUCCESS" | "FAILED"
}
```

### Database Schema

Both services use H2 in-memory databases initialized via Hibernate DDL auto (set to `update` mode):
- **order_service**: `order` table (Order JPA entity)
- **payment_service**: `payment` table (Payment JPA entity)

Access H2 console:
- Payment Service: `http://localhost:8081/h2-console` (JDBC: `jdbc:h2:mem:paymentdb`, user: `sa`, password: blank)

### Configuration

Configuration is in `src/main/resources/application.yml` for each service:
- Server port, database URL, JPA/Hibernate settings
- In order-service: `payment.service.url` specifies where payment-service is located (defaults to `http://localhost:8081`)

## Build System & Testing

### Gradle & Dependencies

- **Gradle 8.5** with wrapper (use `gradle` command, wrapper is checked in)
- **Java 21** as source compatibility
- Spring Boot 3.2.3 with dependency management plugin
- Key dependencies: spring-boot-starter-web, spring-boot-starter-data-jpa, h2, spring-boot-starter-test
- Tests use **JUnit 5** (JUnitPlatform)

### Testing Strategy

- Unit tests are in `src/test/java/com/example/<service>/`
- No mocking of database layer in provided tests; use in-memory H2
- Tests inherit from Spring Boot test base classes (e.g., @SpringBootTest)

### CI/CD Pipeline (GitLab)

Both services have `.gitlab-ci.yml`:
- **build stage**: `gradle clean compileJava` with gradle:8.5-jdk21 image
- **test stage**: `gradle test` with same image
- Cache: `.gradle/caches` and `.gradle/wrapper`

## Key Implementation Notes

- **No package scanning complexity**: Each service is a single module with straightforward Spring component discovery
- **Payment service failures don't block orders**: The order-service has built-in resilience (timeout + fallback status)
- **Stateless services**: Both use stateless REST APIs with in-memory database state (no session affinity needed)
- **Entity IDs**: Auto-generated via JPA (likely Long/auto-increment)
