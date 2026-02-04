# Market Simulator Feature

## Overview

The Market Simulator generates realistic market data (ticker updates and trades) for portfolio showcase purposes. It's designed to be simple, deterministic, and demo-friendly.

## Features

- **Deterministic price simulation** using seeded random number generator
- **Periodic tick generation** with configurable interval
- **Trade generation** with realistic price/quantity distribution
- **Data persistence** to PostgreSQL
- **Redis snapshots** for fast read access
- **Kafka event publishing** for real-time updates
- **REST API** for reading market data
- **Admin controls** for starting/stopping simulator

## Configuration

Add to `application.yml` or set environment variables:

```yaml
app:
  simulator:
    enabled: true                    # Enable/disable simulator
    tick-interval-ms: 1000           # Interval between ticks (ms)
    seed: 42                         # RNG seed for determinism
    markets: BTC-USDT,ETH-USDT       # Comma-separated market symbols
    spread-bps: 8                    # Bid/ask spread in basis points
    avg-trades-per-tick: 2          # Average number of trades per tick
    min-qty: 0.001                   # Minimum trade quantity
    max-qty: 1.0                     # Maximum trade quantity
    persist-every-n-ticks: 1        # Persist every N ticks (1 = all)
    auto-start: true                 # Auto-start on application startup
    redis:
      ticker-ttl: 60                 # Ticker snapshot TTL (seconds)
      trades-ttl: 60                 # Trades list TTL (seconds)
      max-recent-trades: 50          # Max trades in Redis list
```

### Environment Variables

- `SIMULATOR_ENABLED=true`
- `SIMULATOR_TICK_INTERVAL_MS=1000`
- `SIMULATOR_SEED=42`
- `SIMULATOR_MARKETS=BTC-USDT,ETH-USDT`
- `SIMULATOR_SPREAD_BPS=8`
- `SIMULATOR_AVG_TRADES_PER_TICK=2`
- `SIMULATOR_MIN_QTY=0.001`
- `SIMULATOR_MAX_QTY=1.0`
- `SIMULATOR_PERSIST_EVERY_N_TICKS=1`
- `SIMULATOR_AUTO_START=true`

## Database Schema

Two new tables are created via Flyway migration `V5__add_market_simulator_tables.sql`:

- **market_tick**: Stores periodic price updates (last, bid, ask, volume)
- **market_trade**: Stores simulated trades (price, qty, side)

Both tables are indexed by `(market_symbol, ts DESC)` for fast recent queries.

## REST API Endpoints

### Public Endpoints

#### List Markets
```
GET /api/markets
```
Returns list of active markets.

#### Get Ticker
```
GET /api/markets/{symbol}/ticker
```
Returns latest ticker data. Tries Redis first, falls back to database.

#### Get Recent Trades
```
GET /api/markets/{symbol}/trades?limit=50
```
Returns recent trades. Tries Redis first, falls back to database.

#### Get Historical Ticks
```
GET /api/markets/{symbol}/ticks?from=2024-01-01T00:00:00Z&to=2024-01-02T00:00:00Z&page=0&size=100
```
Returns historical tick data from database.

### Admin Endpoints

#### Start Simulator
```
POST /api/admin/simulator/start
```

#### Stop Simulator
```
POST /api/admin/simulator/stop
```

#### Get Simulator Status
```
GET /api/admin/simulator/status
```
Returns current status and configuration.

## Redis Keys

- `ticker:{marketSymbol}` - JSON snapshot of latest ticker data
- `trades:{marketSymbol}` - Redis LIST of recent trades (newest first)

Both keys have configurable TTL (default 60 seconds).

## Kafka Topics

- `market.ticks` - Market tick events (keyed by marketSymbol)
- `market.trades` - Market trade events (keyed by marketSymbol)

Event payloads are JSON-serialized `MarketTickEvent` and `MarketTradeEvent` objects.

## Running Locally

1. **Start infrastructure:**
   ```bash
   docker-compose up -d
   ```

2. **Configure simulator:**
   Set `SIMULATOR_ENABLED=true` in your `.env` file or `application.yml`

3. **Start backend:**
   ```bash
   cd backend
   mvn spring-boot:run
   ```

4. **Verify simulator is running:**
   ```bash
   curl http://localhost:8080/api/admin/simulator/status
   ```

5. **Check market data:**
   ```bash
   # Get ticker
   curl http://localhost:8080/api/markets/BTC-USDT/ticker
   
   # Get recent trades
   curl http://localhost:8080/api/markets/BTC-USDT/trades?limit=10
   ```

## Architecture

### Components

1. **MarketSimulationEngine**: Core simulation logic with deterministic RNG
2. **MarketSimulatorService**: Scheduled service that orchestrates tick generation
3. **MarketSimulatorRedisService**: Manages Redis snapshots
4. **MarketSimulatorKafkaPublisher**: Publishes events to Kafka
5. **MarketController**: Public REST endpoints for market data
6. **SimulatorController**: Admin endpoints for simulator control

### Price Evolution

Uses geometric random walk:
```
newPrice = lastPrice * exp(volatility * normalRandom)
```

- `volatility`: Configurable per-market (default 0.012 = 1.2%)
- `normalRandom`: Generated using Box-Muller transform
- Spread: `bid = mid * (1 - spreadBps/20000)`, `ask = mid * (1 + spreadBps/20000)`

### Trade Generation

- Number of trades: Poisson-like distribution around `avg-trades-per-tick`
- Price: Near mid price (within 0.1% range)
- Quantity: Random between `min-qty` and `max-qty`
- Side: Random BUY/SELL

## Testing

### Unit Tests
- `MarketSimulationEngineTest`: Tests deterministic behavior and price evolution

### Integration Tests
- `MarketSimulatorIntegrationTest`: Tests data persistence and Redis updates

Run tests:
```bash
cd backend
mvn test
```

## Files Created/Modified

### New Entities
- `MarketTick.java` - Ticker data entity
- `MarketTrade.java` - Simulated trade entity

### New Repositories
- `MarketTickRepository.java`
- `MarketTradeRepository.java`

### New Services
- `MarketSimulationEngine.java` - Core simulation logic
- `MarketSimulatorService.java` - Scheduled orchestrator
- `MarketSimulatorRedisService.java` - Redis snapshot management
- `MarketSimulatorKafkaPublisher.java` - Kafka event publishing

### New Controllers
- `MarketController.java` - Public market data endpoints
- `SimulatorController.java` - Admin simulator control

### New Events
- `MarketTickEvent.java` - Kafka event for ticks
- `MarketTradeEvent.java` - Kafka event for trades

### Database Migration
- `V5__add_market_simulator_tables.sql` - Creates market_tick and market_trade tables

### Configuration
- Updated `application.yml` with simulator properties
- Updated `KafkaConfig.java` with new topics and producers
- Updated `BackendApplication.java` to enable scheduling

### Tests
- `MarketSimulationEngineTest.java` - Unit tests
- `MarketSimulatorIntegrationTest.java` - Integration tests

## Notes

- Simulator is disabled by default (`enabled: false`)
- Admin endpoints are currently open (TODO: add authentication)
- Per-market initial prices and volatilities can be configured via properties (see commented examples in `application.yml`)
- Simulator uses deterministic RNG with configurable seed for reproducible results
- Data is persisted to PostgreSQL and cached in Redis for fast reads
