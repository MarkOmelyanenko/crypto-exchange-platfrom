pipeline {
    agent any

    environment {
        // Build settings
        MAVEN_OPTS = "-Dmaven.repo.local=.m2/repository"
        // Docker configuration
        DOCKER_BUILDKIT = "1"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'ls -la'
            }
        }

        stage('Backend: Build & Test') {
            steps {
                dir('backend') {
                    script {
                        // Run backend tests using Maven
                        // Testcontainers will handle database/redis/kafka automatically
                        if (fileExists('./mvnw')) {
                            sh 'chmod +x ./mvnw'
                            sh './mvnw -B -DskipTests=false clean test'
                        } else {
                            sh 'mvn -B -DskipTests=false clean test'
                        }
                    }
                }
            }
        }

        stage('Backend: Package') {
            steps {
                dir('backend') {
                    script {
                        if (fileExists('./mvnw')) {
                            sh './mvnw -B -DskipTests=true clean package'
                        } else {
                            sh 'mvn -B -DskipTests=true clean package'
                        }
                    }
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                script {
                    echo "Building Backend Docker Image..."
                    sh 'docker build -t crypto-exchange-backend:latest ./backend'
                    
                    echo "Building Frontend Docker Image..."
                    sh 'docker build -t crypto-exchange-frontend:latest ./frontend'
                }
            }
        }

        stage('Smoke Test') {
            steps {
                script {
                    echo "Starting services for smoke test..."
                    // Use docker compose (v2) or fallback to docker-compose
                    if (sh(script: 'command -v docker && docker compose version', returnStatus: true) == 0) {
                        sh 'docker compose -f devops/docker-compose.ci.yml up -d'
                    } else if (sh(script: 'command -v docker-compose', returnStatus: true) == 0) {
                        sh 'docker-compose -f devops/docker-compose.ci.yml up -d'
                    } else {
                        error "Neither 'docker compose' nor 'docker-compose' is available"
                    }

                    // Wait for PostgreSQL database creation using docker check
                    echo "Waiting for PostgreSQL database to be created..."
                    timeout(time: 2, unit: 'MINUTES') {
                        waitUntil {
                            def result = sh(
                                script: 'docker exec ci-postgres psql -U postgres -d crypto_exchange -c "SELECT 1" > /dev/null 2>&1',
                                returnStatus: true
                            )
                            return result == 0
                        }
                    }

                    // Wait for backend health
                    echo "Waiting for Backend to specific health endpoint..."
                    timeout(time: 2, unit: 'MINUTES') {
                        waitUntil {
                             // Basic check if port is open or endpoint responds 
                             // Since we might not have curl/wget inside agent sometimes, use python or docker exec curl
                             def result = sh(
                                 script: 'docker exec ci-backend curl -s -f http://localhost:8080/actuator/health > /dev/null 2>&1',
                                 returnStatus: true
                             )
                             return result == 0
                        }
                    }
                    echo "System is healthy!"
                }
            }
            post {
                always {
                    script {
                        echo "Stopping smoke test services..."
                        if (sh(script: 'command -v docker && docker compose version', returnStatus: true) == 0) {
                            sh 'docker compose -f devops/docker-compose.ci.yml down -v'
                        } else {
                            sh 'docker-compose -f devops/docker-compose.ci.yml down -v'
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
    }
}
