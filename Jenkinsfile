pipeline {
    agent any

    stages {
        stage('Build & Test') {
            steps {
                dir('backend') {
                    // Skip integration tests (those requiring containers)
                    sh '''
                        ./mvnw -B -ntp clean test -Dtest='!*IntegrationTest' || \
                        mvn -B -ntp clean test -Dtest='!*IntegrationTest'
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
