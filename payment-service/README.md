# Payment Service

A simple Spring Boot microservice for processing payments. This service is called by the Order Service via REST APIs to handle payment operations.

## Features

- Create payments for orders
- Retrieve payment information by payment ID or order ID
- H2 in-memory database for development and testing
- REST API with standard HTTP status codes

## Technology Stack

- Java 21
- Spring Boot 3.2.3
- Spring Data JPA
- H2 Database
- Gradle

## Building

```bash
gradle clean build
```

## Running

```bash
gradle bootRun
```

The service will start on port 8081.

## API Endpoints

### Create Payment
```
POST /api/payments
Content-Type: application/json

{
  "orderId": 1,
  "amount": 99.99
}
```

### Get Payment
```
GET /api/payments/{id}
```

### Get Payment by Order ID
```
GET /api/payments/order/{orderId}
```

## Testing

```bash
gradle test
```

## H2 Console

Access the H2 database console at: `http://localhost:8081/h2-console`

- JDBC URL: `jdbc:h2:mem:paymentdb`
- Username: `sa`
- Password: (leave blank)
