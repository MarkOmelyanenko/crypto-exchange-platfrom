.PHONY: help jenkins-up jenkins-down jenkins-logs jenkins-password ci-local backend-test backend-build backend-docker frontend-lint frontend-build frontend-docker clean

help: ## Show this help message
	@echo 'Usage: make [target]'
	@echo ''
	@echo 'Available targets:'
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  %-20s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

# ── Jenkins ─────────────────────────────────────────────

jenkins-up: ## Start Jenkins CI server
	cd devops/jenkins && docker compose up -d --build
	@echo "Jenkins is starting..."
	@echo "Access Jenkins at http://localhost:8081"
	@echo "Get initial password with: make jenkins-password"

jenkins-down: ## Stop Jenkins CI server
	cd devops/jenkins && docker compose down

jenkins-logs: ## Show Jenkins logs
	docker logs -f crypto-jenkins

jenkins-password: ## Get Jenkins initial admin password
	@docker exec crypto-jenkins cat /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null || echo "Jenkins container not running. Start it with: make jenkins-up"

# ── CI (local) ──────────────────────────────────────────

ci-local: backend-test backend-build frontend-lint frontend-build ## Run full CI pipeline locally (without Jenkins)
	@echo ""
	@echo "CI pipeline completed!"

# ── Backend ─────────────────────────────────────────────

backend-test: ## Run backend tests
	cd backend && ./mvnw -B -ntp clean test

backend-build: ## Build backend JAR
	cd backend && ./mvnw -B -ntp -DskipTests package

backend-docker: ## Build backend Docker image
	cd backend && docker build -t crypto-backend:latest .

# ── Frontend ────────────────────────────────────────────

frontend-lint: ## Lint frontend code
	cd frontend && npm ci --no-audit --no-fund && npm run lint

frontend-build: ## Build frontend production bundle
	cd frontend && npm ci --no-audit --no-fund && npm run build

frontend-docker: ## Build frontend Docker image
	cd frontend && docker build -t crypto-frontend:latest .

# ── Cleanup ─────────────────────────────────────────────

clean: ## Clean up Docker images and build artifacts
	docker rmi crypto-backend:latest 2>/dev/null || true
	docker rmi crypto-frontend:latest 2>/dev/null || true
	cd backend && ./mvnw -B -ntp clean 2>/dev/null || true
	rm -rf frontend/dist 2>/dev/null || true
	@echo "Cleanup complete"
