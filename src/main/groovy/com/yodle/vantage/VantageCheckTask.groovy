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

import org.apache.commons.lang.RandomStringUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

class VantageCheckTask extends DefaultTask {
  public static final String FATAL_ISSUES_ERROR_MESSAGE = "Fatal issues detected affecting one or more dependencies.  Please upgrade those dependencies to newer versions that are not affected by these issues."

  ReportProvider reportProvider
  VantageService vantageService;
  CheckPrinter checkPrinter;

  def strictMode = true
  def defaultThreshold = "CRITICAL"
  //Allow for setting a higher or lower threshold than the default for a specific component.  E.g. You may want to block
  //the build if a particular component has any issue, not just a critical one.
  def thresholdsByComponent = [:]

  def levelThresholds = [
          "DEPRECATION": 0,
          "MINOR"      : 10,
          "MAJOR"      : 20,
          "CRITICAL"   : 30,
          "IGNORED"    : 100 //Allow setting the threshold for a component high enough that it will ignore all issues
  ]

  VantageCheckTask() {
    vantageService = new VantageService(logger)
    checkPrinter = new CheckPrinter()
  }

  @TaskAction
  public void check() {
    def vantage = project.extensions.getByType(VantageExtension)

    execute(vantage, strictMode)
  }

  private void execute(VantageExtension vantage, boolean strictMode) {
    def response = makeRequest(vantage, strictMode)

    processIssues(response)
  }

  void processIssues(def response) {
    def fatalIssues = []
    def nonFatalIssues = []

    response.resolvedDependencies.each {
      dep ->
        def componentFatalThreshold = levelThresholds.getOrDefault(
                thresholdsByComponent.getOrDefault(dep.version.component, defaultThreshold),
                0
        )
        dep.version.directIssues.each {
          issue ->
            def issueSeverity = levelThresholds.getOrDefault(issue.level, 0)
            if (issueSeverity >= componentFatalThreshold) {
              fatalIssues.add(createRow(dep, issue, true))
            } else {
              nonFatalIssues.add(createRow(dep, issue, false))
            }
        }
    }

    checkPrinter.print(fatalIssues, nonFatalIssues)

    if (!fatalIssues.empty) {
      throw new GradleException(FATAL_ISSUES_ERROR_MESSAGE)
    }
  }

  def makeRequest(VantageExtension vantage, boolean strictMode) {
    def report = reportProvider.getReport()
    //prevent version conflicts.  This relies on getReport() returning a clone and not the pristine instance
    report.setVersion(report.getVersion() + RandomStringUtils.randomAlphanumeric(16))
    vantageService.publishReportDryRun(vantage.server, report, strictMode)
  }

  public List<String> createRow(def dep, def issue, boolean fatal) {
    return [fatal ? "FATAL" : "", dep.version.component, dep.version.version, issue.level, issue.id, issue.message]
  }

  public void componentLevel(String component, String level) {
    thresholdsByComponent.put(component, level)
  }
}
