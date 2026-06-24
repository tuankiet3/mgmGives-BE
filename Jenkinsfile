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
        // Docker registry configuration
        HOST_NAME = 'da-nang-internship-docker-local.dockerregistry.mgm-tp.com'
        REGISTRY = "https://${HOST_NAME}"

        // Image configuration
        NAMESPACE = 'com.mgmtp.da-nang-internship'
        GROUP_ID  = 'com.mgmtp.gives'
        IMAGE_NAME = 'mgm-gives-be'

        IMAGE_REPOSITORY = "${HOST_NAME}/${NAMESPACE}/${GROUP_ID}/${IMAGE_NAME}"

        // Credentials
        DOCKER_CREDENTIALS_ID = 'ci-user-artifactory-token'
        SSH_CREDENTIALS_ID = 'ci-user-ssh'

        // SSH Configuration
        SSH_USER = 'mgmgives'
        SSH_HOST = "${SSH_USER}@mgm-gives.mgm-edv.de"
        SSH_OPTIONS = '-o StrictHostKeyChecking=no'

        REMOTE_DIR = "/home/${SSH_USER}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Integration Test') {
            steps {
                sh 'mvn clean verify -Dspring.profiles.active=test'
            }
        }

        stage('Build') {
            when {
                expression { return isDeployableBranch() }
            }

            steps {
                script {
                    def safeBranchName = env.BRANCH_NAME.replace('/', '-')
                    def latestTagPrefix = getLatestTagPrefix()

                    env.IMAGE_TAG = "${safeBranchName}-${env.BUILD_NUMBER}"
                    env.DOCKER_IMAGE = "${env.IMAGE_REPOSITORY}:${env.IMAGE_TAG}"
                    env.LATEST_TAG = "${env.IMAGE_REPOSITORY}:${latestTagPrefix}-latest"

                    sh "docker build -f Dockerfile . -t ${env.DOCKER_IMAGE} -t ${env.LATEST_TAG}"
                }
            }
        }

        stage('Push') {
            when {
                expression { return isDeployableBranch() }
            }

            steps {
                withDockerRegistry(credentialsId: env.DOCKER_CREDENTIALS_ID, url: env.REGISTRY) {
                    sh """
                        docker push "${DOCKER_IMAGE}"
                        docker push "${LATEST_TAG}"
                    """
                }
            }
        }

        stage('Approve Production Deploy') {
            when {
                expression { return isProductionBranch() }
            }

            steps {
                input message: "Deploy ${env.DOCKER_IMAGE} to production?", ok: 'Deploy'
            }
        }

        stage('Deploy') {
            when {
                expression { return isDeployableBranch() }
            }

            steps {
                script {
                    def deployEnv = getDeployEnvironment()
                    def profile = deployEnv

                    sshagent([env.SSH_CREDENTIALS_ID]) {
                        sh "scp ${SSH_OPTIONS} ./docker-compose.be.yml ${SSH_HOST}:${REMOTE_DIR}/docker-compose.be.yml"

                        withCredentials([usernamePassword(
                            credentialsId: env.DOCKER_CREDENTIALS_ID,
                            usernameVariable: 'DOCKER_USERNAME',
                            passwordVariable: 'DOCKER_PASSWORD'
                        )]) {
                            // Login to Docker registry
                            sh "ssh ${SSH_OPTIONS} ${SSH_HOST} 'docker login -u $DOCKER_USERNAME -p $DOCKER_PASSWORD ${HOST_NAME}'"

                            // Ensure media storage directories exist on the VM
                            sh "ssh ${SSH_OPTIONS} ${SSH_HOST} 'mkdir -p /home/mgmgives/mgm-gives-media/development /home/mgmgives/mgm-gives-media/staging /home/mgmgives/mgm-gives-media/production'"
                            sh "ssh ${SSH_OPTIONS} ${SSH_HOST} 'chmod 755 /home/mgmgives/mgm-gives-media/development /home/mgmgives/mgm-gives-media/staging /home/mgmgives/mgm-gives-media/production'"

                            // Pull and deploy
                            sh """
                                ssh ${SSH_OPTIONS} ${SSH_HOST} '
                                    docker compose -f ${REMOTE_DIR}/docker-compose.be.yml \\
                                        --project-name mgm-gives \\
                                        --profile ${profile} \\
                                        up -d --pull always --force-recreate
                                '
                            """

                            // Logout from Docker registry
                            sh "ssh ${SSH_OPTIONS} ${SSH_HOST} 'docker logout ${HOST_NAME}'"
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            echo 'Backend CI/CD pipeline completed successfully.'
        }
        failure {
            echo 'Backend CI/CD pipeline failed.'
        }
    }
}

def isDeployableBranch() {
    def branch = env.BRANCH_NAME
    return branch == 'develop' || branch == 'master' || branch.startsWith('release/')
}

def getDeployEnvironment() {
    switch(env.BRANCH_NAME) {
        case 'develop':
            return 'development'
        case 'master':
            return 'production'
        default:
            if (env.BRANCH_NAME.startsWith('release/')) {
                return 'staging'
            }
            error "Unsupported deploy branch: ${env.BRANCH_NAME}"
    }
}

def isProductionBranch() {
    return env.BRANCH_NAME == 'master'
}

def getLatestTagPrefix() {
    if (env.BRANCH_NAME == 'develop') {
        return 'develop'
    }

    if (env.BRANCH_NAME == 'master') {
        return 'master'
    }

    if (env.BRANCH_NAME.startsWith('release/')) {
        return 'staging'
    }

    return env.BRANCH_NAME.replace('/', '-')
}