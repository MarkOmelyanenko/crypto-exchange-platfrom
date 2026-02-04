# Local Jenkins Setup

Simple Jenkins setup for running CI pipelines locally.

## Quick Start

1. Start Jenkins:
   ```bash
   cd devops/jenkins
   docker compose up -d
   ```

2. Get initial admin password:
   ```bash
   docker exec crypto-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
   ```

3. Open Jenkins:
   ```
   http://localhost:8081
   ```

4. Create Pipeline job:
   - Click **New Item**
   - Name: `crypto-exchange-ci`
   - Select **Pipeline** → **OK**
   - **Definition**: Pipeline script from SCM
   - **SCM**: Git
   - **Repository URL**: Your repo URL (or local path)
   - **Script Path**: `Jenkinsfile`
   - Click **Save**

5. Run the pipeline by clicking **Build Now**

## Stopping Jenkins

```bash
docker compose down
```

To remove all data:
```bash
docker compose down -v
```
