# DevOps Infrastructure

This directory contains CI/CD infrastructure and configuration files.

## Structure

- `jenkins/` - Jenkins CI server setup for local and remote usage
- `docker-compose.ci.yml` - Docker Compose configuration for CI smoke tests

## CI/CD Pipeline

The project uses Jenkins for continuous integration. The pipeline is defined in the root `Jenkinsfile`.

### Pipeline Stages

1. **Checkout** - Retrieves source code from repository
2. **Backend: Build & Test** - Compiles and runs Maven tests
3. **Backend: Package** - Creates JAR artifact
4. **Backend: Docker Build** - Builds Docker image for backend
5. **Frontend: Build & Test** (conditional) - Builds frontend if present
6. **Frontend: Docker Build** (conditional) - Builds frontend Docker image if present
7. **Smoke Test** - Starts services and verifies health endpoint

### Running CI Locally

#### Option 1: Using Jenkins (Recommended)

1. Start Jenkins:
   ```bash
   cd devops/jenkins
   docker-compose -f docker-compose.jenkins.yml up -d
   ```

2. Access Jenkins at http://localhost:8081

3. Create a pipeline job pointing to the `Jenkinsfile`

4. Run the pipeline

See `jenkins/README.md` for detailed instructions.

#### Option 2: Using Makefile

From the project root:
```bash
make ci-local
```

This will run the CI pipeline steps locally (requires Docker and Maven).

### CI Docker Compose

The `docker-compose.ci.yml` file is used during smoke tests. It:
- Uses different ports than local development to avoid conflicts
- Includes minimal services needed for backend health checks
- Automatically tears down after tests complete

**Note**: This file uses ports 5433, 6380, 9093, and 8080 to avoid conflicts with local development services.

## Local Development

Local development uses the root `docker-compose.yml` which runs on standard ports:
- PostgreSQL: 5432
- Redis: 6379
- Kafka: 9092
- Backend: 8080 (when run from IntelliJ)

The CI compose file uses different ports, so both can run simultaneously if needed.

## Environment Profiles

- `dev` - Local development (default)
- `ci` - CI/CD pipeline (used during smoke tests)

The `ci` profile ensures actuator endpoints are accessible for health checks without affecting production security.
