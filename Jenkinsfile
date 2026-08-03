pipeline {
    agent { label 'linux-node' }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    parameters {
        booleanParam(
            name: 'CREATE_RELEASE_TAG',
            defaultValue: false,
            description: 'Create and push an Axion release tag (master or release/* only).'
        )
    }

    tools {
        jdk 'OpenJDK21'
    }

    environment {
        // Docker registry configuration
        HOST_NAME = 'registry.example.com'
        REGISTRY = "https://${HOST_NAME}"

        // Image configuration
        NAMESPACE = 'portfolio'
        GROUP_ID  = 'com.mgmtp.gives'
        IMAGE_NAME = 'mgm-gives-be'

        IMAGE_REPOSITORY = "${HOST_NAME}/${NAMESPACE}/${GROUP_ID}/${IMAGE_NAME}"

        // Credentials
        DOCKER_CREDENTIALS_ID = 'docker-registry-credentials'
        RELEASE_GIT_CREDENTIALS_ID = 'github-release-token'
        SSH_CREDENTIALS_ID = 'deploy-ssh-key'

        // SSH Configuration
        SSH_USER = 'deploy'
        SSH_HOST = "${SSH_USER}@deploy.example.com"
        SSH_OPTIONS = '-o StrictHostKeyChecking=accept-new'

        REMOTE_DIR = '/opt/mgm-gives'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'chmod +x gradlew'
            }
        }

        stage('Integration Test') {
            steps {
                sh './gradlew clean check --no-daemon'
            }
        }

        stage('Create Release Tag') {
            when {
                expression { return params.CREATE_RELEASE_TAG && isReleaseBranch() }
            }

            steps {
                input message: 'Create and push the next Axion release tag?', ok: 'Release'
                withCredentials([usernamePassword(
                    credentialsId: env.RELEASE_GIT_CREDENTIALS_ID,
                    usernameVariable: 'RELEASE_GIT_USER',
                    passwordVariable: 'RELEASE_GIT_TOKEN'
                )]) {
                    sh './gradlew release --no-daemon'
                }
            }
        }

        stage('Resolve Version') {
            steps {
                script {
                    env.APP_VERSION = sh(
                        script: './gradlew printVersion --quiet --no-daemon',
                        returnStdout: true
                    ).trim()
                    echo "Axion project version: ${env.APP_VERSION}"
                }
            }
        }

        stage('Build') {
            when {
                expression { return isDeployableBranch() }
            }

            steps {
                script {
                    def latestTagPrefix = getLatestTagPrefix()

                    env.IMAGE_TAG = "${env.APP_VERSION}-${env.BUILD_NUMBER}"
                    env.DOCKER_IMAGE = "${env.IMAGE_REPOSITORY}:${env.IMAGE_TAG}"
                    env.LATEST_TAG = "${env.IMAGE_REPOSITORY}:${latestTagPrefix}-latest"

                    sh "./gradlew bootBuildImage --imageName=${env.DOCKER_IMAGE} --no-daemon"
                    sh "docker tag ${env.DOCKER_IMAGE} ${env.LATEST_TAG}"
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
                            sh '''
                                printf '%s' "$DOCKER_PASSWORD" | ssh ${SSH_OPTIONS} ${SSH_HOST} \
                                    "docker login -u '$DOCKER_USERNAME' --password-stdin '$HOST_NAME'"
                            '''

                            // Ensure media storage directories exist on the VM
                            sh "ssh ${SSH_OPTIONS} ${SSH_HOST} 'mkdir -p /opt/mgm-gives/media/development /opt/mgm-gives/media/staging /opt/mgm-gives/media/production'"
                            sh "ssh ${SSH_OPTIONS} ${SSH_HOST} 'chmod 755 /opt/mgm-gives/media/development /opt/mgm-gives/media/staging /opt/mgm-gives/media/production'"

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

def isReleaseBranch() {
    return env.BRANCH_NAME == 'master' || env.BRANCH_NAME.startsWith('release/')
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
