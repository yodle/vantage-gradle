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

import org.gradle.api.Plugin
import org.gradle.api.Project

class VantageGradlePlugin implements Plugin<Project> {
  public static final String VANTAGE_GENERATE_TASK = "vantageGenerate"
  public static final String VANTAGE_PUBLISH_TASK = "vantagePublish"
  public static final String VANTAGE_CHECK_TASK = "vantageCheck"

  void apply(Project project) {
    project.extensions.create("vantage", VantageExtension);
    def vantage = project.extensions.getByType(VantageExtension)
    vantage.moduleName = project.name
    //lazy gstrings to defer resolution until needed
    vantage.groupName = "${->project.group}"
    vantage.version = "${->project.version}"

    project.tasks.create(VANTAGE_GENERATE_TASK, GenerateVantageJsonTask)
    project.tasks.create(VANTAGE_PUBLISH_TASK, PublishVantageJsonTask)
    project.tasks.create(VANTAGE_CHECK_TASK, VantageCheckTask)
    project.tasks.getByName(VANTAGE_PUBLISH_TASK) {
      dependsOn VANTAGE_GENERATE_TASK
      reportProvider = project.tasks.getByName(VANTAGE_GENERATE_TASK)
    }

    project.tasks.getByName(VANTAGE_CHECK_TASK) {
      dependsOn VANTAGE_GENERATE_TASK
      reportProvider = project.tasks.getByName(VANTAGE_GENERATE_TASK)
    }
  }
}
