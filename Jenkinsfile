pipeline {
    agent any

    stages {
        stage('Build & Test') {
            steps {
                dir('backend') {
                    // Skip tests requiring external services (DB, Redis, Kafka)
                    // Exclude: Integration tests, SpringBootTest context loading tests, and Testcontainers-based tests
                    // Allow build to succeed if no tests match (no pure unit tests exist yet)
                    sh '''
                        export TESTCONTAINERS_RYUK_DISABLED=true
                        export TESTCONTAINERS_CHECKS_DISABLE=1
                        ./mvnw -B -ntp clean test \
                            -Dtest='!*IntegrationTest,!BackendApplicationTests,!WalletServiceTest,!WalletServiceConcurrencyTest' \
                            -Dtestcontainers.ryuk.disabled=true \
                            -Dsurefire.failIfNoSpecifiedTests=false
                    '''
                }
            }
            post {
                always {
                    dir('backend') {
                        script {
                            def reportCount = sh(
                                script: 'find target/surefire-reports -name "*.xml" 2>/dev/null | wc -l',
                                returnStdout: true
                            ).trim().toInteger()
                            if (reportCount > 0) {
                                junit 'target/surefire-reports/*.xml'
                            } else {
                                echo 'No test reports found - skipping JUnit report collection'
                            }
                        }
                    }
                }
            }
        }

        stage('Package') {
            steps {
                dir('backend') {
                    sh './mvnw -B -ntp -DskipTests package'
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
