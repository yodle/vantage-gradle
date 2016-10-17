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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject

class PublishVantageJsonTask extends DefaultTask
{
  public static final String NO_VERSION_ERROR_MESSAGE = 'Cannot generate or publish vantage info without vantage.version being set'

  VantageService vantageService;
  ReportProvider reportProvider

  def publishEnabled = true

  @Inject
  PublishVantageJsonTask() {
    vantageService = new VantageService(logger)
  }

  @TaskAction
  public void publish() {
    if (publishEnabled) {
      def vantage = project.extensions.getByType(VantageExtension)
      if (vantage.version.toString().equals('')) {
        throw new GradleException(NO_VERSION_ERROR_MESSAGE)
      }

      vantageService.publishReport(vantage.server, reportProvider.getReport())
    }
  }
}