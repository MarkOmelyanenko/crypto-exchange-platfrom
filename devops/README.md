# DevOps Infrastructure

This directory contains CI/CD infrastructure and configuration files.

## Structure

- `jenkins/` — Jenkins CI server setup (custom image with JDK 21 + Node.js 20 + Docker CLI)

## CI/CD Pipeline

The project uses Jenkins for continuous integration of both backend and frontend. The pipeline is defined in the root `Jenkinsfile`.

Pipeline stages:
1. **Backend: Test** — Unit tests via Maven
2. **Backend: Package** — JAR artifact
3. **Frontend: Install** — npm dependencies
4. **Frontend: Lint** — ESLint checks
5. **Frontend: Build** — Production bundle
6. **Docker Build** — Backend + Frontend images (parallel, optional)

See `CI.md` at the repo root for pipeline details.

## Running Jenkins Locally

1. Start Jenkins:
   ```bash
   cd devops/jenkins
   docker compose up -d --build
   ```

2. Access Jenkins at http://localhost:8081

3. See `jenkins/README.md` for detailed setup instructions

## Local Development

Local development uses the root `docker-compose.yml` which runs on standard ports:
- PostgreSQL: 5432
- Redis: 6379
- Kafka: 9092
- Backend: 8080 (when run from IntelliJ)
- Frontend: 5173 (when run via `npm run dev`)
