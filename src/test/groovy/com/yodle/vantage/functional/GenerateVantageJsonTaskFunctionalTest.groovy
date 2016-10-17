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

import groovy.json.JsonSlurper
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class GenerateVantageJsonTaskFunctionalTest extends Specification {
  public static final String DEPENDENCY_BUILD_FILE = """
plugins {
  id 'com.yodle.vantage'
  id 'java'
}

repositories {
  mavenCentral()
}

dependencies {
  compile 'ch.qos.logback:logback-classic:1.1.7'
  testCompile ('org.spockframework:spock-core:1.0-groovy-2.4') {
    exclude module: 'groovy-all'
  }
}
      """
  @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
  File buildFile

  def setup() {
    buildFile = testProjectDir.newFile('build.gradle')
  }

  def "generate creates report file with component and version properly set "() {
    given:
    def moduleName = 'test-name'
    def version = '1.0.0'
      buildFile << """
plugins {
  id 'com.yodle.vantage'
  id 'java'
}

vantage.moduleName = '$moduleName'
vantage.version = '$version'
      """

    when:
    vantageGenerate(gradleVersion)

    def report = new JsonSlurper().parseText(new File(testProjectDir.root, "build/reports/vantage/${moduleName}-${version}.vantage.json").text)

    then:
      report.component.equals(moduleName)
      report.version.equals(version)
    where:
    gradleVersion << TestedVersions.GRADLE_VERSIONS
  }

  def "generate with a group name creates report file with group name  "() {
    given:
    def moduleName = 'test-name'
    def groupName = 'group'
    def version = '1.0.0'
      buildFile << """
plugins {
  id 'com.yodle.vantage'
  id 'java'
}

vantage.moduleName = '$moduleName'
vantage.version = '$version'
vantage.groupName = '$groupName'
      """

    when:
    vantageGenerate(gradleVersion)

    def report = new JsonSlurper().parseText(new File(testProjectDir.root, "build/reports/vantage/${groupName}:${moduleName}-${version}.vantage.json").text)
    then:
      report.component.equals("${groupName}:${moduleName}".toString())
      report.version.equals(version)

    where:
    gradleVersion << TestedVersions.GRADLE_VERSIONS
  }

  def "requested default dependencies are added to the report with default profile and no second level dependencies"() {
    given:
      buildFile << DEPENDENCY_BUILD_FILE

    when:
    vantageGenerate(gradleVersion)

    def report = slurpVantageReport()
    then:
      report.requestedDependencies.equals([
              [
                      profiles:['default'],
                      version:[
                              component:'ch.qos.logback:logback-classic',
                              requestedDependencies:[],
                              version:'1.1.7'
                      ]
              ]
      ])

    where:
    gradleVersion << TestedVersions.GRADLE_VERSIONS
  }


  def "resolved dependencies are added to the report with appropriate profiles and second level dependencies"() {
    given:
      buildFile << DEPENDENCY_BUILD_FILE

    when:
    vantageGenerate(gradleVersion)

    def report = slurpVantageReport()
    then:
    //we deliberately don't check all profiles so as to reduce coupling with gradle profile details.  We just want to ensure
    //that we're picking up transitive profile inheritance, so we've picked some pretty basic java profiles
    verifyDependency('ch.qos.logback:logback-classic', '1.1.7', ['compile', 'testCompile', 'runtime', 'testRuntime'].toSet(), report)
    verifyDependency('ch.qos.logback:logback-core', '1.1.7', ['compile', 'testCompile', 'runtime', 'testRuntime'].toSet(), report)
    verifyDependency('org.slf4j:slf4j-api', '1.7.20', ['compile', 'testCompile', 'runtime', 'testRuntime'].toSet(), report)
    verifyDependency('junit:junit', '4.12', ['testCompile', 'testRuntime'].toSet(), report)
    verifyDependency('org.spockframework:spock-core', '1.0-groovy-2.4', ['testCompile', 'testRuntime'].toSet(), report)
    verifyDependency('org.hamcrest:hamcrest-core', '1.3', ['testCompile', 'testRuntime'].toSet(), report)

    where:
    gradleVersion << TestedVersions.GRADLE_VERSIONS
  }

  def "resolved dependencies include their requested dependencies"() {
    given:
      buildFile << DEPENDENCY_BUILD_FILE

    when:
    vantageGenerate(gradleVersion)

    def report = slurpVantageReport()
    then:
    //we could validate more components, but this satisfies that this dependency's direct requested dependencies are included
    //and that it's second order dependencies are not
    def component = findComponent(report, 'org.spockframework:spock-core')
    component != null
    component.version.requestedDependencies.size == 1
    component.version.requestedDependencies.get(0).profiles == ['default']
    component.version.requestedDependencies.get(0).version.component == 'junit:junit'
    component.version.requestedDependencies.get(0).version.version == '4.12'

    where:
    gradleVersion << TestedVersions.GRADLE_VERSIONS
  }

  def "subprojects are included as dependencies based on their vantage extension"() {
    given:
    buildFile << """
plugins {
  id 'com.yodle.vantage'
  id 'java'
}

dependencies {
  compile project(':subproject')
}
"""

    setupSubProject()

    when:
    vantageGenerate(gradleVersion)
    def report = slurpVantageReport()

    then:
    report.requestedDependencies.size == 1
    def subproject = report.requestedDependencies.get(0).version
    subproject.component == 'sub-project'
    subproject.version == '1.5'

    where:
    gradleVersion << TestedVersions.GRADLE_VERSIONS
  }

  def "subprojects use latest version if include project dependency versions is false"() {
    given:
    buildFile << """
plugins {
  id 'com.yodle.vantage'
  id 'java'
}

dependencies {
  compile project(':subproject')
}

vantage.includeProjectDependencyVersions = false
"""

    setupSubProject()

    when:
    vantageGenerate(gradleVersion)
    def report = slurpVantageReport()

    then:
    report.requestedDependencies.size == 1
    def subproject = report.requestedDependencies.get(0).version
    subproject.component == 'sub-project'
    subproject.version == 'latest'

    where:
    gradleVersion << TestedVersions.GRADLE_VERSIONS
  }

  def "vantageExtension defaults group name, module name, and version even if set after vantage applied"() {
    def moduleName = 'test-name'
    def version = '1.0.0'
    def groupName = 'group'
      buildFile << """
plugins {
  id 'com.yodle.vantage'
  id 'java'
}
group = '${groupName}'
version = '${version}'
      """

    def settingsGradle = testProjectDir.newFile('settings.gradle')
    settingsGradle << """
rootProject.name = '${moduleName}'
"""

    when:
    vantageGenerate(gradleVersion)

    def report = slurpVantageReport()

    then:
      report.component.equals("${groupName}:${moduleName}".toString())
      report.version.equals(version)

    where:
    gradleVersion << TestedVersions.GRADLE_VERSIONS
  }


  private void setupSubProject() {
    testProjectDir.newFolder('subproject')
    def subProjectBuildFile = testProjectDir.newFile('subproject/build.gradle')
    subProjectBuildFile << """
apply plugin: 'com.yodle.vantage'
apply plugin: 'java'

vantage.groupName = ''
vantage.moduleName = 'sub-project'
vantage.version = '1.5'
"""

    def settingsGradle = testProjectDir.newFile('settings.gradle')
    settingsGradle << """
include 'subproject'
"""
  }

  def verifyDependency(String component, String version, Set<String> expectedProfiles, def report) {
    def dep = findComponent(report, component)
    assert dep != null
    assert dep.version.version.equals(version)
    assert dep.profiles.containsAll(expectedProfiles)
    return true
  }

  private def findComponent(report, String component) {
    def dep = report.resolvedDependencies.find {
      it -> it.version.component.equals(component)
    }
    dep
  }

  private def slurpVantageReport() {
    new JsonSlurper().parseText(new File(testProjectDir.root, "build/reports/vantage").listFiles()[0].text)
  }

  private BuildResult vantageGenerate(def version) {
    GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withPluginClasspath()
            .withArguments(['vantageGenerate'])
            .withGradleVersion(version)
            .build()
  }
}
