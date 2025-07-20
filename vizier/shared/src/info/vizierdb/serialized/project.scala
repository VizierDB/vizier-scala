/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
package info.vizierdb.serialized

import info.vizierdb.types.Identifier
import info.vizierdb.nativeTypes.DateTime
import info.vizierdb.nativeTypes

case class ProjectSummary(
  id: Identifier,
  createdAt: DateTime,
  lastModifiedAt: DateTime,
  defaultBranch: Identifier,
  properties: PropertyList.T,
)
{
  def toDescription(branches: Seq[BranchSummary]) =
    ProjectDescription(
      id = id,
      createdAt = createdAt,
      lastModifiedAt = lastModifiedAt,
      defaultBranch = defaultBranch,
      properties = properties,
      branches = branches
    )

  def apply(key: String): Option[nativeTypes.JsValue] = 
    properties.find { _.key == key }
              .map { _.value }
}

case class ProjectDescription(
  id: Identifier,
  createdAt: DateTime,
  lastModifiedAt: DateTime,
  defaultBranch: Identifier,
  properties: PropertyList.T,
  branches: Seq[BranchSummary]
)
{
    def apply(key: String): Option[nativeTypes.JsValue] = 
    properties.find { _.key == key }
              .map { _.value }
}

case class ProjectList(
  projects: Seq[ProjectSummary],
)
