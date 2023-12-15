/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
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
package info.vizierdb.spark.caveats

import info.vizierdb.serialized.DatasetColumn
import info.vizierdb.nativeTypes.{ JsValue, Caveat }

/**
 * A js-native counterpart of the Mimir Data Container class.
 * 
 * This will be removed as soon as we merge Mimir into Vizier
 */
case class DataContainer (
  schema: Seq[DatasetColumn],
  data: Seq[Seq[JsValue]],
  prov: Seq[String],
  colTaint: Seq[Seq[Boolean]],
  rowTaint: Seq[Boolean],
  reasons: Seq[Seq[Caveat]],
  properties: Map[String,JsValue]
) 