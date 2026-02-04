# DevOps Infrastructure

This directory contains CI/CD infrastructure and configuration files.

## Structure

- `jenkins/` - Jenkins CI server setup for local usage

## CI/CD Pipeline

The project uses Jenkins for continuous integration. The pipeline is defined in the root `Jenkinsfile`.

See `CI.md` at the repo root for pipeline details.

## Running Jenkins Locally

1. Start Jenkins:
   ```bash
   cd devops/jenkins
   docker compose up -d
   ```

2. Access Jenkins at http://localhost:8081

3. See `jenkins/README.md` for detailed setup instructions

## Local Development

Local development uses the root `docker-compose.yml` which runs on standard ports:
- PostgreSQL: 5432
- Redis: 6379
- Kafka: 9092
- Backend: 8080 (when run from IntelliJ)
