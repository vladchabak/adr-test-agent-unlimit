# ADR Test Agent - Microservices Ecosystem

A modern microservices architecture with an AI-powered Architecture Decision Agent system for managing technical decisions and architectural governance.

## 📋 Overview

This repository contains:
- **order-service**: REST API for order management (port 8080)
- **payment-service**: REST API for payment processing (port 8081)
- **Architecture Decision Log**: Centralized decision tracking
- **Architecture Decision Agent**: Autonomous agent that reviews pull requests and manages ADRs

## 🏗️ Architecture

### Service Architecture Pattern

Both services follow a layered architecture:

```
┌─────────────────────────────────────┐
│         REST Controller             │  HTTP Layer
├─────────────────────────────────────┤
│          Service Layer              │  Business Logic
├─────────────────────────────────────┤
│       Repository (JPA)              │  Data Access
├─────────────────────────────────────┤
│      Entity Models & DTOs           │  Domain
├─────────────────────────────────────┤
│         H2 In-Memory DB             │  Persistence
└─────────────────────────────────────┘
```

### Service Interaction

```
Client
  │
  ├──→ POST /api/orders ────────────────────┐
  │                                         │
  │    OrderService                         │
  │  ┌──────────────────────────────────┐   │
  │  │ 1. Save order to DB              │   │
  │  │ 2. Call payment-service          │   │
  │  │ 3. Update order status           │   │
  │  │ 4. Return OrderResponse          │   │
  │  └──────────────────────────────────┘   │
  │         │                                │
  │         ├──→ POST /api/payments ────────→ PaymentService
  │         │                                │   ┌────────────────┐
  │         │                                │   │ Process payment│
  │         │                                │   │ Save to DB     │
  │         │                                │   │ Return status  │
  │         │                                │   └────────────────┘
  │         └─────← PaymentResponse ←────────┘
  │
  └─────← OrderResponse ←────────────────────┘
```

## 🚀 Getting Started

### Prerequisites
- Java 21
- Gradle 8.5+ (wrapper included)
- Git

### Running the Services

Start in separate terminals:

**Terminal 1: Payment Service** (no external dependencies)
```bash
cd payment-service
gradle bootRun
# Service available at http://localhost:8081
# H2 Console: http://localhost:8081/h2-console (dev profile only)
```

**Terminal 2: Order Service** (calls payment-service)
```bash
cd order-service
gradle bootRun
# Service available at http://localhost:8080
# H2 Console: http://localhost:8080/h2-console (dev profile only)
```

### Running with Dev Profile

To enable H2 console and automatic schema updates:

```bash
gradle bootRun --args='--spring.profiles.active=dev'
```

### Running Tests

```bash
# Order Service Tests
cd order-service
gradle test

# Payment Service Tests
cd payment-service
gradle test

# Both Services
cd order-service && gradle test && cd ../payment-service && gradle test
```

## 📡 API Endpoints

### Order Service (8080)

**Create Order**
```http
POST /api/orders
Content-Type: application/json

{
  "customerName": "John Doe",
  "productName": "Laptop",
  "quantity": 1,
  "totalPrice": 999.99
}

Response: 201 Created
{
  "id": 1,
  "customerName": "John Doe",
  "productName": "Laptop",
  "quantity": 1,
  "totalPrice": 999.99,
  "status": "PAYMENT_INITIATED",
  "createdAt": "2026-05-03T10:34:05"
}
```

**Get Order**
```http
GET /api/orders/{id}
Response: 200 OK
```

**List Orders (Paginated)**
```http
GET /api/orders?page=0&size=20&sort=createdAt,desc
Response: 200 OK with paginated results
```

### Payment Service (8081)

**Create Payment**
```http
POST /api/payments
Content-Type: application/json

{
  "orderId": 1,
  "amount": 999.99
}

Response: 201 Created
{
  "id": 1,
  "orderId": 1,
  "amount": 999.99,
  "status": "SUCCESS",
  "paymentMethod": "CARD",
  "createdAt": "2026-05-03T10:34:05"
}
```

**Get Payment**
```http
GET /api/payments/{id}
Response: 200 OK
```

**Get Payment by Order ID**
```http
GET /api/payments/order/{orderId}
Response: 200 OK
```

## 🤖 Architecture Decision Agent

### What It Does

The Architecture Decision Agent is an autonomous system that:

1. **Reviews Pull Requests** - Analyzes code changes for architectural consistency
2. **Manages ADRs** - Creates and updates Architecture Decision Records
3. **Enforces Patterns** - Ensures adherence to established architectural patterns
4. **Documents Decisions** - Maintains a centralized decision log
5. **Provides Feedback** - Comments on PRs with architectural concerns and recommendations

### Architecture Decision Records (ADRs)

Located in `architecture-decisions/adr/` directory, each ADR documents:
- **Status**: Proposed, Accepted, Deprecated, Superseded
- **Context**: Why the decision was needed
- **Decision**: What was decided
- **Consequences**: Positive and negative impacts
- **Alternatives**: Other options considered

### Architecture Decision Log (ADL)

Central log at `architecture-decisions/ARCHITECTURE_DECISION_LOG.md` provides:
- Quick reference to all decisions
- Decision status and dates
- Links to detailed ADRs
- Decision summary and rationale

### How to Use the Agent

1. **Create a Pull Request** with architectural changes
2. **Agent automatically reviews** the code and architecture
3. **Agent comments** with feedback and suggestions
4. **Discussion happens in PR** conversation thread
5. **Decision updates** are reflected in ADL and ADRs

### Example ADR

```
# ADR-001: Use Spring Boot for REST APIs

## Status
Accepted

## Context
We need to build scalable, maintainable microservices for order and payment processing.

## Decision
Use Spring Boot 3.2+ with Spring Data JPA for REST APIs and data access.

## Consequences
✅ Large ecosystem and community support
✅ Reduces development time
✅ Built-in security and testing support
❌ Added JVM overhead
❌ Learning curve for new team members

## Alternatives
- Quarkus: Lower memory footprint but smaller ecosystem
- Micronaut: Better performance but newer framework
```

## 🛠️ Technology Stack

### Core Framework
- **Spring Boot 3.2.3** - REST APIs and dependency injection
- **Spring Data JPA** - Object-relational mapping
- **Hibernate 6.6** - ORM implementation

### Database
- **H2 Database** - In-memory relational database (development)
- **Flyway/Liquibase Ready** - Migration tools configured for production

### Testing
- **JUnit 5** - Unit testing framework
- **Mockito** - Mocking library
- **Spring Test** - Spring integration testing
- **@WebMvcTest** - Controller slice tests
- **@DataJpaTest** - Repository tests

### Build & Dependency Management
- **Gradle 8.5** - Build automation
- **Dependency Management Plugin** - Version consistency across Spring ecosystem

## 📊 Database Schema

### Order Service

**orders table**
```sql
CREATE TABLE orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_name VARCHAR(255) NOT NULL,
  product_name VARCHAR(255) NOT NULL,
  quantity INT NOT NULL,
  total_price DECIMAL(19,2) NOT NULL,
  status VARCHAR(50) NOT NULL,
  created_at TIMESTAMP NOT NULL
);
```

### Payment Service

**payments table**
```sql
CREATE TABLE payments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  amount DECIMAL(19,2) NOT NULL,
  status VARCHAR(50) NOT NULL,
  payment_method VARCHAR(50) NOT NULL,
  created_at TIMESTAMP NOT NULL
);
```

## 🔒 Key Architectural Decisions

### 1. Layered Architecture
- **Why**: Separation of concerns, testability, maintainability
- **How**: Controller → Service → Repository → Database

### 2. RESTful API Design
- **Why**: Stateless, scalable, standard HTTP semantics
- **How**: Resource-oriented endpoints, proper HTTP methods and status codes

### 3. Constructor Injection
- **Why**: No field injection, easier testing, explicit dependencies
- **How**: All Spring beans use constructor-based dependency injection

### 4. DTO Separation
- **Why**: Decouples API contracts from domain models
- **How**: Request/Response records, separate from JPA entities

### 5. Enum-Based Status Fields
- **Why**: Type safety, prevents invalid states, easier refactoring
- **How**: @Enumerated(EnumType.STRING) on JPA entities

### 6. Transaction Boundaries
- **Why**: Ensures data consistency, prevents connection pool exhaustion
- **How**: @Transactional on write operations, readOnly=true for queries

### 7. Exception Handling
- **Why**: Consistent error responses, client-friendly feedback
- **How**: @RestControllerAdvice with global exception handlers

### 8. Resilience to External Service Failures
- **Why**: Payment-service being down shouldn't prevent order creation
- **How**: Order saves before calling payment-service with fallback status

## 🧪 Testing Strategy

### Unit Tests
- Service layer tests with Mockito
- No database interaction
- Fast execution (~100ms per test)

### Integration Tests
- Controller slice tests (@WebMvcTest)
- Repository tests (@DataJpaTest)
- Full Spring context tests (@SpringBootTest)

### Test Coverage
- Happy path scenarios
- Error conditions
- Edge cases (null responses, timeouts)
- Validation error handling

## ⚙️ Configuration

### application.yml (Development)
- H2 in-memory database: `jdbc:h2:mem:{service}db`
- Hibernate DDL: `update` (auto-creates schema)
- JPA logging: SQL formatting enabled
- Default ports: order-service 8080, payment-service 8081

### application-dev.yml (Profile-Specific)
- H2 console enabled for manual queries
- Hibernate DDL set to `update` for development flexibility

### Environment Variables
Configuration can be overridden via environment variables:
```bash
PAYMENT_SERVICE_URL=http://payment-svc:8081
SPRING_PROFILES_ACTIVE=dev
```

## 📝 Code Review Findings

This codebase includes bug fixes addressing:
- ✅ Database connection pool exhaustion from long-held transactions
- ✅ Missing error handling for validation errors
- ✅ Overly broad exception catching
- ✅ Type-unsafe status fields
- ✅ Improper stereotype annotations
- ✅ Missing transactional boundaries
- ✅ Unbounded pagination requests

See `code-review.md` for detailed analysis and fixes applied.

## 🔄 Deployment

### Development
```bash
gradle bootRun
# or with profile
gradle bootRun --args='--spring.profiles.active=dev'
```

### Production
```bash
gradle build
java -jar build/libs/order-service-*.jar \
  --spring.profiles.active=prod \
  --server.port=8080
```

### Docker
```dockerfile
FROM eclipse-temurin:21-jdk
COPY build/libs/order-service-*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
```

## 📚 Project Structure

```
adr-test-agent-unlimit/
├── order-service/
│   ├── src/main/java/com/example/orderservice/
│   │   ├── controller/          # REST endpoints
│   │   ├── service/             # Business logic
│   │   ├── client/              # External service calls
│   │   ├── repository/          # Data access
│   │   ├── model/               # JPA entities
│   │   ├── dto/                 # Request/response objects
│   │   └── exception/           # Error handling
│   ├── src/test/java/           # Unit and integration tests
│   ├── src/main/resources/
│   │   ├── application.yml      # Main configuration
│   │   └── application-dev.yml  # Dev profile
│   └── build.gradle
├── payment-service/             # Same structure as order-service
├── architecture-decisions/
│   ├── ARCHITECTURE_DECISION_LOG.md
│   └── adr/                     # Detailed ADR documents
├── code-review.md               # Comprehensive code review
└── README.md                    # This file
```

## 🤝 Contributing

When making architectural changes:

1. **Discuss** the change in an issue or PR
2. **Align with existing patterns** - constructor injection, DTOs, transactions
3. **Write tests** - unit tests at minimum, integration tests preferred
4. **Update ADL** - document significant decisions
5. **Request review** - PRs are reviewed by the Architecture Decision Agent

## 🚦 CI/CD

Both services include GitLab CI configuration (`.gitlab-ci.yml`):
- **build stage**: Compiles code with Gradle
- **test stage**: Runs full test suite
- **Caching**: Gradle dependencies cached between runs

## 📖 Further Reading

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [Architecture Decision Records (ADR)](https://adr.github.io/)
- [Microservices Patterns](https://microservices.io/patterns/index.html)

## 📄 License

This project is provided as-is for educational and testing purposes.

---

**Last Updated**: 2026-05-03  
**Maintainer**: Architecture Decision Agent System
