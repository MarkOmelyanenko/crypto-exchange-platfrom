# Crypto Exchange Simulator - Backend

REST API backend for the crypto exchange simulator application.

## Prerequisites

- Java 21+
- Maven 3.8+
- PostgreSQL 12+ (or Docker)
- Redis 6+ (optional, for caching)
- Kafka 2.8+ (optional, for event streaming)

## Local Setup

### 1. Environment Variables

Create a `.env` file in the backend directory (or export environment variables) with the following:

```bash
# Database Configuration
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/cryptoexchange
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# Redis Configuration (optional)
REDIS_HOST=localhost
REDIS_PORT=6379

# Kafka Configuration (optional)
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# JWT Configuration
# Generate a secure secret using: openssl rand -base64 32
JWT_SECRET=your-256-bit-secret-key-change-this-in-production-minimum-32-characters
JWT_EXPIRATION=86400000  # 24 hours in milliseconds

# Rate Limiting
RATE_LIMIT_ORDERS=30
RATE_LIMIT_ORDERS_WINDOW=60s
RATE_LIMIT_WALLET_DEPOSIT=10
RATE_LIMIT_WALLET_DEPOSIT_WINDOW=60s
RATE_LIMIT_WALLET_WITHDRAW=5
RATE_LIMIT_WALLET_WITHDRAW_WINDOW=60s

# Market Simulator (optional)
SIMULATOR_ENABLED=false
SIMULATOR_TICK_INTERVAL_MS=1000
SIMULATOR_SEED=42
SIMULATOR_MARKETS=BTC-USDT,ETH-USDT
```

See `application.yml` for all available configuration options with defaults.

### 2. Database Setup

#### Using Docker Compose (Recommended)

From the project root:
```bash
docker-compose up -d postgres redis kafka
```

#### Manual Setup

1. Create PostgreSQL database:
```sql
CREATE DATABASE cryptoexchange;
```

2. The application will automatically run Flyway migrations on startup.

### 3. Running the Application

#### With Maven
```bash
mvn spring-boot:run
```

#### With Docker
```bash
docker build -t crypto-exchange-backend .
docker run -p 8080:8080 --env-file .env crypto-exchange-backend
```

The application will start on `http://localhost:8080`.

## Running Tests

All tests are designed to be hermetic (no external dependencies required):

```bash
mvn test
```

Tests use mocks and in-memory implementations - no database, Redis, or Kafka containers needed.

**Note:** The test configuration includes a pre-loaded Mockito agent to support mocking concrete classes in Java 23. This is automatically configured in `pom.xml` and requires no additional setup.

## API Documentation

### Swagger UI

Once the application is running, access the interactive API documentation at:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

The API documentation includes:
- All available endpoints
- Request/response schemas
- Authentication requirements (JWT bearer token - currently disabled for development)
- Try-it-out functionality

### Health Checks

- **Health endpoint**: http://localhost:8080/actuator/health
- **Info endpoint**: http://localhost:8080/actuator/info

Health checks include:
- Database connectivity
- Redis connectivity (if enabled)
- Kafka connectivity (if enabled)

## Authentication

The application uses JWT (JSON Web Token) authentication. All protected endpoints require a valid JWT token in the `Authorization` header.

### Authentication Endpoints

- `POST /api/auth/register` - Register a new user
  - Request body: `{ "login": "username", "email": "user@example.com", "password": "Password123!" }`
  - Response: `{ "accessToken": "<jwt-token>", "tokenType": "Bearer" }`
  - Returns 409 if login or email already exists

- `POST /api/auth/login` - Login with login or email
  - Request body: `{ "loginOrEmail": "username" or "user@example.com", "password": "Password123!" }`
  - Response: `{ "accessToken": "<jwt-token>", "tokenType": "Bearer" }`
  - Returns 401 for invalid credentials

- `GET /api/users/me` - Get current authenticated user
  - Requires: `Authorization: Bearer <token>` header
  - Response: `{ "id": "...", "login": "...", "email": "..." }`
  - Returns 401 if unauthenticated

### Using JWT Tokens

After successful login or registration, include the token in all API requests:

```bash
curl -H "Authorization: Bearer <your-token>" http://localhost:8080/api/users/me
```

### Security Configuration

- JWT secret key: Configured via `JWT_SECRET` environment variable (default: see `application.yml`)
  - **Generate a secure secret**: Use `openssl rand -base64 32` to generate a 256-bit (32-byte) secret
  - The secret must be at least 32 characters for HMAC-SHA256
  - **Never commit the secret to version control** - use environment variables or secrets management
- Token expiration: Configured via `JWT_EXPIRATION` in milliseconds (default: 24 hours)
- Password hashing: Uses BCrypt with strength 10
- Protected endpoints: All endpoints except `/api/auth/**` require authentication

## API Endpoints

### Public Endpoints
- `GET /api/markets` - List active markets
- `GET /api/markets/{symbol}/ticker` - Get market ticker
- `GET /api/markets/{symbol}/trades` - Get recent trades
- `GET /api/markets/{symbol}/ticks` - Get historical ticks
- `GET /api/trades` - List trades (requires marketId)

### Assets Endpoints (require JWT authentication)
- `GET /api/assets` - List assets with live Binance prices (supports `q`, `sort`, `dir`, `page`, `size`)
- `GET /api/assets/{symbol}` - Get asset detail by symbol (case-insensitive) with 24h stats
- `GET /api/assets/{symbol}/my-position` - Get user's position for the asset

### Protected Endpoints (require JWT authentication)
- `POST /api/orders` - Create order
- `GET /api/orders` - List my orders
- `GET /api/orders/{id}` - Get order by ID
- `POST /api/orders/{id}/cancel` - Cancel order
- `GET /api/wallet` - Get balances
- `POST /api/wallet/deposit` - Deposit funds
- `POST /api/wallet/withdraw` - Withdraw funds

### Admin Endpoints (if simulator enabled)
- `POST /api/admin/simulator/start` - Start market simulator
- `POST /api/admin/simulator/stop` - Stop market simulator
- `GET /api/admin/simulator/status` - Get simulator status

## Configuration

All configuration is done via environment variables or `application.yml`. Key settings:

- **Database**: Configured via `SPRING_DATASOURCE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- **Redis**: Configured via `REDIS_HOST`, `REDIS_PORT` (optional, used for caching)
- **Kafka**: Configured via `KAFKA_BOOTSTRAP_SERVERS` (optional, used for event streaming)
- **Rate Limiting**: Configured via `RATE_LIMIT_*` environment variables
- **Market Simulator**: Configured via `SIMULATOR_*` environment variables

See `application.yml` for detailed configuration options and defaults.

## Development

### Project Structure

```
src/
├── main/
│   ├── java/
│   │   └── com/cryptoexchange/backend/
│   │       ├── config/          # Configuration classes
│   │       ├── domain/
│   │       │   ├── controller/  # REST controllers
│   │       │   ├── model/       # Entity models
│   │       │   ├── repository/  # Data repositories
│   │       │   ├── service/      # Business logic
│   │       │   └── exception/    # Custom exceptions
│   │       └── BackendApplication.java
│   └── resources/
│       ├── application.yml       # Main configuration
│       └── db/migration/         # Flyway migrations
└── test/
    └── java/                     # Unit and integration tests
```

### Code Quality

- All request DTOs use Bean Validation annotations
- Global exception handler provides consistent error responses
- Request correlation IDs (X-Request-Id header) for tracing
- Comprehensive logging at service boundaries
- All fields are final where possible

## Troubleshooting

### Database Connection Issues
- Ensure PostgreSQL is running and accessible
- Check `SPRING_DATASOURCE_URL` matches your database configuration
- Verify Flyway migrations completed successfully (check logs)

### Redis Connection Issues
- Redis is optional - the application will work without it (caching disabled)
- If Redis is required, ensure it's running and `REDIS_HOST`/`REDIS_PORT` are correct

### Kafka Connection Issues
- Kafka is optional - the application will work without it (event publishing disabled)
- If Kafka is required, ensure it's running and `KAFKA_BOOTSTRAP_SERVERS` is correct

### Port Already in Use
- Change the server port in `application.yml` or set `SERVER_PORT` environment variable

## Dashboard API

The dashboard provides comprehensive portfolio analytics for authenticated users.

### Dashboard Endpoints

- `GET /api/dashboard/summary` - Get portfolio summary
  - Returns: total portfolio value, available cash, unrealized PnL (USD and %), realized PnL
  - Requires authentication

- `GET /api/dashboard/holdings` - Get user holdings
  - Returns: list of assets with quantity, average buy price, current price, market value, and PnL
  - Requires authentication

- `GET /api/dashboard/recent-transactions?limit=10` - Get recent transactions
  - Returns: latest user transactions (orders)
  - Requires authentication

- `GET /api/prices/snapshot?symbols=BTC,ETH,SOL` - Get current prices
  - Returns: current price snapshot for specified symbols
  - Public endpoint (no authentication required)

- `GET /api/prices/history?symbol=BTC&range=24h` - Get price history
  - Returns: price history for a symbol over specified range (24h, 7d, 30d)
  - Public endpoint (no authentication required)

- `GET /api/system/health` - Get system health
  - Returns: simplified health status for API, DB, Redis, Kafka
  - Public endpoint (no authentication required)

### Dashboard Calculations

**Total Portfolio Value**: Sum of all asset market values (quantity × current price) + available cash (USDT)

**Average Buy Price**: Weighted average cost basis calculated from all BUY trades, adjusted for SELL trades using average cost method

**Unrealized PnL**: Current market value − cost basis (average buy price × quantity)

**Realized PnL**: Sum of (sell price − average cost at time of sale) × quantity for all completed SELL trades

**Note**: Calculations use average cost method for simplicity. All prices are in USD (USDT is treated as 1:1 with USD).

### How Dashboard Prices & History Are Obtained from Binance

All prices displayed on the dashboard are **Binance-driven**. No synthetic or random-walk data is used.

**Real-time prices** (used in portfolio summary and holdings):
1. `DashboardService.getCurrentPrices()` calls `BinanceService.getCurrentPrice()` for each asset symbol (e.g. `BTCUSDT`).
2. Fallback: `PriceTick` table (Binance-fetched ticks stored by the scheduled job).
3. Fallback: `MarketTick` table (simulator-generated ticks, if any).
4. `BinanceService` uses the public Binance REST API (`https://api.binance.com/api/v3/ticker/price`).

**Price snapshot** (`GET /api/prices/snapshot`):
1. Fetches live price from Binance via `BinanceService.getCurrentPrice()`.
2. Fallback: `PriceTick` table (Binance-fetched ticks stored by the scheduled job).
3. Fallback: `MarketTick` table (simulator-generated ticks, if any).

**Price history** (`GET /api/prices/history`):
1. Fetches candlestick (kline) data directly from Binance via `BinanceService.getKlines()` (e.g. 1h intervals for 24h range).
2. Fallback: `PriceTick` table entries for the requested time range.
3. Fallback: `MarketTick` table entries for the requested time range.
4. Returns empty array if no Binance data is available — no mock data is generated.

**Scheduled price fetcher** (`BinancePriceFetcherService`):
- Runs every 30 seconds (configurable via `app.price-fetcher.interval-ms`).
- Fetches current prices for configured symbols (`app.price-fetcher.symbols`, default: `BTC,ETH,SOL`).
- Stores each price as a `PriceTick` row (`symbol`, `price_usd`, `ts`).
- This provides a local cache of Binance prices so the history endpoint works even when the Binance API is temporarily unreachable.

**Configuration** (in `application.yml` or environment variables):
```yaml
app:
  price-fetcher:
    symbols: BTC,ETH,SOL          # Comma-separated symbols to track
    interval-ms: 30000             # Fetch interval in milliseconds
```

## Assets Feature (Phase 5)

The assets feature provides a list page and detail page backed by live Binance prices.

### How Assets Fetch Prices

1. **List endpoint** (`GET /api/assets`): Uses `BinanceService.getBatchTicker24h()` to fetch
   24h ticker data for all assets in a single Binance API call. Returns `priceUsd` and
   `change24hPercent` for each asset. USDT is always $1.00 / 0%.

2. **Detail endpoint** (`GET /api/assets/{symbol}`): Uses `BinanceService.getTicker24h()` for
   the specific symbol, returning detailed stats (price, 24h change, high, low, volume).

3. **Position endpoint** (`GET /api/assets/{symbol}/my-position`): Combines the user's
   `Balance` with the live Binance price to compute `marketValueUsd`.

### Caching Strategy

`BinanceService` includes an in-memory `ConcurrentHashMap` cache with short TTLs:
- **Ticker cache**: 5-second TTL — avoids repeated calls for the same symbol within seconds.
- **Klines cache**: 10-second TTL — avoids repeated history calls for the same (symbol, range).
- **Stale data fallback**: If Binance is unreachable, the last cached value is returned instead
  of null, ensuring the UI always shows the most recent known price.

### Graceful Failure

If Binance is completely unavailable (no cache available):
- Assets are returned with `priceUsd: null`, `change24hPercent: null`, and `priceUnavailable: true`.
- The frontend renders "—" for unavailable prices and displays a hint.
- No 500 errors are propagated; all exceptions are caught and logged.
