pipeline {
    agent {
        label 'ec_node_29'
    }

    triggers {
        cron('H 2 * * 7')
    }

    parameters {
        string(name: 'NetworkToScan', defaultValue: '192.168.203.0/24', description: 'What subnet or IP should be scanned')
        string(name: 'GitRepo', defaultValue: 'git@github.com:senkodima/DevOps_course.git', description: 'My repository (Clone with SSH)')
        credentials(name: 'GitCredsToUse', defaultValue: 'jenkins', description: 'Credentials to use for github repo', credentialType: "SSH username with private key", required: true )
    }

    stages {
        stage('Pull repository') {
            steps {
                git credentialsId: "${params.GitCredsToUse}", url: "${params.GitRepo}"
            }
        }

        stage('Install nmap and speedtest') {
            steps {
                step([$class: 'AnsibleAdHocCommandBuilder',
                    hostPattern: '127.0.0.1',
                    module: 'yum',
                    command: 'name=nmap,python,python-setuptools,python2-speedtest-cli state=latest',
                    additionalParameters: '--become'
                ])
            }
        }
        stage('parallel check nmap & speedtest') {
            parallel {
                stage('Count all online hosts inside EC') {
                    steps {
                        sh """
                            nmap -sP ${params.NetworkToScan} > nmap.log
                        """
                    }
                }

                stage('Measure Internet access speed in EC') {
                    steps {
                        sh """
                            speedtest > speedtest.log
                        """
                    }
                }
            }
        }
        stage ('Collect and push logs to repository') {
            steps {
                sh """
                    mkdir -p logs/${env.BUILD_ID}
                    mv {nmap,speedtest}.log logs/${env.BUILD_ID}
                """

                sh """
                    git config user.email "jenkins@jenkins.local"
                    git config user.name "Jenkins"
                    git config push.default matching
                    git add .
                    git commit -m "logs from Jenkins build ${env.BUILD_ID}"
                """

                sshagent (credentials: ["${params.GitCredsToUse}"]) {
                    sh('git push')
                }
            }
        }
    }
}