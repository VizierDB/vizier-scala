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

import info.vizierdb.types._

case class PythonPackage(
  name: String,
  version: Option[String]
)

object PythonPackage
{
  def apply(nv: (String, String)): PythonPackage = PythonPackage(nv._1, Some(nv._2))
}

case class PythonEnvironmentDescriptor(
  name: String,
  id: Identifier,
  pythonVersion: String,
  revision: Identifier,
  packages: Seq[PythonPackage]
)

case class PythonEnvironmentSummary(
  name: String,
  id: Identifier,
  revision: Identifier,
  pythonVersion: String,
)

case class PythonSettingsSummary(
  environments: Seq[PythonEnvironmentSummary],
  versions: Seq[String]
)