# Crypto Exchange Simulator - Project Audit

**Date:** 2025-01-27  
**Auditor:** Senior Full-Stack + DevOps Engineer  
**Purpose:** Full analysis for recruiter-ready deployment

---

## Executive Summary

This is a **functional crypto exchange simulator** with a Spring Boot backend (Java 21) and React frontend (Vite). The application implements authentication, portfolio management, trading, deposits with limits, and real-time price data from Binance. The codebase is well-structured with proper separation of concerns, but **lacks production-ready deployment configuration** and has some environment-specific hardcoding that will block deployment.

**Current State:** ✅ Core features work | ⚠️ Deployment not ready | ❌ No demo environment

---

## A. What Exists Now

### Backend Architecture

**Framework & Stack:**
- **Spring Boot 4.0.2** (Java 21)
- **PostgreSQL 16** (via Flyway migrations)
- **Redis 7** (optional, for caching)
- **Kafka 7.6.2** (optional, for event streaming)
- **JWT authentication** (HMAC-SHA256, 24h expiration)
- **Spring Security** with stateless sessions
- **OpenAPI/Swagger** documentation at `/swagger-ui.html`
- **Spring Actuator** health checks at `/actuator/health`

**Key Modules:**
- **Controllers:** Auth, User, Wallet, Order, Market, Asset, Dashboard, Transaction, Price, SystemHealth, Simulator (admin)
- **Services:** Auth, User, Wallet, CashDeposit (with $1000/24h limit), Order, Market, Asset, Dashboard, Binance integration
- **Repositories:** JPA repositories for all entities
- **Models:** UserAccount, Asset, Market, Balance, Order, Trade, CashDeposit, Transaction

**Database Schema:**
- 13 Flyway migrations (V1-V13)
- Seeded: Top 100 crypto assets, BTC/USDT and ETH/USDT markets, cross-pairs
- Tables: `user_account`, `asset`, `market`, `balance`, `order`, `trade`, `cash_deposit`, `transaction`, `price_tick`, `market_tick`, `market_trade`

**External Integrations:**
- **Binance API:** Real-time price fetching (ticker, klines) with in-memory caching (5-10s TTL)
- **Scheduled job:** Fetches prices every 30s for BTC, ETH, SOL (configurable)

**Security:**
- JWT tokens stored in `localStorage` (frontend)
- Password hashing: BCrypt strength 10
- Rate limiting: Orders (30/min), Deposits (10/min), Withdrawals (5/min)
- CORS: **Hardcoded to `http://localhost:5173`** ⚠️

**Optional Services:**
- Redis: Used for caching (markets, ticker, order book, portfolio). App works without it (caching disabled).
- Kafka: Used for event streaming (orders, trades, market ticks). App works without it (events not published).

### Frontend Architecture

**Framework & Stack:**
- **React 19.2** with Vite 7.2
- **React Router 6.28** for navigation
- **Axios** for API calls
- **Recharts** for portfolio pie chart and price history
- **Context API** for auth state

**Pages & Routes:**
- `/login` - Login (login or email + password)
- `/register` - Registration (login, email, password)
- `/dashboard` - Portfolio summary, holdings table, pie chart, price trends, system status
- `/assets` - Asset list with Binance prices (searchable, sortable, paginated)
- `/assets/:symbol` - Asset detail page with 24h stats
- `/wallet` - Wallet balances view
- `/deposit` - Deposit USDT (with $1000/24h limit display)
- `/trade` - Trading page (market orders, pair selection, price charts)
- `/transactions` - Transaction history
- `/account` - Account settings (change login, email, password)

**API Client:**
- Base URL: `VITE_API_BASE_URL` env var (default: `http://localhost:8080`)
- Auto-adds JWT token from `localStorage` to all requests
- Auto-redirects to `/login` on 401

**State Management:**
- `AuthContext` for user/auth state
- Local state for page-specific data
- No global state library (Redux/Zustand)

**Styling:**
- Custom CSS (`index.css`, 879 lines)
- Responsive design with mobile menu
- No CSS framework (Tailwind/Bootstrap)

### Infrastructure

**Docker Compose:**
- `docker-compose.yml` at root: PostgreSQL, Redis, Zookeeper, Kafka
- Services use environment variables: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- No backend/frontend services in compose (only infrastructure)

**CI/CD:**
- **Jenkins** pipeline (`Jenkinsfile`)
- Stages: Build & Test (unit tests only), Package (JAR), Docker Build (optional)
- **Does NOT:** Run integration tests, build frontend, deploy
- Jenkins setup: `devops/jenkins/docker-compose.yml`

**Build Artifacts:**
- Backend: `backend/Dockerfile` (multi-stage Maven build)
- Frontend: **No Dockerfile** ❌ (only `dist/` folder from `npm run build`)

**Environment Configuration:**
- Backend: `application.yml` + env vars (no `.env.example`)
- Frontend: `VITE_API_BASE_URL` (no `.env.example`)
- Docker Compose: Uses env vars but no `.env.example`

### Current User Journey (End-to-End)

1. **Register/Login:**
   - User registers with login, email, password (min 8 chars, number, special char)
   - JWT token stored in `localStorage`
   - User redirected to `/dashboard`

2. **Dashboard:**
   - Shows portfolio summary (total value, USDT balance, unrealized/realized PnL)
   - Holdings table with current prices (from Binance)
   - Portfolio pie chart (includes USDT as separate slice)
   - Price trends chart (24h history)
   - System status (API, DB, Redis, Kafka)

3. **Deposit:**
   - User can deposit USDT (fake money)
   - Enforced limit: **$1000 per rolling 24 hours**
   - Updates `user_account.cash_balance_usd` and `balance` table
   - UI shows remaining limit

4. **Trading:**
   - Select trading pair (BTC/USDT, ETH/USDT, cross-pairs)
   - Market orders only (BUY/SELL)
   - Real-time price from SSE stream (USDT pairs) or REST polling (cross-pairs)
   - Price history chart (24h, 7d, 30d)
   - Order executes immediately (no order book matching yet)

5. **Portfolio View:**
   - Dashboard pie chart includes USDT balance
   - Holdings table shows all assets with quantities, prices, market values, PnL
   - Prices fetched from Binance API

6. **Account Settings:**
   - Change login, email, password
   - Password change requires current password

7. **Admin Page:**
   - **NOT IMPLEMENTED IN FRONTEND** ❌
   - Backend endpoints exist: `/api/admin/simulator/*` (start/stop/status)
   - System status is shown on dashboard, not a separate admin page

---

## B. What's Missing / Broken

### Critical (Blocks Deployment)

1. **CORS Hardcoded to Localhost**
   - **File:** `backend/src/main/java/com/cryptoexchange/backend/config/SecurityConfig.java:93`
   - **Issue:** `configuration.setAllowedOrigins(List.of("http://localhost:5173"));`
   - **Impact:** Frontend cannot connect from any other origin (production domain, VPS, etc.)
   - **Fix:** Make CORS origin configurable via `CORS_ALLOWED_ORIGINS` env var

2. **No Frontend Dockerfile**
   - **Issue:** Frontend has no containerization
   - **Impact:** Cannot deploy frontend as container, must build on host or use different method
   - **Fix:** Create `frontend/Dockerfile` (multi-stage: build with Node, serve with nginx)

3. **No Environment Variable Examples**
   - **Issue:** No `.env.example` files for backend or frontend
   - **Impact:** Developers/recruiters don't know what env vars are needed
   - **Fix:** Create `.env.example` files with all required variables

4. **Docker Compose Missing Backend/Frontend Services**
   - **Issue:** `docker-compose.yml` only has infrastructure (Postgres, Redis, Kafka)
   - **Impact:** Cannot run full stack with `docker-compose up`
   - **Fix:** Add backend and frontend services to compose file

5. **Kafka Configuration May Cause Startup Failure**
   - **File:** `backend/src/main/resources/application.yml:38`
   - **Issue:** `KAFKA_BOOTSTRAP_SERVERS` is required (no default)
   - **Impact:** Backend may fail to start if Kafka is not available and env var is not set
   - **Fix:** Make Kafka truly optional (conditional bean configuration or default to empty string)

### High Priority (Blocks Demo)

6. **No Admin Page in Frontend**
   - **Issue:** Admin endpoints exist but no UI
   - **Impact:** Cannot control simulator or view admin features
   - **Fix:** Create `/admin` page with system status and simulator controls

7. **No Demo User Seeding**
   - **Issue:** No script to create demo user for recruiters
   - **Impact:** Recruiters must register manually
   - **Fix:** Add Flyway migration or startup script to seed demo user (login: `demo`, password: `demo123!`)

8. **No Health Check Endpoint for Frontend**
   - **Issue:** Frontend is static files, no health endpoint
   - **Impact:** Cannot verify frontend is serving correctly
   - **Fix:** Add simple `health.html` or use nginx status

9. **No Production Build Instructions**
   - **Issue:** READMEs only cover local dev
   - **Impact:** No clear path to deploy
   - **Fix:** Add deployment section to main README

### Medium Priority (Nice to Have)

10. **No HTTPS Configuration**
    - **Issue:** No SSL/TLS setup for production
    - **Impact:** Insecure in production
    - **Fix:** Add nginx reverse proxy with Let's Encrypt

11. **No Logging Configuration**
    - **Issue:** Default Spring Boot logging (console only)
    - **Impact:** Hard to debug in production
    - **Fix:** Add logback configuration with file rotation

12. **No Database Backup Strategy**
    - **Issue:** No backup scripts or documentation
    - **Impact:** Data loss risk
    - **Fix:** Add backup script and restore instructions

13. **Frontend Build Output Not Optimized for Production**
    - **Issue:** No nginx config for serving static files
    - **Impact:** Suboptimal performance
    - **Fix:** Add nginx config with compression, caching

---

## C. Top Risks

### Security Risks

1. **JWT Secret Default Value**
   - **Risk:** Default secret in `application.yml` is weak and public
   - **Impact:** Tokens can be forged if default is used
   - **Mitigation:** Require `JWT_SECRET` env var in production, fail startup if default

2. **CORS Misconfiguration**
   - **Risk:** Hardcoded origin allows only localhost
   - **Impact:** Production frontend cannot connect
   - **Mitigation:** Make CORS configurable, validate origins

3. **No Rate Limiting on Auth Endpoints**
   - **Risk:** `/api/auth/login` and `/api/auth/register` have no rate limits
   - **Impact:** Brute force attacks possible
   - **Mitigation:** Add rate limiting to auth endpoints

4. **Password Requirements Not Enforced on Backend**
   - **Risk:** Frontend validates, but backend may accept weak passwords
   - **Impact:** Weak passwords if API called directly
   - **Mitigation:** Add `@Pattern` validation to password DTOs

### Reliability Risks

5. **Binance API Dependency**
   - **Risk:** All prices depend on Binance API availability
   - **Impact:** App shows "—" for prices if Binance is down
   - **Mitigation:** Already has fallback to `PriceTick` table (good), but could add more graceful degradation

6. **Database Migration Failure**
   - **Risk:** Flyway migrations may fail on existing data
   - **Impact:** App won't start
   - **Mitigation:** Test migrations on clean and existing databases

7. **No Database Connection Pooling Tuning**
   - **Risk:** Default HikariCP settings may not be optimal
   - **Impact:** Connection exhaustion under load
   - **Mitigation:** Configure pool size based on expected load

### Demo-Readiness Risks

8. **No Seeded Demo Data**
   - **Risk:** Recruiters see empty app
   - **Impact:** Poor first impression
   - **Mitigation:** Seed demo user with sample portfolio

9. **Complex Setup Requirements**
   - **Risk:** Too many steps to get running
   - **Impact:** Recruiters give up
   - **Mitigation:** Single-command deployment (docker-compose up)

10. **No Monitoring/Alerting**
    - **Risk:** Issues go unnoticed
    - **Impact:** App down without knowing
    - **Mitigation:** Add health check monitoring (simple ping endpoint is enough for MVP)

---

## D. Quick Wins (1-2 Days)

### Day 1: Unblock Deployment

1. **Fix CORS Configuration** (2 hours)
   - Add `CORS_ALLOWED_ORIGINS` env var
   - Update `SecurityConfig.java` to read from env (comma-separated list)
   - Default to `http://localhost:5173` if not set

2. **Create Frontend Dockerfile** (2 hours)
   - Multi-stage: Node for build, nginx for serving
   - Copy `dist/` to nginx html directory
   - Expose port 80
   - Accept `VITE_API_BASE_URL` as build arg

3. **Create .env.example Files** (1 hour)
   - `backend/.env.example` with all required vars
   - `frontend/.env.example` with `VITE_API_BASE_URL`
   - Root `.env.example` for docker-compose

4. **Add Backend/Frontend to Docker Compose** (2 hours)
   - Add `backend` service (build from Dockerfile, depends on postgres/redis/kafka)
   - Add `frontend` service (build from Dockerfile, depends on backend)
   - Configure networking and env vars

5. **Make Kafka Truly Optional** (1 hour)
   - Add default empty string for `KAFKA_BOOTSTRAP_SERVERS`
   - Use `@ConditionalOnProperty` for Kafka beans
   - Test startup without Kafka

**Total: ~8 hours (1 day)**

### Day 2: Demo Readiness

6. **Seed Demo User** (1 hour)
   - Add Flyway migration `V14__seed_demo_user.sql`
   - Create user: login=`demo`, email=`demo@example.com`, password=`demo123!`
   - Optionally seed demo portfolio (some BTC, ETH)

7. **Create Admin Page** (3 hours)
   - Add `/admin` route (protected, check for admin role or simple flag)
   - Show system health (reuse `SystemHealthController`)
   - Add simulator controls (start/stop/status) if enabled
   - Link from dashboard or header

8. **Add Production README Section** (1 hour)
   - Document docker-compose deployment
   - Document environment variables
   - Document health check endpoints

9. **Test Full Stack Locally** (2 hours)
   - Run `docker-compose up`
   - Verify all services start
   - Test user journey end-to-end
   - Fix any issues

**Total: ~7 hours (1 day)**

---

## E. Next Wins (3-7 Days)

### Week 1: Production Hardening

10. **Add HTTPS with Let's Encrypt** (1 day)
    - Create nginx reverse proxy service in docker-compose
    - Add certbot container for Let's Encrypt
    - Configure SSL termination
    - Update CORS to allow HTTPS origin

11. **Add Logging Configuration** (4 hours)
    - Configure logback with file appender
    - Add log rotation (daily, keep 30 days)
    - Log to `/var/log/crypto-exchange/` in container

12. **Add Database Backup Script** (2 hours)
    - Create `scripts/backup-db.sh`
    - Use `pg_dump` to backup PostgreSQL
    - Add restore script
    - Document backup/restore process

13. **Add Rate Limiting to Auth Endpoints** (2 hours)
    - Extend rate limit config to include `/api/auth/**`
    - Set reasonable limits (10 login attempts/min, 5 register/min)

14. **Add Password Validation on Backend** (1 hour)
    - Add `@Pattern` to `RegisterRequest` and `ChangePasswordRequest`
    - Enforce min 8 chars, number, special char

15. **Add Health Check Monitoring** (2 hours)
    - Create simple monitoring script (curl `/api/system/health`)
    - Add to cron or systemd timer
    - Send alerts on failure (email or webhook)

**Total: ~2.5 days**

### Week 2: Enhancement

16. **Add Frontend Health Check** (1 hour)
    - Create `health.html` endpoint served by nginx
    - Return 200 OK with timestamp

17. **Optimize Frontend Build** (2 hours)
    - Add nginx config with gzip, caching headers
    - Optimize asset loading
    - Add service worker for offline support (optional)

18. **Add Database Connection Pool Tuning** (1 hour)
    - Configure HikariCP pool size (min 5, max 20)
    - Add connection timeout settings

19. **Add Graceful Shutdown** (1 hour)
    - Configure Spring Boot graceful shutdown (30s timeout)
    - Ensure in-flight requests complete

20. **Document Deployment Options** (2 hours)
    - Write detailed deployment guide for each option
    - Add troubleshooting section
    - Add performance tuning tips

**Total: ~1 day**

---

## F. Recruiter-Ready Demo Plan

### Option 1: Docker Compose on VPS + Nginx + HTTPS (Simplest)

**Best for:** Quick demo, low cost, easy setup

**Architecture:**
```
Internet → Nginx (HTTPS) → Frontend (port 80) + Backend (port 8080)
                          → PostgreSQL, Redis, Kafka (internal)
```

**What to Implement:**

1. **Files to Add/Change:**
   - `docker-compose.prod.yml` - Production compose with all services
   - `nginx/nginx.conf` - Reverse proxy config
   - `nginx/Dockerfile` - Nginx container
   - `scripts/setup-ssl.sh` - Let's Encrypt setup
   - `.env.production.example` - Production env vars template

2. **Setup Steps:**
   ```bash
   # On VPS (Ubuntu 22.04)
   git clone <repo>
   cd crypto-exchange-sim
   
   # Copy and configure env
   cp .env.production.example .env
   # Edit .env with your values
   
   # Start services
   docker-compose -f docker-compose.prod.yml up -d
   
   # Setup SSL (one-time)
   ./scripts/setup-ssl.sh
   
   # Verify
   curl https://your-domain.com/api/system/health
   ```

3. **Secrets Handling:**
   - Store `.env` file securely (not in git)
   - Use Docker secrets or env file mounted as volume
   - Rotate JWT_SECRET on first deploy

4. **Demo User:**
   - Seeded by Flyway migration
   - Login: `demo`
   - Password: `demo123!`
   - Pre-loaded with 500 USDT balance

5. **Monitoring:**
   - Health endpoint: `https://your-domain.com/api/system/health`
   - Frontend: `https://your-domain.com`
   - Swagger: `https://your-domain.com/swagger-ui.html`

**Cost:** ~$5-10/month (VPS: DigitalOcean/Hetzner)

**Pros:**
- Simple, single command to deploy
- HTTPS out of the box
- Easy to update (git pull + docker-compose restart)

**Cons:**
- Single server (no high availability)
- Manual SSL renewal (can automate with cron)

---

### Option 2: Container Registry + Cloud Host (Still Simple)

**Best for:** Scalability, managed services

**Architecture:**
```
Internet → Cloud Load Balancer → Frontend Container (multiple instances)
                               → Backend Container (multiple instances)
                               → Managed PostgreSQL (RDS/Cloud SQL)
                               → Managed Redis (ElastiCache/Cloud Memorystore)
```

**What to Implement:**

1. **Files to Add/Change:**
   - `docker-compose.build.yml` - Build and push images
   - `.github/workflows/deploy.yml` - GitHub Actions for CI/CD
   - `scripts/build-and-push.sh` - Build and push to registry
   - Update `docker-compose.yml` to use image tags

2. **Setup Steps:**
   ```bash
   # Build and push images
   docker build -t your-registry/crypto-backend:latest ./backend
   docker build -t your-registry/crypto-frontend:latest ./frontend
   docker push your-registry/crypto-backend:latest
   docker push your-registry/crypto-frontend:latest
   
   # Deploy to cloud (example: Google Cloud Run)
   gcloud run deploy crypto-backend --image your-registry/crypto-backend:latest
   gcloud run deploy crypto-frontend --image your-registry/crypto-frontend:latest
   ```

3. **Secrets Handling:**
   - Use cloud secrets manager (AWS Secrets Manager, GCP Secret Manager)
   - Inject secrets as env vars in container config
   - Never commit secrets

4. **Demo User:**
   - Same as Option 1 (seeded by migration)
   - Or use cloud SQL initialization script

5. **Monitoring:**
   - Cloud provider monitoring (CloudWatch, Stackdriver)
   - Health checks via load balancer
   - Logs via cloud logging

**Cost:** ~$20-50/month (depends on cloud provider and usage)

**Pros:**
- Scalable (auto-scaling containers)
- Managed databases (backups, high availability)
- Built-in monitoring and logging

**Cons:**
- More complex setup
- Vendor lock-in
- Higher cost

---

### Option 3: Kubernetes (Only If Needed)

**Best for:** Enterprise, multi-region, complex requirements

**Architecture:**
```
Internet → Ingress (nginx/traefik) → Frontend Pods (Deployment)
                                   → Backend Pods (Deployment)
                                   → PostgreSQL (StatefulSet or managed)
                                   → Redis (StatefulSet or managed)
                                   → Kafka (StatefulSet or managed)
```

**What to Implement:**

1. **Files to Add/Change:**
   - `k8s/namespace.yaml` - Namespace
   - `k8s/backend-deployment.yaml` - Backend deployment
   - `k8s/frontend-deployment.yaml` - Frontend deployment
   - `k8s/postgres-statefulset.yaml` - PostgreSQL (or use managed)
   - `k8s/redis-deployment.yaml` - Redis (or use managed)
   - `k8s/ingress.yaml` - Ingress with TLS
   - `k8s/configmap.yaml` - ConfigMaps for non-secret config
   - `k8s/secrets.yaml` - Secrets (or use external secrets operator)

2. **Setup Steps:**
   ```bash
   # Create namespace
   kubectl apply -f k8s/namespace.yaml
   
   # Create secrets
   kubectl create secret generic app-secrets --from-env-file=.env -n crypto-exchange
   
   # Deploy services
   kubectl apply -f k8s/ -n crypto-exchange
   
   # Verify
   kubectl get pods -n crypto-exchange
   ```

3. **Secrets Handling:**
   - Use Kubernetes Secrets (base64 encoded)
   - Or use External Secrets Operator (syncs from cloud secrets manager)
   - Never commit secrets

4. **Demo User:**
   - Same as Option 1 (seeded by migration)
   - Or use init container to seed data

5. **Monitoring:**
   - Prometheus + Grafana (optional)
   - Kubernetes health checks (liveness/readiness probes)
   - Log aggregation (Fluentd/Logstash)

**Cost:** ~$50-200/month (depends on cluster size and cloud provider)

**Pros:**
- High availability (multiple replicas)
- Auto-scaling (HPA)
- Service mesh support (Istio/Linkerd)
- Multi-region deployment

**Cons:**
- High complexity
- Steep learning curve
- Overkill for simple demo
- Higher operational overhead

**Recommendation:** Only use if you need multi-region or enterprise features. For recruiter demo, Option 1 is sufficient.

---

## G. Recommendation

### For "Recruiter Can Click and Test" Goal

**Choose Option 1 (Docker Compose + Nginx + HTTPS)**

**Why:**
1. **Fastest to implement** (1-2 days)
2. **Lowest cost** (~$5-10/month)
3. **Easiest to maintain** (single command updates)
4. **Sufficient for demo** (handles 10-100 concurrent users)
5. **HTTPS included** (professional appearance)

**What to Do Next:**

1. **Immediate (This Week):**
   - Fix CORS configuration (make it env-based)
   - Create frontend Dockerfile
   - Add backend/frontend to docker-compose
   - Create .env.example files
   - Test full stack locally

2. **Next Week:**
   - Create `docker-compose.prod.yml` with nginx
   - Add SSL setup script
   - Seed demo user
   - Deploy to VPS (DigitalOcean/Hetzner)
   - Test end-to-end

3. **Before Demo:**
   - Verify all features work
   - Test with demo user
   - Document login credentials
   - Set up basic monitoring (health check ping)

### Suggested Phase-by-Phase Roadmap

**Phase 1: Unblock Deployment (Week 1)**
- ✅ Fix CORS
- ✅ Create frontend Dockerfile
- ✅ Add services to docker-compose
- ✅ Create .env.example files
- ✅ Make Kafka optional
- **Commit:** "Fix deployment blockers: CORS, Dockerfiles, env examples"

**Phase 2: Demo Readiness (Week 1-2)**
- ✅ Seed demo user
- ✅ Create admin page
- ✅ Add production README
- ✅ Test full stack
- **Commit:** "Add demo user and admin page"

**Phase 3: Production Deployment (Week 2)**
- ✅ Create docker-compose.prod.yml
- ✅ Add nginx reverse proxy
- ✅ Add SSL setup script
- ✅ Deploy to VPS
- **Commit:** "Add production deployment configuration"

**Phase 4: Hardening (Week 3, Optional)**
- ✅ Add logging configuration
- ✅ Add database backup script
- ✅ Add rate limiting to auth
- ✅ Add password validation on backend
- **Commit:** "Add production hardening: logging, backups, security"

**Phase 5: Monitoring (Week 3, Optional)**
- ✅ Add health check monitoring
- ✅ Add basic alerting
- **Commit:** "Add monitoring and alerting"

---

## H. Changes Made (If Any)

**Changes implemented during audit:**

1. **Fixed CORS Configuration (SecurityConfig.java)**
   - **File:** `backend/src/main/java/com/cryptoexchange/backend/config/SecurityConfig.java`
   - **Change:** Made CORS allowed origins configurable via `CORS_ALLOWED_ORIGINS` environment variable
   - **Details:**
     - Added `@Value("${CORS_ALLOWED_ORIGINS:http://localhost:5173}")` to inject env var with default
     - Updated `corsConfigurationSource()` to parse comma-separated origins from env var
     - Default remains `http://localhost:5173` if env var not set (backward compatible)
   - **Impact:** Unblocks deployment to any domain (just set `CORS_ALLOWED_ORIGINS` env var)
   - **Risk:** Low - backward compatible, only adds flexibility

2. **Documented .env.example Files**
   - **Files to create:**
     - `backend/.env.example` - All backend environment variables with descriptions
     - `frontend/.env.example` - Frontend API base URL
     - `.env.example` (root) - Docker Compose variables
   - **Note:** These files are blocked by `.gitignore` pattern, so they should be created manually or `.gitignore` should be updated to allow `.env.example` files
   - **Content:** See Appendix below for exact content of each file

---

## Appendix: File Reference

### Key Files to Modify for Deployment

**Backend:**
- `backend/src/main/java/com/cryptoexchange/backend/config/SecurityConfig.java` - CORS configuration
- `backend/src/main/resources/application.yml` - Kafka optional config
- `backend/Dockerfile` - Already exists, may need env var updates

**Frontend:**
- `frontend/Dockerfile` - **Create this**
- `frontend/vite.config.js` - Already configured for env vars

**Infrastructure:**
- `docker-compose.yml` - Add backend/frontend services
- `docker-compose.prod.yml` - **Create this** (production version)
- `nginx/nginx.conf` - **Create this** (reverse proxy)
- `nginx/Dockerfile` - **Create this**

**Documentation:**
- `README.md` - **Create or update** (add deployment section)
- `.env.example` files - **Create these**

**Database:**
- `backend/src/main/resources/db/migration/V14__seed_demo_user.sql` - **Create this**

**Environment Files:**
- `backend/.env.example` - **Create this** (see content below)
- `frontend/.env.example` - **Create this** (see content below)
- `.env.example` (root) - **Create this** (see content below)

---

## Appendix: .env.example File Contents

### backend/.env.example
```bash
# Database Configuration
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/cryptoexchange
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# Redis Configuration (optional - app works without it)
REDIS_HOST=localhost
REDIS_PORT=6379

# Kafka Configuration (optional - app works without it)
# Leave empty or unset to disable Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# JWT Configuration
# Generate a secure secret using: openssl rand -base64 32
# IMPORTANT: Change this in production!
JWT_SECRET=your-256-bit-secret-key-change-this-in-production-minimum-32-characters
JWT_EXPIRATION=86400000  # 24 hours in milliseconds

# CORS Configuration
# Comma-separated list of allowed origins (e.g., "http://localhost:5173,https://yourdomain.com")
# Default: http://localhost:5173
CORS_ALLOWED_ORIGINS=http://localhost:5173

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

# Price Fetcher (Binance API)
PRICE_FETCHER_SYMBOLS=BTC,ETH,SOL
PRICE_FETCHER_INTERVAL_MS=30000
```

### frontend/.env.example
```bash
# Frontend API Base URL
# Default: http://localhost:8080
VITE_API_BASE_URL=http://localhost:8080
```

### .env.example (root, for docker-compose.yml)
```bash
# Docker Compose Environment Variables
# Used by docker-compose.yml for infrastructure services

# PostgreSQL Configuration
POSTGRES_DB=cryptoexchange
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# Note: Backend and frontend have their own .env.example files
# See backend/.env.example and frontend/.env.example
```

---

**End of Audit**
