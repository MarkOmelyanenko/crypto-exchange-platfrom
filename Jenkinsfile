pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Backend: Test') {
            steps {
                dir('backend') {
                    sh './mvnw -B clean test || mvn -B clean test'
                }
            }
            post {
                always {
                    dir('backend') {
                        junit 'target/surefire-reports/*.xml'
                    }
                }
            }
        }

        stage('Backend: Package') {
            steps {
                dir('backend') {
                    sh './mvnw -B clean package || mvn -B clean package'
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                sh 'docker build -t crypto-exchange-backend:latest ./backend'
                sh 'docker build -t crypto-exchange-frontend:latest ./frontend'
            }
        }

        stage('Smoke Test') {
            steps {
                sh '''
                    docker compose -f devops/docker-compose.ci.yml up -d || \
                    docker-compose -f devops/docker-compose.ci.yml up -d
                '''
                sh '''
                    timeout 120 bash -c 'until docker exec ci-backend curl -sf http://localhost:8080/actuator/health; do sleep 2; done'
                '''
            }
            post {
                always {
                    sh '''
                        docker compose -f devops/docker-compose.ci.yml down -v || \
                        docker-compose -f devops/docker-compose.ci.yml down -v
                    '''
                }
            }
        }
    }

    post {
        always {
            sh 'docker image prune -f || true'
        }
    }
}
