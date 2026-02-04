.PHONY: help jenkins-up jenkins-down jenkins-logs jenkins-password ci-local backend-test backend-build backend-docker frontend-build clean

help: ## Show this help message
	@echo 'Usage: make [target]'
	@echo ''
	@echo 'Available targets:'
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  %-20s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

jenkins-up: ## Start Jenkins CI server
	cd devops/jenkins && docker compose up -d
	@echo "Jenkins is starting..."
	@echo "Access Jenkins at http://localhost:8081"
	@echo "Get initial password with: make jenkins-password"

jenkins-down: ## Stop Jenkins CI server
	cd devops/jenkins && docker compose down

jenkins-logs: ## Show Jenkins logs
	docker logs -f crypto-jenkins

jenkins-password: ## Get Jenkins initial admin password
	@docker exec crypto-jenkins cat /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null || echo "Jenkins container not running. Start it with: make jenkins-up"

ci-local: ## Run CI pipeline steps locally (without Jenkins)
	@echo "Running CI pipeline locally..."
	@echo "Step 1: Backend tests..."
	cd backend && mvn -B -ntp -DskipTests=false clean test
	@echo "Step 2: Backend package..."
	cd backend && mvn -B -ntp -DskipTests package
	@echo "Step 3: Backend Docker build (if Dockerfile exists)..."
	@if [ -f backend/Dockerfile ]; then \
		cd backend && docker build -t crypto-backend:ci .; \
	else \
		echo "Dockerfile not found, skipping Docker build"; \
	fi
	@echo "CI pipeline completed!"

backend-test: ## Run backend tests
	cd backend && mvn -B -DskipTests=false test

backend-build: ## Build backend JAR
	cd backend && mvn -B -DskipTests package

backend-docker: ## Build backend Docker image
	cd backend && docker build -t crypto-exchange-backend:latest .

frontend-build: ## Build frontend (if exists)
	@if [ -d "frontend" ]; then \
		cd frontend && npm ci && npm run build; \
	else \
		echo "Frontend directory not found"; \
	fi

clean: ## Clean up Docker images and containers
	docker rmi crypto-backend:ci 2>/dev/null || true
	docker rmi crypto-exchange-backend:latest 2>/dev/null || true
	@echo "Cleanup complete"
