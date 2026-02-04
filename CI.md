# CI/CD Pipeline

This project uses Jenkins for continuous integration.

## Pipeline Overview

The Jenkins pipeline (`Jenkinsfile`) performs:

- **Checkout**: Retrieves source code from repository
- **Build & Test**: Compiles backend and runs unit tests
- **Package**: Creates JAR artifact and archives it
- **Docker Build** (optional): Builds Docker image if Dockerfile exists and Docker is available

## What the Pipeline Does NOT Do

- ❌ Start docker-compose services (Postgres, Redis, Kafka)
- ❌ Run integration tests requiring external services
- ❌ Deploy to any environment
- ❌ Build frontend (backend-only pipeline)

## Running Jenkins Locally

See `devops/jenkins/README.md` for instructions on running Jenkins locally using Docker Compose.

## Local Development

The CI pipeline does not interfere with local development:
- Local dev uses `docker-compose.yml` at repo root
- CI runs only unit tests that don't require external services
- Backend can run directly in IntelliJ without CI dependencies
