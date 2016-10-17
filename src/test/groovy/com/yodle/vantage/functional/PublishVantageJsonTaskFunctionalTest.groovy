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
import com.yodle.vantage.PublishVantageJsonTask
import groovy.json.JsonSlurper
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.util.concurrent.Executors

class PublishVantageJsonTaskFunctionalTest extends Specification {
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

  def "publish task publishes report"() {
    given:
    HttpExchange capturedHttpExchange
    String capturedBody
    server.createContext('/', new HttpHandler() {

          @Override
          void handle(HttpExchange httpExchange) throws IOException {
            capturedHttpExchange = httpExchange
            httpExchange.sendResponseHeaders(200, 0)
            capturedBody = httpExchange.getRequestBody().text
            httpExchange.responseBody.write("{}".bytes)
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
    vantagePublish(gradleVersion, false)

    then:
    capturedHttpExchange != null
    capturedBody !=  null

    capturedHttpExchange.getRequestMethod().equals("PUT")
    capturedHttpExchange.getRequestURI().getPath().equals("/api/v1/components/${groupName}:${moduleName}/versions/${version}".toString())
    capturedHttpExchange.getRequestHeaders().get('Content-Type').equals(['application/json'])

    def body = new JsonSlurper().parse(capturedBody.bytes)
    body.component.equals("${groupName}:${moduleName}".toString())
    body.version.equals(version)

    where:
    gradleVersion << TestedVersions.GRADLE_VERSIONS
  }


  def "publish task propogates error on http error"() {
    given:
    boolean called = false
    server.createContext('/', new HttpHandler() {

          @Override
          void handle(HttpExchange httpExchange) throws IOException {
            httpExchange.sendResponseHeaders(500, 0)
            called = true
            httpExchange.responseBody.write("{}".bytes)
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
    vantagePublish(gradleVersion, true)

    then:
    called

    where:
    gradleVersion << TestedVersions.GRADLE_VERSIONS
  }

  def "publish task errors out if version is not set"() {
    given:
    buildFile << """
plugins {
  id 'com.yodle.vantage'
  id 'java'
}
vantage.version = ''
"""
    when:
    def result = vantagePublish(gradleVersion, true)


    then:
    result.output.contains(PublishVantageJsonTask.NO_VERSION_ERROR_MESSAGE)

    where:
    gradleVersion << TestedVersions.GRADLE_VERSIONS
  }

  private BuildResult vantagePublish(def gradleVersion, boolean expectFailure) {
    def runner = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withPluginClasspath()
            .withArguments('vantagePublish')
            .withGradleVersion(gradleVersion)
    expectFailure ? runner.buildAndFail() : runner.build()
  }
}
