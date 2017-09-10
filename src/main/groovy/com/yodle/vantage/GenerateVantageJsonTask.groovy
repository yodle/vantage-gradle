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

import com.google.common.collect.Sets
import com.yodle.vantage.domain.Dependency
import com.yodle.vantage.domain.Report
import com.yodle.vantage.domain.Version
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject

class GenerateVantageJsonTask extends DefaultTask implements ReportProvider
{
  public static final String DEFAULT_PROFILE = "default"
  public static final String LATEST_VERSION = "latest"

  private Report report;
  ReportPrinter reportPrinter;

  @Inject
  public GenerateVantageJsonTask() {
    reportPrinter = new ReportPrinter()
  }

  @TaskAction
  public void generate()
  {
    def vantage = project.extensions.getByType(VantageExtension)

    def resolvedDependencies = project.configurations
            .findAll(isResolvable)
            .inject([], aggregateComponentProfileTuples)
            .inject(new HashMap<Version, Set<String>>(), groupProfilesByVersion(vantage.includeProjectDependencyVersions))
            .collect { new Dependency(it.key, it.value) }

    def requestedDependencies = project.configurations.getByName(DEFAULT_PROFILE).allDependencies
            .findAll { !it.name.equals(project.name) }
            .collect { toVersionFromDependency(it, vantage.includeProjectDependencyVersions) }
            .collect { new Dependency(it, Sets.newHashSet(DEFAULT_PROFILE)) }

    this.report = new Report()
    report.component = vantage.componentName()
    report.version = vantage.version
    report.resolvedDependencies.addAll(resolvedDependencies)
    report.requestedDependencies.addAll(requestedDependencies)

    reportPrinter.printReport(project.buildDir, report)
  }

  private static Closure<Boolean> isResolvable = {
    //if isCanBeResolved does not exist in the current gradle version, it must be a version of gradle without
    //unresolvable versions.  See https://discuss.gradle.org/t/3-4-rc-1-3-3-resolving-configurations-may-be-disallowed-and-throw-illegalstateexception/21470
    !it.metaClass.respondsTo(it, 'isCanBeResolved') || it.isCanBeResolved()
  }

  Closure<List<Tuple2<String, ResolvedComponentResult>>> aggregateComponentProfileTuples = {
    components, Configuration configuration ->
      components + configuration.incoming.resolutionResult.allComponents
              .findAll { !it.getModuleVersion().name.equals(project.name) }
              .collect { [configuration.name, it] }
  }

  Closure<Map<Version, Set<String>>> groupProfilesByVersion(boolean includeProjectDependencyVersions) {
    {
      versions, profileComponentTuple ->
        def (String profile, ResolvedComponentResult cmp) = profileComponentTuple

        def version = toVersionFromResolvedComponent(cmp, includeProjectDependencyVersions)

        if (!versions.containsKey(version)) {
          addRequestedDependenciesToVersion(cmp, version)
          versions.put(version, new HashSet<String>())
        }
        versions.get(version).add(profile)
        versions
    }
  }

  Version toVersionFromResolvedComponent(ResolvedComponentResult component, boolean includeProjectDependencyVersions) {
    if (component.getId() instanceof ProjectComponentIdentifier) {
      def otherProject = project.getRootProject().project(component.getId().asType(ProjectComponentIdentifier).projectPath)
      return toVersionFromProject(otherProject, includeProjectDependencyVersions)
    } else {
      return toVersionFromModule(component.getModuleVersion())
    }
  }

  Version toVersionFromDependency(org.gradle.api.artifacts.Dependency component, boolean includeProjectDependencyVersions) {
    if (component instanceof ProjectDependency) {
      def otherProject = component.getDependencyProject().getProject()
      return toVersionFromProject(otherProject, includeProjectDependencyVersions)
    } else {
      return toVersionFromModule(component)
    }
  }

  private Version toVersionFromModule(def moduleVersion) {
    new Version(moduleVersion.getVersion(), "${moduleVersion.getGroup()}:${moduleVersion.getName()}")
  }

  private Version toVersionFromProject(Project otherProject, boolean includeProjectDependencyVersions) {
    String version = includeProjectDependencyVersions ? otherProject.vantage.version : LATEST_VERSION
    new Version(version, otherProject.vantage.componentName())
  }

  void addRequestedDependenciesToVersion(ResolvedComponentResult cmp, Version version) {
    Set<Dependency> requested = new HashSet<>()
    cmp.getDependencies().each {
      if (it.requested instanceof ModuleComponentSelector) {
        requested.add(new Dependency(new Version(it.requested.version, "${it.requested.group}:${it.requested.module}"), Sets.newHashSet(DEFAULT_PROFILE)))
      }
    }
    version.setRequestedDependencies(requested)
  }

  public Report getReport() {
    return report.clone()
  }
}
