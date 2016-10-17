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
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class GenerateVantageJsonTaskTest extends Specification {
  Project project
  GenerateVantageJsonTask generateTask

  ReportPrinter reportPrinter;

  def setup() {
    reportPrinter = Mock(ReportPrinter)

    project = ProjectBuilder.builder().withName('the-project-name').build()
    project.apply plugin: 'com.yodle.vantage'

    generateTask = project.vantageGenerate
    generateTask.reportPrinter = reportPrinter
  }

  def "report has correct component and version"() {
    given:
    project.apply plugin: 'java'
    project.version = '1.2.3'

    Report report

    when:
    generateTask.generate()

    then:
    reportPrinter.printReport(project.buildDir, _) >> {
      report = it[1]
    }

    report.getComponent().equals(project.name)
    report.getVersion().equals(project.version)
  }

  def "report component name includes group if present"() {
    given:
    project.apply plugin: 'java'
    project.version = '1.2.3'
    project.group = 'the-group'

    Report report

    when:
    generateTask.generate()

    then:
    reportPrinter.printReport(project.buildDir, _) >> {
      report = it[1]
    }

    report.getComponent().equals("${project.group}:${project.name}".toString())
    report.getVersion().equals(project.version)
  }

  def "report has no dependencies if project has none"() {
    given:
    project.apply plugin: 'java'

    Report report

    when:
    generateTask.generate()

    then:
    reportPrinter.printReport(project.buildDir, _) >> {
      report = it[1]
    }
    report.requestedDependencies.isEmpty()
    report.resolvedDependencies.isEmpty()
  }


}
