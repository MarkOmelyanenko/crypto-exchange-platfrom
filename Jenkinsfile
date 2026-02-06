pipeline {
    agent any

    environment {
        DOCKER_AVAILABLE = sh(script: 'docker --version >/dev/null 2>&1 && echo true || echo false', returnStdout: true).trim()
    }

    stages {

        // ── Backend ────────────────────────────────────────────

        stage('Backend: Test') {
            steps {
                dir('backend') {
                    sh './mvnw -B -ntp clean test'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'backend/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Backend: Package') {
            steps {
                dir('backend') {
                    sh './mvnw -B -ntp -DskipTests package'
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                }
            }
        }

        // ── Frontend ───────────────────────────────────────────

        stage('Frontend: Install') {
            steps {
                dir('frontend') {
                    sh 'node --version && npm --version'
                    sh 'npm ci --no-audit --no-fund'
                }
            }
        }

        stage('Frontend: Lint') {
            steps {
                dir('frontend') {
                    sh 'npm run lint'
                }
            }
        }

        stage('Frontend: Build') {
            steps {
                dir('frontend') {
                    sh 'npm run build'
                    archiveArtifacts artifacts: 'dist/**', fingerprint: true
                }
            }
        }

        // ── Docker Images ──────────────────────────────────────

        stage('Docker Build') {
            when {
                expression { return env.DOCKER_AVAILABLE == 'true' }
            }
            parallel {
                stage('Backend Image') {
                    steps {
                        dir('backend') {
                            sh "docker build -t crypto-backend:${env.BUILD_NUMBER} -t crypto-backend:latest ."
                        }
                    }
                }
                stage('Frontend Image') {
                    steps {
                        dir('frontend') {
                            sh "docker build -t crypto-frontend:${env.BUILD_NUMBER} -t crypto-frontend:latest ."
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            echo 'CI pipeline completed successfully.'
        }
        failure {
            echo 'CI pipeline failed.'
        }
    }
}
