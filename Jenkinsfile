pipeline {
  environment {
    devRegistry = 'ghcr.io/datakaveri/aaa-dev'
    deplRegistry = 'ghcr.io/datakaveri/aaa-depl'
    testRegistry = 'ghcr.io/datakaveri/aaa-test:latest'
    registryUri = 'https://ghcr.io'
    registryCredential = 'datakaveri-ghcr'
    GIT_HASH = GIT_COMMIT.take(7)
  }
  agent { 
    node {
      label 'slave1' 
    }
  }
  stages {

    stage('Building images') {
      steps{
        script {
          echo 'Pulled - ' + env.GIT_BRANCH
          devImage = docker.build( devRegistry, "-f ./docker/dev.dockerfile .")
          deplImage = docker.build( deplRegistry, "-f ./docker/depl.dockerfile .")
          testImage = docker.build( testRegistry, "-f ./docker/test.dockerfile .")
        }
      }
    }

    stage('Unit Tests and CodeCoverage Test'){
      steps{
        script{
          sh 'docker compose -f docker-compose-test.yml up test'
        }
        xunit (
          thresholds: [ skipped(failureThreshold: '15'), failed(failureThreshold: '0') ],
          tools: [ JUnit(pattern: 'target/surefire-reports/*.xml') ]
        )
        jacoco classPattern: 'target/classes', execPattern: 'target/jacoco.exec', sourcePattern: 'src/main/java', exclusionPattern:'**/*VertxEBProxy.class,**/Constants.class,**/*VertxProxyHandler.class,**/*Verticle.class,iudx/aaa/server/deploy/*.class,iudx/aaa/server/registration/KcAdmin.class,iudx/aaa/server/apiserver/*,iudx/aaa/server/apiserver/util/*,iudx/aaa/server/admin/AdminService.class,iudx/aaa/server/apd/ApdService.class,iudx/aaa/server/auditing/AuditingService.class,iudx/aaa/server/auditing/AuditingService.class,iudx/aaa/server/registration/RegistrationService.class,iudx/aaa/server/token/TokenService.class,iudx/aaa/server/policy/PolicyService.class'
      }
      post{
        always {
          recordIssues enabledForFailure: true, tool: checkStyle(pattern: 'target/checkstyle-result.xml')
          recordIssues enabledForFailure: true, tool: spotBugs(pattern: 'target/spotbugsXml.xml')
          recordIssues enabledForFailure: true, tool: pmdParser(pattern: 'target/pmd.xml')
        }
        failure{
          script{
            sh 'docker compose -f docker-compose-test.yml down --remove-orphans'
          }
          error "Test failure. Stopping pipeline execution!"
        } 
        cleanup{
          script{
            sh 'sudo rm -rf target/'
          }
        }
      }
    }

    stage('Run aaa-server for Integration Test'){
      steps{
        script{
            sh 'docker/runIntegTests.sh'
            sh 'docker compose -f docker-compose-test.yml up -d integTest'
            sh 'sleep 45'
        }
      }
      post{
        failure{
          script{
            sh 'mvn flyway:clean -Dflyway.configFiles=/home/ubuntu/configs/aaa-flyway.conf'
            sh 'docker compose -f docker-compose-test.yml down --remove-orphans'
          }
          cleanWs deleteDirs: true, disableDeferredWipeout: true, patterns: [[pattern: 'src/main/resources/db/migration/V*__Add_Integration_Test_data.sql', type: 'INCLUDE']]
        }
      }
    }

    stage('Integration Test & OWASP ZAP pen test'){
      steps{
        node('built-in') {
          script{
            startZap ([host: '0.0.0.0', port: 8090, zapHome: '/var/lib/jenkins/tools/com.cloudbees.jenkins.plugins.customtools.CustomTool/OWASP_ZAP/ZAP_2.11.0'])
            sh 'curl http://0.0.0.0:8090/JSON/pscan/action/disableScanners/?ids=10096'
            catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
            }
          }
        }
        script{
            sh 'cp /home/ubuntu/configs/aaa-config-integ.json configs/config-integ.json'
            sh 'mvn test-compile failsafe:integration-test  -DskipUnitTests=true -DintTestProxyHost=jenkins-master-priv -DintTestProxyPort=8090 -DintTestHost=jenkins-slave1 -DintTestPort=8443'
            }
        node('built-in') {
          script{
            runZapAttack()
            }
        }
      }
      post{
        always{
          xunit (
             thresholds: [ skipped(failureThreshold: '0'), failed(failureThreshold: '0') ],
             tools: [ JUnit(pattern: 'target/failsafe-reports/*.xml') ]
             )
          node('built-in') {
            script{
              archiveZap failHighAlerts: 1, failMediumAlerts: 1, failLowAlerts: 2
            }  
          }
        }
        failure{
          error "Test failure. Stopping pipeline execution!"
        }
        cleanup{
          script{
            sh 'mvn flyway:clean -Dflyway.configFiles=/home/ubuntu/configs/aaa-flyway.conf'
            sh 'docker compose -f docker-compose-test.yml down --remove-orphans'
          }
          cleanWs deleteDirs: true, disableDeferredWipeout: true, patterns: [[pattern: 'src/main/resources/db/migration/V*__Add_Integration_Test_data.sql', type: 'INCLUDE']]
        }
      }
    }

    stage('Continuous Deployment') {
      when {
        allOf {
          anyOf {
            changeset "docker/**"
            changeset "docs/**"
            changeset "pom.xml"
            changeset "src/main/**"
            triggeredBy cause: 'UserIdCause'
          }
          expression {
            return env.GIT_BRANCH == 'origin/main';
          }
        }
      }
      stages {
        stage('Push Images') {
          steps {
            script {
              docker.withRegistry( registryUri, registryCredential ) {
                devImage.push("5.0.0-alpha-${env.GIT_HASH}")
                deplImage.push("5.0.0-alpha-${env.GIT_HASH}")
              }
            }
          }
        }
        stage('Docker Swarm deployment') {
          steps {
            script {
              sh "ssh azureuser@docker-swarm 'docker service update auth_auth --image ghcr.io/datakaveri/aaa-depl:5.0.0-alpha-${env.GIT_HASH}'"
              sh 'sleep 15'
              sh '''#!/bin/bash 
              response_code=$(curl -s -o /dev/null -w \'%{http_code}\\n\' --connect-timeout 5 --retry 5 --retry-connrefused -XGET https://authvertx.iudx.io/apis)

              if [[ "$response_code" -ne "200" ]]
              then
                echo "Health check failed"
                exit 1
              else
                echo "Health check complete; Server is up."
                exit 0
              fi
              '''                
            }
          }
          post{
            failure{
              error "Failed to deploy image in Docker Swarm"
            }
          }
        }
      }
    }
  }
  post{
    failure{
      script{
        if (env.GIT_BRANCH == 'origin/main')
        emailext recipientProviders: [buildUser(), developers()], to: '$AAA_RECIPIENTS, $DEFAULT_RECIPIENTS', subject: '$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS!', body: '''$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS:
Check console output at $BUILD_URL to view the results.'''
      }
    }
  }
}
