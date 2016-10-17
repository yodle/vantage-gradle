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
import groovy.json.JsonBuilder
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.StopActionException

class VantageService {

  private final Logger logger;

  VantageService(Logger logger) {
    this.logger = logger
  }

  public def publishReport(String vantageServer, Report report) {
    putReport(vantageServer, report, true, false)
  }

  public def publishReportDryRun(String vantageServer, Report report, boolean strictMode) {
    putReport(vantageServer, report, strictMode, true)
  }

  private def putReport(String vantageServer, Report report, boolean strictMode, boolean dryRun) {
    try {
      def http = new HTTPBuilder("http://" + vantageServer)
      http.request(Method.PUT) {
        uri.path = "/api/v1/components/${report.getComponent()}/versions/${report.getVersion()}"
        if (dryRun)
          uri.query = ['dryRun': 'true']
        requestContentType = ContentType.JSON
        contentType = ContentType.JSON
        body = new JsonBuilder(report).toString()
      }
    } catch (Exception e) {
      if (strictMode) {
        throw e;
      } else {
        def errorMessage = "Error occurred attempting to contact vantage server ${vantageServer}.  Ignoring due to strictMode=false. "
        logger.warn(errorMessage, e);
        throw new StopActionException(errorMessage)
      }
    }
  }
}
