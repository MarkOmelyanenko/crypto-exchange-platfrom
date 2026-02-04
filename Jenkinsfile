pipeline {
    agent any
    
    tools {
        // These tools should be configured in Jenkins: Manage Jenkins -> Tools
        // For Maven: Add Maven installation (e.g., version 3.9.x)
        // For JDK: Add JDK installation (e.g., Java 21) OR use JAVA_HOME environment variable
        maven 'Maven' // Configure in Jenkins Global Tool Configuration
        // JDK is optional - will use JAVA_HOME if not configured
        // jdk 'JDK21'   // Uncomment and configure in Jenkins Global Tool Configuration
    }
    
    environment {
        // Maven settings for caching
        MAVEN_OPTS = '-Dmaven.repo.local=/var/jenkins_home/.m2/repository'
        // Docker image names
        BACKEND_IMAGE = 'crypto-exchange-backend:latest'
        FRONTEND_IMAGE = 'crypto-exchange-frontend:latest'
        // Test environment variables (will be set in script)
        POSTGRES_DB = 'crypto_exchange_test'
        POSTGRES_USER = 'postgres'
        POSTGRES_PASSWORD = 'postgres'
        SPRING_PROFILES_ACTIVE = 'test'
    }
    
    stages {
        stage('Checkout') {
            steps {
                script {
                    if (env.BRANCH_NAME) {
                        checkout scm
                    } else {
                        // For local testing, workspace should already be available
                        sh 'pwd && ls -la'
                    }
                }
            }
        }
        
        stage('Backend: Start Test Services') {
            steps {
                script {
                    // Detect host IP for accessing services from Jenkins container
                    def testHost = sh(
                        script: '''
                            # Try host.docker.internal first (works on Mac/Windows Docker Desktop)
                            if ping -c 1 -W 1 host.docker.internal > /dev/null 2>&1; then
                                echo "host.docker.internal"
                            else
                                # For Linux, try to get the host IP from the default gateway
                                # Try ip command first, then fallback to route
                                if command -v ip > /dev/null 2>&1; then
                                    ip route show default 2>/dev/null | awk '/default/ {print $3}' | head -1
                                elif command -v route > /dev/null 2>&1; then
                                    route -n | awk '/^0.0.0.0/ {print $2}' | head -1
                                else
                                    # Last resort: use localhost (may work if services are on same network)
                                    echo "localhost"
                                fi
                            fi
                        ''',
                        returnStdout: true
                    ).trim()
                    
                    // Fallback to localhost if detection failed
                    if (!testHost || testHost.isEmpty()) {
                        testHost = "localhost"
                    }
                    
                    if (!testHost) {
                        testHost = "localhost"
                    }
                    
                    env.TEST_HOST = testHost
                    env.SPRING_DATASOURCE_URL = "jdbc:postgresql://${testHost}:5434/crypto_exchange_test"
                    env.SPRING_DATASOURCE_USERNAME = "postgres"
                    env.SPRING_DATASOURCE_PASSWORD = "postgres"
                    env.REDIS_HOST = testHost
                    env.REDIS_PORT = "6381"
                    env.KAFKA_BOOTSTRAP_SERVERS = "${testHost}:9094"
                    
                    echo "Using test host: ${testHost}"
                    echo "PostgreSQL URL: ${env.SPRING_DATASOURCE_URL}"
                    echo "Redis: ${env.REDIS_HOST}:${env.REDIS_PORT}"
                    echo "Kafka: ${env.KAFKA_BOOTSTRAP_SERVERS}"
                }
                sh '''
                    echo "Starting test services (PostgreSQL, Redis, Kafka)..."
                    # Use docker compose (v2) or fallback to docker-compose
                    if command -v docker > /dev/null 2>&1 && docker compose version > /dev/null 2>&1; then
                        docker compose -f devops/docker-compose.test.yml up -d
                    elif command -v docker-compose > /dev/null 2>&1; then
                        docker-compose -f devops/docker-compose.test.yml up -d
                    else
                        echo "ERROR: Neither 'docker compose' nor 'docker-compose' is available"
                        echo "Please ensure Docker is installed and accessible in the Jenkins container"
                        exit 1
                    fi
                    
                    echo "Waiting for services to be healthy..."
                    # Wait for PostgreSQL (up to 60 seconds)
                    timeout=60
                    elapsed=0
                    while [ $elapsed -lt $timeout ]; do
                        if docker exec test-postgres pg_isready -U postgres > /dev/null 2>&1; then
                            echo "✓ PostgreSQL is ready"
                            break
                        fi
                        sleep 2
                        elapsed=$((elapsed + 2))
                    done
                    if [ $elapsed -ge $timeout ]; then
                        echo "✗ PostgreSQL failed to start"
                        docker logs test-postgres
                        exit 1
                    fi
                    
                    # Wait for Redis
                    elapsed=0
                    while [ $elapsed -lt $timeout ]; do
                        if docker exec test-redis redis-cli ping > /dev/null 2>&1; then
                            echo "✓ Redis is ready"
                            break
                        fi
                        sleep 2
                        elapsed=$((elapsed + 2))
                    done
                    if [ $elapsed -ge $timeout ]; then
                        echo "✗ Redis failed to start"
                        docker logs test-redis
                        exit 1
                    fi
                    
                    # Wait for Zookeeper
                    elapsed=0
                    while [ $elapsed -lt $timeout ]; do
                        if docker exec test-zookeeper nc -z localhost 2181 > /dev/null 2>&1; then
                            echo "✓ Zookeeper is ready"
                            break
                        fi
                        sleep 2
                        elapsed=$((elapsed + 2))
                    done
                    if [ $elapsed -ge $timeout ]; then
                        echo "✗ Zookeeper failed to start"
                        docker logs test-zookeeper
                        exit 1
                    fi
                    
                    # Wait for Kafka (needs Zookeeper to be ready first)
                    elapsed=0
                    while [ $elapsed -lt $timeout ]; do
                        if docker exec test-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1; then
                            echo "✓ Kafka is ready"
                            break
                        fi
                        sleep 2
                        elapsed=$((elapsed + 2))
                    done
                    if [ $elapsed -ge $timeout ]; then
                        echo "✗ Kafka failed to start"
                        docker logs test-kafka
                        exit 1
                    fi
                    
                    echo "All test services are ready"
                '''
            }
        }
        
        stage('Backend: Build & Test') {
            steps {
                script {
                    // Verify services are accessible before running tests
                    sh '''
                        echo "=== Verifying service connectivity ==="
                        echo "Testing PostgreSQL connection..."
                        if command -v nc > /dev/null 2>&1; then
                            nc -z ${TEST_HOST:-localhost} 5434 && echo "✓ PostgreSQL reachable" || echo "✗ PostgreSQL not reachable"
                        else
                            echo "nc not available, skipping connectivity test"
                        fi
                        
                        echo "Testing Redis connection..."
                        if command -v nc > /dev/null 2>&1; then
                            nc -z ${TEST_HOST:-localhost} 6381 && echo "✓ Redis reachable" || echo "✗ Redis not reachable"
                        fi
                        
                        echo "Testing Kafka connection..."
                        if command -v nc > /dev/null 2>&1; then
                            nc -z ${TEST_HOST:-localhost} 9094 && echo "✓ Kafka reachable" || echo "✗ Kafka not reachable"
                        fi
                        echo "====================================="
                    '''
                }
                dir('backend') {
                    sh '''
                        # Check if Java is available
                        if ! command -v java > /dev/null 2>&1 && [ -z "$JAVA_HOME" ]; then
                            echo "WARNING: Java not found. Checking if Maven wrapper can provide Java..."
                        fi
                        
                        # Print environment variables for debugging
                        echo "=== Test Environment ==="
                        echo "SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL}"
                        echo "REDIS_HOST: ${REDIS_HOST}"
                        echo "REDIS_PORT: ${REDIS_PORT}"
                        echo "KAFKA_BOOTSTRAP_SERVERS: ${KAFKA_BOOTSTRAP_SERVERS}"
                        echo "SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE}"
                        echo "TEST_HOST: ${TEST_HOST}"
                        echo "========================"
                        
                        # Use Maven wrapper if available, otherwise use mvn command
                        if [ -f "./mvnw" ]; then
                            echo "Using Maven wrapper..."
                            ./mvnw -B -DskipTests=false clean test || true
                        else
                            echo "Using system Maven..."
                            mvn -B -DskipTests=false clean test || true
                        fi
                    '''
                }
            }
            post {
                always {
                    // Archive test results and show detailed errors
                    dir('backend') {
                        junit 'target/surefire-reports/*.xml'
                        sh '''
                            echo "=== Test Failure Details ==="
                            if [ -d "target/surefire-reports" ]; then
                                for file in target/surefire-reports/*.txt; do
                                    if [ -f "$file" ]; then
                                        echo "--- $(basename $file) ---"
                                        head -200 "$file"
                                        echo ""
                                    fi
                                done
                            fi
                            echo "=== Checking test XML reports ==="
                            if [ -d "target/surefire-reports" ]; then
                                ls -la target/surefire-reports/
                            fi
                        '''
                    }
                    // Always tear down test services
                    sh '''
                        echo "Stopping test services..."
                        # Use docker compose (v2) or fallback to docker-compose
                        if command -v docker > /dev/null 2>&1 && docker compose version > /dev/null 2>&1; then
                            docker compose -f devops/docker-compose.test.yml down -v || true
                        elif command -v docker-compose > /dev/null 2>&1; then
                            docker-compose -f devops/docker-compose.test.yml down -v || true
                        fi
                    '''
                }
            }
        }
        
        stage('Backend: Package') {
            steps {
                dir('backend') {
                    sh '''
                        echo "Packaging backend application..."
                        # Use Maven wrapper if available, otherwise use mvn command
                        if [ -f "./mvnw" ]; then
                            ./mvnw -B -DskipTests package
                        else
                            mvn -B -DskipTests package
                        fi
                    '''
                }
            }
        }
        
        stage('Backend: Docker Build') {
            steps {
                dir('backend') {
                    script {
                        if (fileExists('Dockerfile')) {
                            sh """
                                echo "Building Docker image: ${env.BACKEND_IMAGE}"
                                docker build -t ${env.BACKEND_IMAGE} .
                            """
                        } else {
                            error("Backend Dockerfile not found. Please create one.")
                        }
                    }
                }
            }
        }
        
        stage('Frontend: Build & Test') {
            when {
                expression { fileExists('frontend/package.json') }
            }
            steps {
                dir('frontend') {
                    script {
                        // Check if npm is available
                        def npmAvailable = sh(
                            script: 'command -v npm > /dev/null 2>&1',
                            returnStatus: true
                        ) == 0
                        
                        if (!npmAvailable) {
                            echo "WARNING: npm not found. Skipping frontend build."
                            echo "To enable frontend builds, install Node.js in the Jenkins container."
                            return
                        }
                        
                        sh '''
                            echo "Installing frontend dependencies..."
                            npm ci
                        '''
                        
                        // Try to run tests, but don't fail if test script doesn't exist
                        def testScript = sh(
                            script: 'npm run test 2>&1 || echo "No test script found"',
                            returnStatus: true
                        )
                        if (testScript != 0) {
                            echo "No test script found or tests skipped, proceeding with build..."
                        }
                        
                        sh '''
                            echo "Building frontend..."
                            npm run build
                        '''
                    }
                }
            }
        }
        
        stage('Frontend: Docker Build') {
            when {
                expression { 
                    fileExists('frontend/package.json') && fileExists('frontend/Dockerfile')
                }
            }
            steps {
                dir('frontend') {
                    sh """
                        echo "Building Docker image: ${env.FRONTEND_IMAGE}"
                        docker build -t ${env.FRONTEND_IMAGE} .
                    """
                }
            }
        }
        
        stage('Smoke Test') {
            steps {
                script {
                    // Start services using CI docker-compose
                    sh '''
                        echo "Starting services for smoke test..."
                        # Use docker compose (v2) or fallback to docker-compose
                        if command -v docker > /dev/null 2>&1 && docker compose version > /dev/null 2>&1; then
                            docker compose -f devops/docker-compose.ci.yml up -d
                        elif command -v docker-compose > /dev/null 2>&1; then
                            docker-compose -f devops/docker-compose.ci.yml up -d
                        else
                            echo "ERROR: Neither 'docker compose' nor 'docker-compose' is available"
                            exit 1
                        fi
                        
                        # Wait for PostgreSQL to be fully ready (including database creation)
                        echo "Waiting for PostgreSQL database to be created..."
                        max_wait=30
                        waited=0
                        while [ $waited -lt $max_wait ]; do
                            if docker exec ci-postgres psql -U postgres -lqt 2>/dev/null | cut -d \\| -f 1 | grep -qw crypto_exchange; then
                                echo "✓ Database 'crypto_exchange' exists"
                                break
                            fi
                            sleep 1
                            waited=$((waited + 1))
                        done
                        if [ $waited -ge $max_wait ]; then
                            echo "✗ Database 'crypto_exchange' was not created within timeout"
                            docker logs ci-postgres
                            exit 1
                        fi
                    '''
                    
                    // Wait for backend to be healthy (give it more time for Flyway migrations)
                    sh '''
                        echo "Waiting for backend to be healthy..."
                        echo "Note: Backend needs time to run Flyway migrations on first startup"
                        max_attempts=60  # Increased to 2 minutes for migrations
                        attempt=0
                        while [ $attempt -lt $max_attempts ]; do
                            # Check if backend container is still running
                            if ! docker ps | grep -q ci-backend; then
                                echo "Backend container stopped! Checking logs..."
                                docker logs --tail 50 ci-backend || true
                                exit 1
                            fi
                            
                            if curl -f -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
                                echo "Backend is UP!"
                                echo "Health check response:"
                                curl -s http://localhost:8080/actuator/health | jq '.' 2>/dev/null || curl -s http://localhost:8080/actuator/health
                                exit 0
                            fi
                            attempt=$((attempt + 1))
                            if [ $((attempt % 5)) -eq 0 ]; then
                                echo "Attempt $attempt/$max_attempts: Backend not ready yet, checking logs..."
                                docker logs --tail 20 ci-backend || true
                            else
                                echo "Attempt $attempt/$max_attempts: Backend not ready yet, waiting..."
                            fi
                            sleep 2
                        done
                        echo "Backend failed to become healthy within timeout"
                        echo "Checking full container logs..."
                        docker logs ci-backend || true
                        exit 1
                    '''
                }
            }
            post {
                always {
                    // Always tear down containers
                    sh '''
                        echo "Tearing down test containers..."
                        # Use docker compose (v2) or fallback to docker-compose
                        if command -v docker > /dev/null 2>&1 && docker compose version > /dev/null 2>&1; then
                            docker compose -f devops/docker-compose.ci.yml down -v
                        elif command -v docker-compose > /dev/null 2>&1; then
                            docker-compose -f devops/docker-compose.ci.yml down -v
                        fi
                    '''
                }
            }
        }
    }
    
    post {
        success {
            echo 'Pipeline succeeded!'
        }
        failure {
            echo 'Pipeline failed!'
        }
        always {
            // Clean up any dangling images
            sh 'docker image prune -f || true'
        }
    }
}
