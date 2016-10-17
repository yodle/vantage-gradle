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

import com.jasonwjones.jolo.TableColumnList
import com.jasonwjones.jolo.TablePrinter

class CheckPrinter {
  def print(def fatalIssues, def nonFatalIssues) {
    if (!fatalIssues.empty || !nonFatalIssues.empty) {
      TableColumnList table = new TableColumnList.Builder()
              .add("Fatal?", 7)
              .add("Component", 30)
              .add("Version", 10)
              .add("Level", 11)
              .add("Id", 20)
              .add("Message", 80)
              .build();

      new TablePrinter().outputTable(table, fatalIssues + nonFatalIssues)
    }
  }
}
