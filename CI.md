# CI/CD Pipeline

This project uses Jenkins for continuous integration of both backend and frontend.

## Pipeline Overview

The Jenkins pipeline (`Jenkinsfile`) performs:

### Backend
- **Test** — Compiles and runs unit tests via Maven wrapper (`./mvnw`)
  - Pure unit tests (Mockito) and `@WebMvcTest` controller tests run
  - Integration tests requiring DB/Redis/Kafka are `@Disabled` and skipped automatically
- **Package** — Creates JAR artifact and archives it

### Frontend
- **Install** — Installs npm dependencies (`npm ci`)
- **Lint** — Runs ESLint checks (`npm run lint`)
- **Build** — Produces production bundle (`npm run build`), archives `dist/`

### Docker (optional)
- **Backend Image** — Builds `crypto-backend` Docker image (multi-stage: Maven build → JRE runtime)
- **Frontend Image** — Builds `crypto-frontend` Docker image (multi-stage: Node build → nginx)
- Skipped automatically if Docker is not available on the agent

## What the Pipeline Does NOT Do

- ❌ Start docker-compose services (Postgres, Redis, Kafka)
- ❌ Run integration tests requiring external services
- ❌ Deploy to any environment
- ❌ Run end-to-end tests

## Requirements

The Jenkins agent needs:
- **JDK 21+** (Maven wrapper downloads Maven automatically)
- **Node.js 20+** and **npm** (for frontend stages)
- **Docker** (optional, for image builds)

The custom Jenkins image in `devops/jenkins/` bundles all three.

## Running Jenkins Locally

See `devops/jenkins/README.md` for instructions on running Jenkins locally using Docker Compose.

## Local Development

The CI pipeline does not interfere with local development:
- Local dev uses `docker-compose.yml` at repo root (Postgres, Redis, Kafka)
- CI runs only unit tests that don't require external services
- Backend runs directly in IntelliJ, frontend via `npm run dev`
