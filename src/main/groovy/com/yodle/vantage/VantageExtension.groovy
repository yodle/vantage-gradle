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

class VantageExtension {
  def moduleName
  def groupName
  def version
  def server = 'localhost:8080'
  //on some occasions, a project may not want to include its project dependencies' versions.
  //for example, if the project is an application and its project dependencies are a client
  //library published on a different cycle than the application itself
  def includeProjectDependencyVersions = true

  public def componentName() {
    if (groupName != null && groupName.length() > 0)
      return "${groupName}:${moduleName}"
    else
      return moduleName
  }
}
