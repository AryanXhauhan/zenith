# ⚡ Zenith — High-Performance Settlement Engine

> A production-grade FinTech settlement platform built with Java 21 + Spring Boot 3 + PostgreSQL + Redis + Ethereum (Web3j) + Kubernetes.

---

## Architecture Overview

```
┌───────────────────────────────────────────────────────────────────┐
│                        CLIENT / FRONTEND                          │
└─────────────────────────────┬─────────────────────────────────────┘
                              │  X-Zenith-Key header
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│  MODULE 1: API GATEWAY (ApiKeyInterceptor + RateLimiterService)     │
│  • SHA-256 key validation  →  Redis cache (μs) → PostgreSQL fallback│
│  • Token-bucket rate limiting per tier (Lua atomic script in Redis) │
│    FREE: 5 req/s | PRO: 100 req/s | ENTERPRISE: 1000 req/s         │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│  MODULE 3: IDEMPOTENCY ASPECT (AOP @Idempotent)                     │
│  • Client sends idempotencyKey with every request                   │
│  • 2nd press of "Pay" button → same response, no new transaction    │
│  • Cached in Redis for 24 hours                                     │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│  MODULE 2: DOUBLE-ENTRY LEDGER (LedgerService + PostgreSQL)         │
│  • Every transfer = 2 LedgerEntry rows (DEBIT + CREDIT)             │
│  • @Transactional(isolation=REPEATABLE_READ) — full ACID            │
│  • SELECT FOR UPDATE (pessimistic locking) → no double-spend        │
│  • Invariant: SUM(DEBIT) == SUM(CREDIT) always                      │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│  MODULE 4: WEB3 SETTLEMENT (Web3Service + ZenithEscrow.sol)         │
│  • Solidity escrow contract on Ethereum (EVM-compatible)            │
│  • deposit() → release() → confirmBlockchainSettlement()            │
│  • LedgerService marks transaction blockchain_confirmed = true      │
└─────────────────────────────────────────────────────────────────────┘
                               │
             ┌─────────────────┴──────────────────┐
             ▼                                    ▼
     PostgreSQL 16                          Redis 7.2
   (Ledger + Accounts)            (Rate limiting + Idempotency)

┌─────────────────────────────────────────────────────────────────────┐
│  MODULE 5: KUBERNETES INFRASTRUCTURE                                │
│  • Multi-stage Dockerfile (eclipse-temurin:21, non-root user)       │
│  • deployment.yaml with Liveness / Readiness / Startup Probes       │
│  • HPA: 3→20 replicas, CPU 70% + Memory 80% triggers               │
│  • Rolling updates with zero downtime                               │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Project Structure

```
Zenith/
├── src/main/java/com/zenith/
│   ├── ZenithApplication.java               # Entry point
│   ├── config/
│   │   ├── ZenithProperties.java            # Type-safe config binding
│   │   └── RedisConfig.java                 # Redis bean configuration
│   ├── gateway/                             # ── MODULE 1 ──
│   │   ├── model/ApiKey.java
│   │   ├── repository/ApiKeyRepository.java
│   │   ├── service/ApiKeyService.java       # SHA-256 + Redis cache lookup
│   │   ├── service/RateLimiterService.java  # Lua token-bucket in Redis
│   │   ├── interceptor/ApiKeyInterceptor.java
│   │   └── config/WebConfig.java
│   ├── ledger/                              # ── MODULE 2 ──
│   │   ├── model/{Account,Transaction,LedgerEntry}.java
│   │   ├── repository/{Account,Transaction,LedgerEntry}Repository.java
│   │   ├── dto/{TransactionRequest,TransactionResponse}.java
│   │   ├── service/LedgerService.java       # Core double-entry logic
│   │   └── controller/LedgerController.java
│   ├── idempotency/                         # ── MODULE 3 ──
│   │   ├── Idempotent.java                  # Marker annotation
│   │   ├── IdempotencyService.java          # Redis cache
│   │   └── IdempotencyAspect.java           # AOP interceptor
│   ├── web3/                                # ── MODULE 4 ──
│   │   ├── Web3Service.java                 # Web3j + Ethereum
│   │   ├── Web3Controller.java
│   │   └── dto/BlockchainSettlementRequest.java
│   └── common/exception/                    # Shared error handling
│       ├── GlobalExceptionHandler.java      # RFC 7807 ProblemDetail
│       ├── InsufficientFundsException.java
│       ├── ResourceNotFoundException.java
│       └── ValidationException.java
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/V1__init_schema.sql     # Flyway: full DB schema
├── src/test/java/com/zenith/
│   ├── ledger/LedgerServiceTest.java        # Double-entry integration tests
│   └── gateway/ApiKeyServiceTest.java       # Gateway unit tests
├── contracts/
│   └── ZenithEscrow.sol                     # Solidity escrow contract
├── k8s/
│   ├── deployment.yaml                      # K8s Deployment + probes
│   ├── service.yaml                         # ClusterIP Service
│   ├── hpa.yaml                             # HPA 3→20 replicas
│   └── infrastructure.yaml                  # Namespace + ConfigMap + Redis
├── Dockerfile                               # Multi-stage (JDK build → JRE runtime)
├── docker-compose.yml                       # Local dev stack
└── pom.xml                                  # Maven dependencies
```

---

## Quick Start

### Prerequisites
- Java 21+
- Docker & Docker Compose
- Maven 3.9+ (or use `./mvnw`)

### 1. Start the local stack

```bash
# Start PostgreSQL + Redis
docker compose up -d postgres redis

# Run the application
./mvnw spring-boot:run
```

### 2. Or run everything with Docker Compose

```bash
docker compose up -d
```

App will be available at `http://localhost:8080`

---

## API Reference

### Module 1 – Authentication

All protected endpoints require the `X-Zenith-Key` header.

```bash
# Every request must include:
curl -H "X-Zenith-Key: your-api-key" ...
```

**Rate limit responses:**
- `401 UNAUTHORIZED` — missing or invalid key
- `429 TOO_MANY_REQUESTS` — rate limit exceeded (see `Retry-After` header)

---

### Module 2 – Double-Entry Ledger

#### POST `/api/v1/ledger/transfer`

Initiate an atomic transfer between two accounts.

```bash
curl -X POST http://localhost:8080/api/v1/ledger/transfer \
  -H "Content-Type: application/json" \
  -H "X-Zenith-Key: your-api-key" \
  -d '{
    "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
    "sourceAccountNumber": "ZNT-SRC-0001",
    "destAccountNumber":   "ZNT-DST-0001",
    "amount":   "250.00",
    "currency": "USD",
    "description": "Invoice #INV-001 payment"
  }'
```

**Response `201 Created`:**
```json
{
  "id": "a1b2c3d4-...",
  "reference": "ZNT-1714156800000-A1B2C3D4",
  "status": "COMPLETED",
  "amount": "250.00",
  "currency": "USD",
  "sourceAccountNumber": "ZNT-SRC-0001",
  "destAccountNumber": "ZNT-DST-0001",
  "blockchainConfirmed": false,
  "createdAt": "2024-04-26T14:00:00Z"
}
```

#### GET `/api/v1/ledger/transactions/{id}`

```bash
curl http://localhost:8080/api/v1/ledger/transactions/a1b2c3d4-... \
  -H "X-Zenith-Key: your-api-key"
```

---

### Module 3 – Idempotency

Retry the same request with the **same `idempotencyKey`** — no duplicate transaction:

```bash
# First call → creates transaction
curl -X POST .../transfer -d '{"idempotencyKey":"abc-123", ...}'

# Second call (retry) → returns SAME response, header X-Idempotency-Replay: true
curl -X POST .../transfer -d '{"idempotencyKey":"abc-123", ...}'
```

---

### Module 4 – Web3 Settlement

#### POST `/api/v1/web3/confirm`

Confirm an on-chain settlement and update the Zenith ledger.

```bash
curl -X POST http://localhost:8080/api/v1/web3/confirm \
  -H "Content-Type: application/json" \
  -H "X-Zenith-Key: your-api-key" \
  -d '{
    "blockchainTxHash": "0xabc123...",
    "zenithTransactionId": "a1b2c3d4-..."
  }'
```

#### GET `/api/v1/web3/balance/{address}`

Returns ETH balance of an Ethereum address in Wei.

---

## Module 5 – Kubernetes Deployment

### Deploy to Kubernetes

```bash
# 1. Create namespace and infrastructure (Redis, ConfigMap)
kubectl apply -f k8s/infrastructure.yaml

# 2. Create secrets (fill in real values)
kubectl create secret generic zenith-db-secret \
  --from-literal=host=your-postgres-host \
  --from-literal=database=zenith \
  --from-literal=username=zenith_user \
  --from-literal=password=YOUR_SECURE_PASSWORD \
  -n zenith

kubectl create secret generic zenith-api-secret \
  --from-literal=master-key=YOUR_API_MASTER_KEY \
  -n zenith

kubectl create secret generic zenith-web3-secret \
  --from-literal=private-key=YOUR_ETH_PRIVATE_KEY \
  -n zenith

# 3. Deploy the application
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml

# 4. Enable autoscaling
kubectl apply -f k8s/hpa.yaml

# 5. Verify
kubectl get pods -n zenith
kubectl get hpa   -n zenith
```

### Health Probes

| Probe     | Path                          | Purpose                          |
|-----------|-------------------------------|----------------------------------|
| Liveness  | `/actuator/health/liveness`   | Restarts pod if app is stuck     |
| Readiness | `/actuator/health/readiness`  | Removes pod from load balancer   |
| Startup   | `/actuator/health`            | Allows Flyway to complete first  |

---

## Smart Contract – ZenithEscrow.sol

```
contracts/ZenithEscrow.sol
```

**Deploy to Sepolia testnet:**

```bash
# Install Hardhat
npm install --save-dev hardhat

# Deploy
npx hardhat run scripts/deploy.js --network sepolia
```

**Key functions:**

| Function              | Caller       | Description                          |
|-----------------------|--------------|--------------------------------------|
| `deposit(id, payee)`  | Any wallet   | Lock ETH in escrow                   |
| `release(id)`         | Owner only   | Release funds to payee               |
| `refund(id)`          | Owner only   | Return funds to payer                |
| `getEscrow(id)`       | Anyone       | View escrow details                  |

---

## Security Design

| Concern               | Solution                                        |
|-----------------------|-------------------------------------------------|
| API Authentication    | SHA-256 hashed keys in DB, cached in Redis      |
| Rate Limiting         | Atomic Lua token-bucket in Redis (no race)      |
| Double-spend          | PostgreSQL `SELECT FOR UPDATE` pessimistic lock |
| Double-payment        | Idempotency keys cached 24h in Redis            |
| SQL Injection         | JPA/Hibernate parameterized queries             |
| Reentrancy (contract) | CEI pattern + mutex flag in Solidity            |
| Container security    | Non-root user (`zenith:zenith`) in Docker       |
| Secrets               | Kubernetes Secrets (never in ConfigMap)         |

---

## Running Tests

```bash
./mvnw test
```

Tests cover:
- ✅ Double-entry invariant: `SUM(DEBIT) == SUM(CREDIT)`
- ✅ Atomic rollback on `InsufficientFundsException`
- ✅ Frozen account rejection
- ✅ Self-transfer rejection
- ✅ API key cache hit / miss behaviour

---

## Environment Variables Reference

| Variable             | Description                    | Required |
|----------------------|--------------------------------|----------|
| `DB_HOST`            | PostgreSQL host                | ✅        |
| `DB_NAME`            | Database name                  | ✅        |
| `DB_USER`            | Database user                  | ✅        |
| `DB_PASSWORD`        | Database password              | ✅        |
| `REDIS_HOST`         | Redis host                     | ✅        |
| `ZENITH_MASTER_KEY`  | Master API key (dev only)      | ✅        |
| `WEB3_RPC_URL`       | Ethereum node RPC URL          | ✅        |
| `WEB3_PRIVATE_KEY`   | Ethereum wallet private key    | ✅        |
| `ESCROW_CONTRACT`    | Deployed ZenithEscrow address  | ✅        |
| `CHAIN_ID`           | Ethereum chain ID              | Optional |

---

> Built with ⚡ by Zenith Engineering
