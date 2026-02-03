# Jenkins CI Setup

This directory contains the Jenkins infrastructure for local and remote CI usage.

## Quick Start

### Prerequisites
- Docker and Docker Compose installed
- Docker daemon running

### Starting Jenkins

1. Navigate to this directory:
   ```bash
   cd devops/jenkins
   ```

2. Start Jenkins:
   ```bash
   docker-compose -f docker-compose.jenkins.yml up -d
   ```

3. Get the initial admin password:
   ```bash
   docker exec crypto-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
   ```

4. Open Jenkins in your browser:
   ```
   http://localhost:8081
   ```

5. Follow the setup wizard:
   - Paste the initial admin password
   - Install suggested plugins (or select "Install suggested plugins")
   - Create an admin user or continue with the default admin account

### Recommended Plugins

After initial setup, install these plugins if not already installed:
- **Pipeline** (usually included)
- **Git** (usually included)
- **Docker Pipeline** (for building Docker images)
- **Blue Ocean** (optional, for a modern UI)

To install plugins:
1. Go to **Manage Jenkins** → **Plugins**
2. Search for the plugin name
3. Check the box and click **Install without restart**

### Configuring Build Tools

The pipeline requires Maven and JDK to be configured in Jenkins:

1. Go to **Manage Jenkins** → **Tools**
2. **Maven installations**:
   - Click **Add Maven**
   - Name: `Maven` (must match Jenkinsfile)
   - Version: Select `3.9.6` or latest 3.9.x
   - Click **Save**
3. **JDK installations**:
   - Click **Add JDK**
   - Name: `JDK21` (must match Jenkinsfile)
   - JAVA_HOME: `/usr/lib/jvm/java-21-openjdk-amd64` (or use automatic installer)
   - Or use automatic installer: Select `jdk-21` from the list
   - Click **Save**

**Alternative**: If you prefer not to configure tools, you can modify the Jenkinsfile to use Docker agents or install tools in the pipeline steps.

### Creating a Pipeline Job

1. In Jenkins, click **New Item**
2. Enter a name (e.g., "crypto-exchange-ci")
3. Select **Pipeline** and click **OK**
4. In the pipeline configuration:
   - **Definition**: Pipeline script from SCM
   - **SCM**: Git
   - **Repository URL**: Your repository URL (or use local path `/workspace` if testing locally)
   - **Branch**: `*/main` or `*/master` (adjust as needed)
   - **Script Path**: `Jenkinsfile`
5. Click **Save**

### Running the Pipeline

1. Click on your pipeline job
2. Click **Build Now**
3. Monitor the build progress in the console output

## Docker Socket Mounting

**Security Note**: This setup mounts the Docker socket (`/var/run/docker.sock`) into the Jenkins container. This gives Jenkins full access to the Docker daemon on the host machine, which means:
- Jenkins can build and run Docker images
- Jenkins has root-level access to the host Docker daemon
- This is convenient for local development but should be secured in production

For production deployments:
- Consider using Docker-in-Docker (DinD) instead
- Use Jenkins agents with restricted Docker access
- Implement proper network isolation
- Review Jenkins security best practices

## Stopping Jenkins

```bash
docker-compose -f docker-compose.jenkins.yml down
```

To remove all data (including jobs and configuration):
```bash
docker-compose -f docker-compose.jenkins.yml down -v
```

## Troubleshooting

### Jenkins won't start
- Check if port 8081 is already in use: `lsof -i :8081`
- Check Docker logs: `docker logs crypto-jenkins`

### Cannot build Docker images
- Ensure Docker socket is accessible: `ls -la /var/run/docker.sock`
- Check Jenkins container has Docker CLI: `docker exec crypto-jenkins docker --version`
- You may need to install Docker CLI in the Jenkins container or use a custom image

### Pipeline fails to access workspace
- Ensure the workspace volume mount is correct
- Check file permissions on the mounted directory
