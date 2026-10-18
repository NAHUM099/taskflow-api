
pipeline {
    agent any

    environment {
        SPRING_DATASOURCE_URL = 'jdbc:postgresql://taskflow-postgres-ci:5432/taskflow'
    }

    stages {
        stage('Build and Test') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'taskflow-ci-db',
                        usernameVariable: 'DB_USERNAME',
                        passwordVariable: 'DB_PASSWORD'
                    ),
                    string(
                        credentialsId: 'taskflow-ci-jwt',
                        variable: 'JWT_SECRET'
                    )
                ]) {
                    sh 'chmod +x gradlew'
                    sh './gradlew clean test'
                }
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true,
                  testResults: '**/build/test-results/test/*.xml'
        }
    }
}
