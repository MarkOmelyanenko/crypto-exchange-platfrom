pipeline {
    agent any

    stages {
        stage('Build & Test') {
            steps {
                dir('backend') {
                    // Skip tests requiring external services (DB, Redis, Kafka)
                    // Exclude: Integration tests and SpringBootTest context loading tests
                    sh '''
                        ./mvnw -B -ntp clean test -Dtest='!*IntegrationTest,!BackendApplicationTests' || \
                        mvn -B -ntp clean test -Dtest='!*IntegrationTest,!BackendApplicationTests'
                    '''
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

        stage('Package') {
            steps {
                dir('backend') {
                    sh './mvnw -B -ntp -DskipTests package || mvn -B -ntp -DskipTests package'
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                }
            }
        }

        stage('Docker Build') {
            steps {
                script {
                    if (fileExists('backend/Dockerfile')) {
                        try {
                            sh 'docker --version'
                            dir('backend') {
                                sh 'docker build -t crypto-backend:ci .'
                            }
                        } catch (Exception e) {
                            echo "Docker not available, skipping image build: ${e.message}"
                        }
                    } else {
                        echo "Dockerfile not found, skipping Docker build"
                    }
                }
            }
        }
    }
}
