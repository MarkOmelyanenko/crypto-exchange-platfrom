.PHONY: help jenkins-up jenkins-down jenkins-logs jenkins-password ci-local backend-test backend-build backend-docker frontend-build clean

help: ## Show this help message
	@echo 'Usage: make [target]'
	@echo ''
	@echo 'Available targets:'
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  %-20s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

jenkins-up: ## Start Jenkins CI server
	cd devops/jenkins && docker-compose -f docker-compose.jenkins.yml up -d
	@echo "Jenkins is starting..."
	@echo "Access Jenkins at http://localhost:8081"
	@echo "Get initial password with: make jenkins-password"

jenkins-down: ## Stop Jenkins CI server
	cd devops/jenkins && docker-compose -f docker-compose.jenkins.yml down

jenkins-logs: ## Show Jenkins logs
	docker logs -f crypto-jenkins

jenkins-password: ## Get Jenkins initial admin password
	@docker exec crypto-jenkins cat /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null || echo "Jenkins container not running. Start it with: make jenkins-up"

ci-local: ## Run CI pipeline steps locally (without Jenkins)
	@echo "Running CI pipeline locally..."
	@echo "Step 1: Backend tests..."
	cd backend && mvn -B -DskipTests=false clean test
	@echo "Step 2: Backend package..."
	cd backend && mvn -B -DskipTests package
	@echo "Step 3: Backend Docker build..."
	cd backend && docker build -t crypto-exchange-backend:latest .
	@echo "Step 4: Starting services for smoke test..."
	docker-compose -f devops/docker-compose.ci.yml up -d
	@echo "Waiting for services to be healthy..."
	@sleep 10
	@echo "Checking backend health..."
	@for i in $$(seq 1 30); do \
		if curl -f -s http://localhost:8080/actuator/health > /dev/null 2>&1; then \
			echo "Backend is UP!"; \
			curl -s http://localhost:8080/actuator/health | jq '.' || curl -s http://localhost:8080/actuator/health; \
			break; \
		fi; \
		echo "Attempt $$i/30: Waiting for backend..."; \
		sleep 2; \
	done
	@echo "Tearing down test containers..."
	docker-compose -f devops/docker-compose.ci.yml down -v
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
	docker-compose -f devops/docker-compose.ci.yml down -v 2>/dev/null || true
	docker rmi crypto-exchange-backend:latest 2>/dev/null || true
	docker rmi crypto-exchange-frontend:latest 2>/dev/null || true
	@echo "Cleanup complete"
