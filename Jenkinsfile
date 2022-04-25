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
          sh 'docker-compose -f docker-compose-test.yml up test'
        }
        xunit (
          thresholds: [ skipped(failureThreshold: '0'), failed(failureThreshold: '0') ],
          tools: [ JUnit(pattern: 'target/surefire-reports/*.xml') ]
        )
        jacoco classPattern: 'target/classes', execPattern: 'target/jacoco.exec', sourcePattern: 'src/main/java', exclusionPattern:'iudx/aaa/server/apiserver/ApiServerVerticle.class,**/*VertxEBProxy.class,**/Constants.class,**/*VertxProxyHandler.class,**/*Verticle.class,iudx/aaa/server/deploy/*.class'
      }
      post{
        failure{
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
            sh 'scp src/test/resources/Integration_Test.postman_collection.json jenkins@jenkins-master:/var/lib/jenkins/iudx/aaa/Newman/'
            sh 'docker-compose -f docker-compose-test.yml up -d integTest'
            sh 'sleep 45'
        }
      }
      post{
        failure{
          script{
            sh 'mvn flyway:clean -Dflyway.configFiles=/home/ubuntu/configs/3.5.0/aaa-flyway.conf'
          }
          cleanWs deleteDirs: true, disableDeferredWipeout: true, patterns: [[pattern: 'src/main/resources/db/migration/V?__Add_Integration_Test_data.sql', type: 'INCLUDE']]
        }
      }
    }

    stage('Integration Test & OWASP ZAP pen test'){
      steps{
        node('master') {
          script{
            startZap ([host: 'localhost', port: 8090, zapHome: '/var/lib/jenkins/tools/com.cloudbees.jenkins.plugins.customtools.CustomTool/OWASP_ZAP/ZAP_2.11.0'])
            sh 'curl http://127.0.0.1:8090/JSON/pscan/action/disableScanners/?ids=10096'
            catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
              sh 'HTTP_PROXY=\'127.0.0.1:8090\' newman run /var/lib/jenkins/iudx/aaa/Newman/Integration_Test.postman_collection.json -e /home/ubuntu/configs/3.5.0/aaa-postman-env.json --insecure -r htmlextra --reporter-htmlextra-export /var/lib/jenkins/iudx/aaa/Newman/report/report.html --reporter-htmlextra-skipSensitiveData'
            }
            runZapAttack()
          }
        }
      }
      post{
        always{
          node('master') {
            script{
              archiveZap failHighAlerts: 1, failMediumAlerts: 1, failLowAlerts: 27
            }  
            publishHTML([allowMissing: false, alwaysLinkToLastBuild: true, keepAll: true, reportDir: '/var/lib/jenkins/iudx/aaa/Newman/report/', reportFiles: 'report.html', reportName: 'HTML Report', reportTitles: '', reportName: 'Integration Test Report'])
          }
        }
        failure{
          error "Test failure. Stopping pipeline execution!"
        }
        cleanup{
          script{
            sh 'mvn flyway:clean -Dflyway.configFiles=/home/ubuntu/configs/3.5.0/aaa-flyway.conf'
            sh 'docker-compose -f docker-compose-test.yml down --remove-orphans'
          }
          cleanWs deleteDirs: true, disableDeferredWipeout: true, patterns: [[pattern: 'src/main/resources/db/migration/V?__Add_Integration_Test_data.sql', type: 'INCLUDE']]
        }
      }
    }

    stage('Push Image') {
      when{
        expression {
          return env.GIT_BRANCH == 'origin/3.5.0';
        }
      }
      steps{
        script {
          docker.withRegistry( registryUri, registryCredential ) {
            devImage.push("3.5.0-${env.GIT_HASH}")
            deplImage.push("3.5.0-${env.GIT_HASH}")
          }
        }
      }
    }
  }
}
