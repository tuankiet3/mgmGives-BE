pipeline {
    agent { label 'linux-node' }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    tools {
        maven 'Maven3'
        jdk 'OpenJDK21'
    }

    environment {
        HOST_NAME = 'da-nang-internship-docker-local.dockerregistry.mgm-tp.com'

        NAMESPACE = 'com.mgmtp.da-nang-internship'
        GROUP_ID  = 'com.mgmtp.gives'
        IMAGE_NAME = 'mgm-gives-be'

        IMAGE_REPOSITORY = "${HOST_NAME}/${NAMESPACE}/${GROUP_ID}/${IMAGE_NAME}"
        IMAGE_TAG_PREFIX = "${(env.CHANGE_BRANCH ?: env.BRANCH_NAME).replace('/', '-')}"

        DOCKER_IMAGE = "${IMAGE_REPOSITORY}:${IMAGE_TAG_PREFIX}-${env.BUILD_NUMBER}"
        LATEST_TAG = "${IMAGE_REPOSITORY}:${IMAGE_TAG_PREFIX}-latest"

        REGISTRY = "https://${HOST_NAME}"
        DOCKER_CREDENTIALS_ID = 'ci-user-artifactory-token'
    }

    stages {
        stage('Checkout') {
             steps {
                  checkout scm
             }
        }

        stage('Verify & Test') {
            steps {
                sh 'mvn clean verify -Dspring.profiles.active=test'
            }
        }

        stage('Build & Push Docker Image') {
            when {
                expression { return isDeployableBranch() }
            }

            steps {
                withDockerRegistry(credentialsId: env.DOCKER_CREDENTIALS_ID, url: env.REGISTRY) {
                    sh '''
                        docker build -f Dockerfile . -t "$DOCKER_IMAGE" -t "$LATEST_TAG"
                        docker push "$DOCKER_IMAGE"
                        docker push "$LATEST_TAG"
                    '''
                }
            }
        }

        stage('Deploy') {
             when {
                 expression { return isDeployableBranch() }
             }

             steps {
                 echo 'Deploy stage is skipped because deployment server is not configured yet.'
             }
        }
    }

    post {
        success {
            echo 'Backend CI/CD pipeline completed successfully.'
        }
        failure {
            echo 'Backend CI/CD pipeline failed'
        }
    }
}

def getCurrentBranch() {
    return env.CHANGE_BRANCH ?: env.BRANCH_NAME
}

def isDeployableBranch() {
    def branch = getCurrentBranch()
    def exactBranches = ['develop', 'master']

    return exactBranches.contains(branch) || branch.startsWith('release/')
}