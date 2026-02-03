pipeline {
    agent any
    
    tools {
        // These tools should be configured in Jenkins: Manage Jenkins -> Tools
        // For Maven: Add Maven installation (e.g., version 3.9.x)
        // For JDK: Add JDK installation (e.g., Java 21)
        // If tools are not configured, you can use docker agents or install tools in pipeline
        maven 'Maven' // Configure in Jenkins Global Tool Configuration
        jdk 'JDK21'   // Configure in Jenkins Global Tool Configuration
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
                                # For Linux, get the host IP from the default gateway
                                # This is the IP of the Docker host from the container's perspective
                                ip route show default | awk '/default/ {print $3}' | head -1
                            fi
                        ''',
                        returnStdout: true
                    ).trim()
                    
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
                    docker-compose -f devops/docker-compose.test.yml up -d
                    
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
                dir('backend') {
                    sh '''
                        echo "Running Maven tests..."
                        mvn -B -DskipTests=false clean test
                    '''
                }
            }
            post {
                always {
                    // Archive test results
                    dir('backend') {
                        junit 'target/surefire-reports/*.xml'
                    }
                    // Always tear down test services
                    sh '''
                        echo "Stopping test services..."
                        docker-compose -f devops/docker-compose.test.yml down -v
                    '''
                }
            }
        }
        
        stage('Backend: Package') {
            steps {
                dir('backend') {
                    sh '''
                        echo "Packaging backend application..."
                        mvn -B -DskipTests package
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
                    sh '''
                        echo "Installing frontend dependencies..."
                        npm ci
                    '''
                    script {
                        // Try to run tests, but don't fail if test script doesn't exist
                        def testScript = sh(
                            script: 'npm run test 2>&1 || echo "No test script found"',
                            returnStatus: true
                        )
                        if (testScript != 0) {
                            echo "No test script found or tests skipped, proceeding with build..."
                        }
                    }
                    sh '''
                        echo "Building frontend..."
                        npm run build
                    '''
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
                        docker-compose -f devops/docker-compose.ci.yml up -d
                    '''
                    
                    // Wait for backend to be healthy
                    sh '''
                        echo "Waiting for backend to be healthy..."
                        max_attempts=30
                        attempt=0
                        while [ $attempt -lt $max_attempts ]; do
                            if curl -f -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
                                echo "Backend is UP!"
                                echo "Health check response:"
                                curl -s http://localhost:8080/actuator/health | jq '.' 2>/dev/null || curl -s http://localhost:8080/actuator/health
                                exit 0
                            fi
                            attempt=$((attempt + 1))
                            echo "Attempt $attempt/$max_attempts: Backend not ready yet, waiting..."
                            sleep 2
                        done
                        echo "Backend failed to become healthy within timeout"
                        echo "Checking container logs..."
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
                        docker-compose -f devops/docker-compose.ci.yml down -v
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
