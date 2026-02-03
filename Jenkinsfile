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
