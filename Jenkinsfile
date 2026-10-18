pipeline {
    agent any

    stages {
        stage('Build and Test') {
            steps {
                sh 'chmod +x gradlew'
                sh './gradlew clean test'
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