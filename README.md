# Payment Gateway

> A REST API that sits between a merchant and an acquiring bank, enabling merchants to process card payments securely.

---

## Requirements

| Tool | Version |
|------|---------|
| JDK | 17+ |
| Docker | Latest |

---

## Getting Started

**1.** Start the bank simulator:
```bash
docker-compose up -d
```

**2.** Run the application:
```bash
.\gradlew.bat bootRun
```

**3.** Run tests:
```bash
.\gradlew.bat test
```

> **Application:** http://localhost:8090
>
> **Swagger UI:** http://localhost:8090/swagger-ui/index.html

---

## Endpoints

### `POST /payment` — Process a new payment

<details>
<summary><b>Request</b></summary>

```json
{
  "card_number": "2222405343248877",
  "expiry_month": 4,
  "expiry_year": 2025,
  "currency": "GBP",
  "amount": 100,
  "cvv": "123"
}
```

| Field | Type | Rules |
|-------|------|-------|
| `card_number` | string | Required, 14-19 digits |
| `expiry_month` | integer | Required, 1-12 |
| `expiry_year` | integer | Required, must not be in the past |
| `currency` | string | Required, one of `USD`, `EUR`, `GBP` |
| `amount` | integer | Required, positive |
| `cvv` | string | Required, 3-4 digits |

</details>

<details>
<summary><b>201 Created</b> — Payment processed (Authorized / Declined)</summary>

```json
{
  "id": "f1a2b3c4-d5e6-7890-abcd-ef1234567890",
  "status": "Authorized",
  "cardNumberLastFour": 8877,
  "expiryMonth": 4,
  "expiryYear": 2025,
  "currency": "GBP",
  "amount": 100
}
```

</details>

<details>
<summary><b>400 Bad Request</b> — Validation failed (Rejected)</summary>

```json
{
  "id": null,
  "status": "Rejected",
  "cardNumberLastFour": 0,
  "expiryMonth": 0,
  "expiryYear": 0,
  "currency": null,
  "amount": 0
}
```

</details>

---

### `GET /payment/{id}` — Retrieve a payment

<details>
<summary><b>200 OK</b> — Payment found</summary>

```json
{
  "id": "f1a2b3c4-d5e6-7890-abcd-ef1234567890",
  "status": "Authorized",
  "cardNumberLastFour": 8877,
  "expiryMonth": 4,
  "expiryYear": 2025,
  "currency": "GBP",
  "amount": 100
}
```

</details>

<details>
<summary><b>404 Not Found</b> — Payment not found</summary>

```json
{
  "message": "Payment not found"
}
```

</details>

---

## Payment Statuses

| Status | Meaning | HTTP Code |
|--------|---------|-----------|
| `Authorized` | Bank approved the payment | `201` |
| `Declined` | Bank rejected or was unreachable | `201` |
| `Rejected` | Validation failed, bank was never called | `400` |
