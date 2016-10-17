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
package com.yodle.vantage

import com.yodle.vantage.domain.Report
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.StopActionException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class VantageCheckTaskTest extends Specification {

  private final static String VANTAGE_SERVER = 'the-server'
  public static final String COMPONENT_NAME = 'the-component'
  public static final String VERSION = 'the-version'
  public static final String ISSUE_ID = 'issue-id'
  public static final String MESSAGE = 'msg'

  Project project
  VantageCheckTask checkTask

  ReportProvider reportProvider
  VantageService vantageService
  CheckPrinter checkPrinter
  Report report

  def setup() {
    report = new Report()
    reportProvider = Mock(ReportProvider) {
      getReport() >> { report }
    }

    vantageService = Mock(VantageService)
    checkPrinter = Mock(CheckPrinter)

    project = ProjectBuilder.builder().build()
    project.apply plugin: 'com.yodle.vantage'
    project.vantage.server = VANTAGE_SERVER

    checkTask = project.vantageCheck
    checkTask.reportProvider = reportProvider
    checkTask.vantageService = vantageService
    checkTask.checkPrinter = checkPrinter
  }

  def "given strict mode off and transport failure, check propagates StopActionException"() {
    given:
    checkTask.strictMode = false

    when:
    checkTask.check()

    then:
    1 * vantageService.publishReportDryRun(VANTAGE_SERVER, report, false) >> {throw new StopActionException() }
    thrown StopActionException
  }

  def "given strict mode on and transport failure, check propagates underlying exception"() {
    given:
    checkTask.strictMode = true

    when:
    checkTask.check()

    then:
    1 * vantageService.publishReportDryRun(VANTAGE_SERVER, report, true) >> {throw new RuntimeException() }
    thrown RuntimeException
  }

  def "version has disambiguating string appended prior to retrieving report"() {
    given:
    project.version = '1.0.0'
    report.version = '1.0.0'

    when:
    checkTask.check()

    then:
    1 * vantageService.publishReportDryRun(VANTAGE_SERVER, report, _) >> { [resolvedDependencies: []] }
    report.version.startsWith(project.version)
    !report.version.equals(project.version)
    1 * checkPrinter.print([], [])
  }

  def "processIssues categorizes fatal issues based on default threshold and component threshold"() {
    given:
    checkTask.defaultThreshold = defaultThreshold
    if (componentLevel != null) {
      checkTask.componentLevel(COMPONENT_NAME, componentLevel)
    }

    when:
    checkTask.processIssues([resolvedDependencies: [
            createVersion(COMPONENT_NAME, VERSION, issueLevel)
    ]])

    then:
    thrown GradleException
    1 * checkPrinter.print([['FATAL', COMPONENT_NAME, VERSION, issueLevel, ISSUE_ID, MESSAGE]], [])

    where:
    defaultThreshold | componentLevel | issueLevel
    'DEPRECATED'     | null           | 'DEPRECATED'
    'DEPRECATED'     | null           | 'CRITICAL'
    'MINOR'          | null           | 'MINOR'
    'MINOR'          | null           | 'MAJOR'
    'CRITICAL'       | null           | 'CRITICAL'
    'DEPRECATED'     | null           | 'UNKNOWN'
    'DEPRECATED'     | 'DEPRECATED'   | 'DEPRECATED'
    'IGNORED'        | 'MINOR'        | 'MAJOR'
    'DEPRECATED'     | 'MAJOR'        | 'CRITICAL'
  }

  def "processIssues categorizes non-fatal issues based on default threshold and component threshold"() {
    given:
    checkTask.defaultThreshold = defaultThreshold
    if (componentLevel != null) {
      checkTask.componentLevel(COMPONENT_NAME, componentLevel)
    }

    when:
    checkTask.processIssues([resolvedDependencies: [
            createVersion(COMPONENT_NAME, VERSION, issueLevel)
    ]])

    then:
    1 * checkPrinter.print([], [['', COMPONENT_NAME, VERSION, issueLevel, ISSUE_ID, MESSAGE]])

    where:
    defaultThreshold | componentLevel | issueLevel
    'CRITICAL'       | null           | 'DEPRECATED'
    'MAJOR'          | null           | 'MINOR'
    'MINOR'          | null           | 'UNKNOWN'
    'DEPRECATED'     | 'MINOR'        | 'DEPRECATED'
    'CRITICAL'       | 'MAJOR'        | 'MINOR'
    'DEPRECATED'     | 'IGNORED'      | 'CRITICAL'
  }

  def "processIssue categorizes multiple issues for the same component"() {
    given:
    checkTask.defaultThreshold = 'MINOR'

    when:
    checkTask.processIssues([resolvedDependencies: [
            createVersion(COMPONENT_NAME, VERSION, 'DEPRECATED'),
            createVersion(COMPONENT_NAME, VERSION, 'MINOR'),
            createVersion(COMPONENT_NAME, VERSION, 'CRITICAL')
    ]])

    then:
    thrown GradleException
    1 * checkPrinter.print(
            [
                    ['FATAL', COMPONENT_NAME, VERSION, 'MINOR', ISSUE_ID, MESSAGE],
                    ['FATAL', COMPONENT_NAME, VERSION, 'CRITICAL', ISSUE_ID, MESSAGE]
            ],
            [
                    ['', COMPONENT_NAME, VERSION, 'DEPRECATED', ISSUE_ID, MESSAGE]
            ]
    )
  }

  def "processIssue categorizes issues for different components"() {
    given:
    checkTask.componentLevel(COMPONENT_NAME, 'MAJOR')

    when:
    checkTask.processIssues([resolvedDependencies: [
            [
                    version: [
                            component   : COMPONENT_NAME,
                            version     : VERSION,
                            directIssues: [
                                    createIssue('DEPRECATED'),
                                    createIssue('MAJOR')
                            ]
                    ]
            ],
            [
                    version: [
                            component   : COMPONENT_NAME+'2',
                            version     : VERSION,
                            directIssues: [
                                    createIssue('DEPRECATED'),
                                    createIssue('MAJOR'),
                            ]
                    ]
            ]
    ]])

    then:
    thrown GradleException
    1 * checkPrinter.print(
            [
                    ['FATAL', COMPONENT_NAME, VERSION, 'MAJOR', ISSUE_ID, MESSAGE]
            ],
            [
                    ['', COMPONENT_NAME, VERSION, 'DEPRECATED', ISSUE_ID, MESSAGE],
                    ['', COMPONENT_NAME+'2', VERSION, 'DEPRECATED', ISSUE_ID, MESSAGE],
                    ['', COMPONENT_NAME+'2', VERSION, 'MAJOR', ISSUE_ID, MESSAGE]
            ]
    )
  }

  private def createVersion (String componentName, String version, String issueLevel) {
    [
            version: [
                    component   : componentName,
                    version     : version,
                    directIssues: [
                            createIssue(issueLevel)
                    ]
            ]
    ]
  }

  private def createIssue(String issueLevel) {
    [
            level  : issueLevel,
            id     : ISSUE_ID,
            message: MESSAGE
    ]
  }
}
