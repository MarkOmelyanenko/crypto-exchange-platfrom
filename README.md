# Crypto Exchange

A full-stack cryptocurrency exchange platform built with Spring Boot and React. Users can register, deposit USDT, browse live-priced crypto assets, execute market and limit orders, and track their portfolio -- all powered by real-time price data from external exchanges (WhiteBit, Binance).

---

## Key Features

- **JWT Authentication** -- registration, login, profile management, and password change with stateless token-based security (BCrypt + JJWT).
- **Portfolio Dashboard** -- aggregated portfolio value, per-asset holdings with live USD prices, and profit/loss tracking.
- **Wallet and Deposits** -- multi-asset balance management with USDT cash deposits subject to a rolling 24-hour limit.
- **Asset Catalog** -- browse 100+ seeded crypto assets with live prices, 24h change, search, sorting, and pagination.
- **Asset Detail View** -- price charts (24h / 7d / 30d), market statistics (high, low, volume), and the user's current position.
- **Market Orders** -- execute spot BUY/SELL orders at live market prices fetched from WhiteBit.
- **Limit Order Book** -- place limit orders matched by a price-time priority matching engine with partial fill support.
- **Transaction History** -- unified, filterable view of all transactions and trades with pagination.
- **Real-Time Price Stream** -- Server-Sent Events (SSE) endpoint broadcasting live price updates to connected clients.
- **Rate Limiting** -- Redis-backed per-user rate limits on order placement, deposits, and withdrawals.
- **Health Monitoring** -- Spring Boot Actuator integration with custom system health endpoint reporting API, database, Redis, and Kafka status.
- **API Documentation** -- interactive Swagger UI auto-generated from annotated controllers via SpringDoc OpenAPI.

---

## Tech Stack

| Layer            | Technology                                                                 |
|------------------|----------------------------------------------------------------------------|
| **Backend**      | Java 21, Spring Boot 4.0, Spring Security, Spring Data JPA, Spring Kafka  |
| **Frontend**     | React 19, Vite 7, React Router 6, Axios, Recharts                         |
| **Database**     | PostgreSQL 16 with Flyway migrations                                       |
| **Cache**        | Redis 7 (caching, rate limiting)                                           |
| **Messaging**    | Apache Kafka (Confluent 7.6) with Zookeeper                               |
| **Auth**         | JWT (jjwt 0.12.5) + BCrypt                                                |
| **API Docs**     | SpringDoc OpenAPI 3.0.1 (Swagger UI)                                      |
| **Build**        | Maven (backend), npm (frontend), Docker, Make                              |
| **CI/CD**        | Jenkins (Declarative Pipeline), Makefile                                   |
| **Testing**      | JUnit 5, Mockito, Testcontainers (PostgreSQL, Kafka), H2, ESLint          |

---

## Architecture

```
                  +------------------+
                  |   React SPA      |  (Vite dev server :5173)
                  |   (frontend/)    |
                  +--------+---------+
                           |  HTTP / SSE
                           v
                  +------------------+
                  | Spring Boot API  |  (:8080)
                  |   (backend/)     |
                  +--+------+-----+--+
                     |      |     |
            +--------+  +---+  +--+--------+
            |           |      |           |
      +-----v---+  +---v---+  +---v----+  |
      |PostgreSQL|  | Redis |  | Kafka  |  |  External APIs
      |  :5432   |  | :6379 |  | :9092  |  +---> WhiteBit
      +---------+   +-------+  +--------+  +---> Binance
```

- The **React frontend** communicates with the backend over REST (`/api/**`) and subscribes to live price updates via SSE (`/api/stream/prices`).
- The **Spring Boot backend** persists data in PostgreSQL, uses Redis for caching and rate limiting, and publishes domain events (order created, trade executed) to Kafka topics.
- **Live market prices** are fetched from WhiteBit (on-demand via REST) and Binance (scheduled periodic fetch stored as price ticks).
- **Flyway** manages all database schema changes through versioned SQL migrations.
- In production, **Nginx** acts as a reverse proxy, serving the frontend static build and forwarding `/api/` and `/actuator/` requests to the backend.

---

## Repository Structure

```
crypto-exchange-sim/
|-- backend/                    # Spring Boot application
|   |-- src/main/java/          #   Controllers, services, models, config
|   |-- src/main/resources/     #   application.yml profiles, Flyway migrations
|   |-- src/test/               #   Unit and integration tests
|   |-- Dockerfile              #   Multi-stage Docker build
|   +-- pom.xml                 #   Maven project descriptor
|-- frontend/                   # React SPA
|   |-- src/
|   |   |-- app/                #   App shell, router
|   |   |-- pages/              #   Page components (Dashboard, Wallet, Trading, etc.)
|   |   +-- shared/             #   API clients, context, hooks, components
|   |-- Dockerfile              #   Multi-stage build (Node -> Nginx)
|   +-- package.json
|-- devops/jenkins/             # Jenkins CI server (Dockerfile + docker-compose)
|-- docker-compose.yml          # Development infrastructure (Postgres, Redis, Kafka)
|-- Jenkinsfile                 # Declarative CI pipeline
+-- Makefile                    # Developer convenience targets
```

---

## Getting Started (Local Development)

### Prerequisites

| Tool      | Minimum Version | Purpose                                       |
|-----------|-----------------|-----------------------------------------------|
| Java      | 21              | Backend compilation and runtime               |
| Maven     | 3.9+ (wrapper)  | Backend build (wrapper included: `./mvnw`)    |
| Node.js   | 20              | Frontend build and development server         |
| npm       | 9+              | Frontend dependency management                |
| Docker    | 24+             | Infrastructure containers                     |
| Docker Compose | v2         | Orchestrating Postgres, Redis, Kafka          |

### Configuration

Create a `.env` file in the project root for Docker Compose and the backend:

```env
# PostgreSQL
POSTGRES_DB=crypto_exchange
POSTGRES_USER=postgres
POSTGRES_PASSWORD=<your-password>

# Spring datasource (used by the backend)
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/crypto_exchange
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=<your-password>

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# JWT
JWT_SECRET=<your-256-bit-secret-minimum-32-characters>
JWT_EXPIRATION=86400000

# CORS (frontend dev server)
CORS_ALLOWED_ORIGINS=http://localhost:5173

# Price fetcher
PRICE_FETCHER_SYMBOLS=BTC,ETH,SOL
PRICE_FETCHER_INTERVAL_MS=30000

# Rate limits (requests per window)
RATE_LIMIT_ORDERS=30
RATE_LIMIT_WALLET_DEPOSIT=10
RATE_LIMIT_WALLET_WITHDRAW=5
```

> The backend loads `.env` automatically via its `DotenvConfig` class. No additional tooling is required.

### Run Infrastructure

Start PostgreSQL, Redis, Kafka, and Zookeeper:

```bash
docker compose up -d
```

Verify all containers are running:

```bash
docker compose ps
```

### Run Backend

```bash
cd backend
./mvnw spring-boot:run
```

The backend starts on **http://localhost:8080** with the `dev` profile active by default.

### Run Frontend

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server starts on **http://localhost:5173** and proxies `/api` and `/actuator` requests to the backend automatically.

---

## API Documentation

Interactive API documentation is available via Swagger UI when the backend is running:

| Resource    | URL                                              |
|-------------|--------------------------------------------------|
| Swagger UI  | http://localhost:8080/swagger-ui.html             |
| OpenAPI JSON| http://localhost:8080/v3/api-docs                 |

The Swagger UI supports JWT Bearer authentication -- paste a token obtained from `/api/auth/login` into the Authorize dialog to test authenticated endpoints.

API groups documented in Swagger: **Auth**, **Users**, **Dashboard**, **Wallet**, **Assets**, **Markets**, **Orders**, **Market Orders**, **Trades**, **Transactions**, **Prices**, **Price Stream**, **System**.

---

## Health Checks / Observability

### Spring Boot Actuator

```bash
curl http://localhost:8080/actuator/health
```

Returns component-level health for the database, Redis, and Kafka. In the `dev` profile, detailed health information is shown.

Exposed actuator endpoints: `health`, `info`.

### Custom System Health

```bash
curl http://localhost:8080/api/system/health
```

Returns a simplified JSON status object:

```json
{
  "status": {
    "api": "OK",
    "db": "OK",
    "redis": "OK",
    "kafka": "OK"
  }
}
```

This endpoint is public (no authentication required) and is used by the frontend dashboard.

---

## Testing

### Backend

Unit and integration tests use JUnit 5, Mockito, and Testcontainers (PostgreSQL, Kafka). An H2 in-memory database is available for lightweight tests.

```bash
cd backend
./mvnw clean test
```

Or via Make:

```bash
make backend-test
```

### Frontend

Lint the frontend codebase:

```bash
cd frontend
npm run lint
```

Or via Make:

```bash
make frontend-lint
```

### Full Local CI

Run the complete CI pipeline locally (backend tests, backend build, frontend lint, frontend build):

```bash
make ci-local
```

---

## CI/CD

### Jenkins Pipeline

The project includes a declarative `Jenkinsfile` with the following stages:

1. **Backend: Test** -- runs `./mvnw clean test` and archives JUnit results.
2. **Backend: Package** -- builds the JAR (`./mvnw -DskipTests package`) and archives the artifact.
3. **Frontend: Install** -- installs npm dependencies (`npm ci`).
4. **Frontend: Lint** -- runs ESLint.
5. **Frontend: Build** -- builds the production bundle and archives `dist/`.
6. **Docker Build** (conditional, parallel) -- builds `crypto-backend` and `crypto-frontend` Docker images if Docker is available on the agent.

### Running Jenkins Locally

A self-contained Jenkins server is provided in `devops/jenkins/`:

```bash
make jenkins-up
```

Jenkins is accessible at **http://localhost:8081**. Retrieve the initial admin password:

```bash
make jenkins-password
```

Stop Jenkins:

```bash
make jenkins-down
```

The Jenkins image is based on `jenkins/jenkins:lts` with Node.js 20 and Docker CLI pre-installed.

---

## Hosting / Deployment

### `deploy-local/` Directory

The `deploy-local/` directory is excluded from version control (listed in `.gitignore`) and is intended for local-only deployment helpers. It is expected to contain:

| File / Directory             | Purpose                                                    |
|------------------------------|------------------------------------------------------------|
| `docker-compose.prod.yml`   | Production-grade Compose file with all services and Nginx   |
| `nginx/nginx.conf`          | Reverse proxy config (API proxy, SSE support, static files) |
| `.env.prod`                 | Production environment variables (secrets)                  |
| `deploy.sh`                 | Deployment script (build, upload, restart on remote host)   |
| Utility scripts              | Health checks, restart helpers, tunnel setup                |

### Suggested Setup (if `deploy-local/` does not exist)

Create the directory and populate it with the required files:

```bash
mkdir -p deploy-local/nginx
```

1. **`docker-compose.prod.yml`** -- define services for `postgres`, `redis`, `zookeeper`, `kafka`, `backend`, and `nginx`. Point Nginx volumes to `./nginx/nginx.conf` and `./frontend/dist`.
2. **`nginx/nginx.conf`** -- configure an upstream block for the backend (`backend:8080`), proxy `/api/` and `/actuator/` to it, proxy `/api/stream/` with SSE-friendly settings (`proxy_buffering off`), and serve frontend static files with SPA fallback (`try_files $uri $uri/ /index.html`).
3. **`.env.prod`** -- supply all required environment variables (database credentials, Redis password, JWT secret, CORS origins, Kafka bootstrap servers).

### Running in Production Mode

```bash
cd deploy-local

# Build backend Docker image
cd ../backend && docker build -t crypto-exchange-backend:latest . && cd ../deploy-local

# Build frontend
cd ../frontend && npm ci && npm run build && cd ../deploy-local

# Start all services
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
```

The application will be available on port 80 (Nginx).

### Docker Images

Build standalone Docker images using Make:

```bash
make backend-docker    # builds crypto-backend:latest
make frontend-docker   # builds crypto-frontend:latest
```

Both images use multi-stage builds to minimize size. The backend image runs as a non-root `spring` user and includes a health check. The frontend image serves the production build via Nginx with SPA routing and API proxy configuration.
