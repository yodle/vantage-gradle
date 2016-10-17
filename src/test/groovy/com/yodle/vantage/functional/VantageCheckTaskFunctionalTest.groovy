/*
 * Copyright 2016 Yodle, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yodle.vantage.functional

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import com.yodle.vantage.VantageCheckTask
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.util.concurrent.Executors

class VantageCheckTaskFunctionalTest extends Specification {
  private static final String DEPENDENCY_COMPONENT = 'dependency-1'
  public static final String DEPENDENCY_VERSION = '1.2.3'
  public static final String ISSUE_ID = 'issue-id'
  public static final String ISSUE_MESSAGE = 'stuffs bad'
  @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
  File buildFile
  def server

  def setup() {
    buildFile = testProjectDir.newFile('build.gradle')

    server = HttpServer.create(new InetSocketAddress(0), 0)

    server.with {
      setExecutor(Executors.newSingleThreadExecutor())
      start()
    }

  }

  def "check task sends report with dry run param and randomized version"() {
    given:
    HttpExchange capturedHttpExchange
    String capturedBody
    server.createContext('/', new HttpHandler() {

          @Override
          void handle(HttpExchange httpExchange) throws IOException {
            capturedHttpExchange = httpExchange
            httpExchange.sendResponseHeaders(200, 0)
            capturedBody = httpExchange.getRequestBody().text
            httpExchange.responseBody.write('{}'.bytes)
            httpExchange.responseBody.close()
          }
        })

    def moduleName = 'module'
    def groupName = 'group'
    def version = '1.0'

    buildFile << """
plugins {
  id 'com.yodle.vantage'
  id 'java'
}
vantage.moduleName = '${moduleName}'
vantage.groupName = '${groupName}'
vantage.version = '${version}'
vantage.server = 'localhost:${server.address.port}'
"""
    when:
    def result = vantageCheck(gradleVersion, false)

    then:
    capturedHttpExchange != null
    capturedBody !=  null

    capturedHttpExchange.getRequestMethod().equals("PUT")

    def expectedPathStart = "/api/v1/components/${groupName}:${moduleName}/versions/${version}".toString()
    capturedHttpExchange.getRequestURI().getPath().startsWith(expectedPathStart)
    !capturedHttpExchange.getRequestURI().getPath().equals(expectedPathStart)
    capturedHttpExchange.getRequestHeaders().get('Content-Type').equals(['application/json'])
    capturedHttpExchange.getRequestURI().getQuery().equals("dryRun=true")

    def body = new JsonSlurper().parse(capturedBody.bytes)
    body.component.equals("${groupName}:${moduleName}".toString())
    body.version.startsWith(version)
    !body.version.equals(version)

    where:
    gradleVersion << TestedVersions.GRADLE_VERSIONS
  }

  def "vantage check runs even if version is blank"() {
    given:
    writeBuildFile(null, null)
    buildFile << "vantage.version = ''\n"

    boolean called = false
    server.createContext('/', new HttpHandler() {

          @Override
          void handle(HttpExchange httpExchange) throws IOException {
            called = true
            httpExchange.sendResponseHeaders(200, 0)
            httpExchange.responseBody.write('{}'.bytes)
            httpExchange.responseBody.close()
          }
        })

    when:
    vantageCheck(gradleVersion, false)

    then:
    called

    where:
    gradleVersion << TestedVersions.GRADLE_VERSIONS

  }


  def "check task errors out if fatal issues are found"() {
    given:
    mockOutIssueResponse('CRITICAL')
    writeBuildFile(null, null)
    when:
    def result = vantageCheck(gradleVersion, true)

    then:
    result.output.contains(VantageCheckTask.FATAL_ISSUES_ERROR_MESSAGE)
    result.output =~ reportLineRegex(true, 'CRITICAL')

    where:
    gradleVersion << TestedVersions.GRADLE_VERSIONS
  }

  def "check task errors out if issues are fatal due to updated global level"() {
    given:
    mockOutIssueResponse('MINOR')
    writeBuildFile('MINOR', null)
    when:
    def result = vantageCheck(gradleVersion, true)

    then:
    result.output.contains(VantageCheckTask.FATAL_ISSUES_ERROR_MESSAGE)
    result.output =~ reportLineRegex(true, 'MINOR')

    where:
    gradleVersion << TestedVersions.GRADLE_VERSIONS
  }

  def "check task does not error out if non-fatal issues  are found"() {
    given:
    mockOutIssueResponse('MINOR')
    writeBuildFile(null, null)

    when:
    def result = vantageCheck(gradleVersion, false)

    then:
    result.output =~ reportLineRegex(false, 'MINOR')

    where:
    gradleVersion << TestedVersions.GRADLE_VERSIONS
  }


  def "check task errors out if fatal issues are found due to component level overrides"() {
    given:
    mockOutIssueResponse('MINOR')
    writeBuildFile(null, 'MINOR')

    when:
    def result = vantageCheck(gradleVersion, true)

    then:
    result.output.contains(VantageCheckTask.FATAL_ISSUES_ERROR_MESSAGE)
    result.output =~ reportLineRegex(true, 'MINOR')

    where:
    gradleVersion << TestedVersions.GRADLE_VERSIONS
  }


  def "check task does not error out if fatal issues are ignored due to individual level overrides"() {
    mockOutIssueResponse('MINOR')
    writeBuildFile('MINOR', 'CRITICAL')

    when:
    def result = vantageCheck(gradleVersion, false)

    then:
    result.output =~ reportLineRegex(false, 'MINOR')

    where:
    gradleVersion << TestedVersions.GRADLE_VERSIONS
  }

  def "transport errors with strict mode on cause build failure"() {
    given:
    writeBuildFile(null, null)
    boolean called = false
    server.createContext('/', new HttpHandler() {

          @Override
          void handle(HttpExchange httpExchange) throws IOException {
            called = true
            httpExchange.sendResponseHeaders(500, 0)
            httpExchange.responseBody.close()
          }
        })

    when:
    def result =vantageCheck(gradleVersion, true)

    then:
    called

    where:
    gradleVersion << TestedVersions.GRADLE_VERSIONS
  }

  def "transport errors with strict mode off do not cause build failure"() {
    given:
    writeBuildFile(null, null)
    buildFile << 'vantageCheck.strictMode = false\n'
    boolean called = false
    server.createContext('/', new HttpHandler() {

          @Override
          void handle(HttpExchange httpExchange) throws IOException {
            called = true
            httpExchange.sendResponseHeaders(500, 0)
            httpExchange.responseBody.close()
          }
        })

    when:
    def result =vantageCheck(gradleVersion, false)

    then:
    called

    where:
    gradleVersion << TestedVersions.GRADLE_VERSIONS
  }

  private void writeBuildFile(String defaultLevel, String individualLevel) {
    buildFile << """
plugins {
  id 'com.yodle.vantage'
  id 'java'
}
vantage.server = 'localhost:${server.address.port}'
"""

    if (defaultLevel) {
      buildFile << "vantageCheck.defaultThreshold = '${defaultLevel}'\n"
    }

    if (individualLevel) {
      buildFile << "vantageCheck.componentLevel '${DEPENDENCY_COMPONENT}', '${individualLevel}'\n"
    }
  }


  private void mockOutIssueResponse(String level) {
    server.createContext('/', new HttpHandler() {
      @Override
      void handle(HttpExchange httpExchange) throws IOException {
        httpExchange.sendResponseHeaders(200, 0)

        httpExchange.responseBody.write(
                JsonOutput.toJson([resolvedDependencies: [
                        [
                                version: [
                                        component   : DEPENDENCY_COMPONENT,
                                        version     : DEPENDENCY_VERSION,
                                        directIssues: [
                                                [
                                                        id     : ISSUE_ID,
                                                        level  : level,
                                                        message: ISSUE_MESSAGE
                                                ]
                                        ]
                                ]
                        ]
                ]]).bytes
        )
        httpExchange.responseBody.close()
      }
    })
  }

  private String reportLineRegex(boolean fatal, String level) {
    def fatalStr = fatal ? 'FATAL' : ''
    /${fatalStr}\\s+\\|${DEPENDENCY_COMPONENT}\\s+\\|1\\.2\\.3\\s+\\|${level}\\s+\\|${ISSUE_ID}\\s+\\|${ISSUE_MESSAGE}/
  }

  private BuildResult vantageCheck(String gradleVersion, boolean fatal) {
    def runner = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withPluginClasspath()
            .withGradleVersion(gradleVersion)
            .withArguments('vantageCheck')

    fatal ? runner.buildAndFail() : runner.build()
  }


}
