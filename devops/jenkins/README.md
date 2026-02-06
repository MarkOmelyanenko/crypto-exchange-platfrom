# Local Jenkins Setup

Custom Jenkins image with JDK 21, Node.js 20, and Docker CLI for running the full CI pipeline.

## Quick Start

1. Build and start Jenkins:
   ```bash
   cd devops/jenkins
   docker compose up -d --build
   ```

2. Get initial admin password:
   ```bash
   docker exec crypto-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
   ```

3. Open Jenkins:
   ```
   http://localhost:8081
   ```

4. Install suggested plugins when prompted.

5. Create Pipeline job:
   - Click **New Item**
   - Name: `crypto-exchange-ci`
   - Select **Pipeline** → **OK**
   - **Definition**: Pipeline script from SCM
   - **SCM**: Git
   - **Repository URL**: Your repo URL (or local path)
   - **Script Path**: `Jenkinsfile`
   - Click **Save**

6. Run the pipeline by clicking **Build Now**

## What's Included

The custom Jenkins image (`Dockerfile`) bundles:
- **Jenkins LTS** — CI server
- **JDK 21** — for backend Maven builds (via Jenkins base image)
- **Node.js 20** — for frontend lint and build
- **Docker CLI** — for building Docker images (uses host Docker via socket mount)

## Pipeline Stages

| Stage | Tool | What it does |
|---|---|---|
| Backend: Test | `./mvnw test` | Runs unit tests |
| Backend: Package | `./mvnw package` | Creates JAR artifact |
| Frontend: Install | `npm ci` | Installs dependencies |
| Frontend: Lint | `npm run lint` | ESLint checks |
| Frontend: Build | `npm run build` | Production bundle |
| Docker Build | `docker build` | Backend + Frontend images (parallel) |

## Stopping Jenkins

```bash
docker compose down
```

To remove all data:
```bash
docker compose down -v
```

## Rebuilding the Image

After changing the `Dockerfile`:
```bash
docker compose up -d --build
```
