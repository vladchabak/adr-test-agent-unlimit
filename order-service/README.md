# Order Service

A Spring Boot microservice for managing orders that communicates with a Payment Service via REST.

## Overview

Order Service is a simple REST API that allows you to create and retrieve orders. When an order is created, it automatically initiates a payment request to the Payment Service running on port 8081.

## Features

- Create new orders
- Retrieve a specific order by ID
- Retrieve all orders
- Automatic payment initiation upon order creation
- H2 in-memory database for persistence
- JPA/Hibernate ORM

## Getting Started

### Prerequisites

- Java 21
- Gradle 8.5+

### Build

```bash
gradle clean compileJava
```

### Run

```bash
gradle bootRun
```

The service will start on `http://localhost:8080`

### Test

```bash
gradle test
```

## API Endpoints

### Create Order
```
POST /api/orders
Content-Type: application/json

{
  "customerName": "John Doe",
  "productName": "Laptop",
  "quantity": 1,
  "totalPrice": 999.99
}
```

### Get Order by ID
```
GET /api/orders/{id}
```

### Get All Orders
```
GET /api/orders
```

## Configuration

Configuration is managed through `src/main/resources/application.yml`:

- `server.port`: Order Service port (default: 8080)
- `spring.datasource.url`: H2 database URL
- `spring.jpa.hibernate.ddl-auto`: Hibernate DDL strategy (default: update)
- `payment.service.url`: Payment Service base URL (default: http://localhost:8081)

## Architecture

- **Model**: JPA entities for Order persistence
- **Repository**: Spring Data JPA repository for database operations
- **Service**: Business logic layer with order and payment management
- **Controller**: REST endpoints for API consumption
- **PaymentClient**: REST client for communicating with Payment Service

## Integration

The Order Service expects a Payment Service running at `http://localhost:8081` with the following endpoint:

```
POST /api/payments
Content-Type: application/json

{
  "orderId": 1,
  "amount": 999.99
}
```

Expected response:
```json
{
  "id": 1,
  "orderId": 1,
  "status": "SUCCESS"
}
```

If the Payment Service is unavailable, the order will still be created but marked with status `PAYMENT_FAILED`.

## CI/CD

The project includes a `.gitlab-ci.yml` configuration that:
- Builds the project with Gradle
- Runs tests during the test stage
- Uses Gradle 8.5 with JDK 21
