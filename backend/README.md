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

## API Endpoints

### Public Endpoints
- `GET /api/markets` - List active markets
- `GET /api/markets/{symbol}/ticker` - Get market ticker
- `GET /api/markets/{symbol}/trades` - Get recent trades
- `GET /api/markets/{symbol}/ticks` - Get historical ticks
- `GET /api/trades` - List trades (requires marketId)

### User Endpoints (require userId parameter)
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
