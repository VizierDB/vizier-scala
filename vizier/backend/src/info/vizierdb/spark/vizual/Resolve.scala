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
package info.vizierdb.spark.vizual

import org.apache.spark.sql.{ Column, DataFrame } 
import org.apache.spark.sql.catalyst.plans.logical.Project

object Resolve
{
  /**
   * Resolve [[UnreferencedAttribute]], [[UnreferencedFunction]], etc... in the provided expression
   *
   * @param   expr   The expression to resolve
   * @param   df     The dataframe to resolve the expression in the context of
   * @returns        `expr` with all unresolved references replaced
   */
  def apply(expr: Column, df: DataFrame): Column = 
  {
    // Spark calls resolution 'analysis'.  Unfortunately, however, it does not expose this 
    // infrastructure to the outside world.  Worse, there's no generic "resolve in the context of
    // a dataframe" method.  The offical word (based on @mrb24 & @okennedy's discussions w/ 
    // DataBricks folks at CIDR 2020) is that the "best" way to do this is to create a projection,
    // analyze it, and then strip the resulting projection target off.

    // Create the fake projection
    df.select( expr )
      // Analysis functionality lives in this component
      .queryExecution
      // Resolve the tree
      .analyzed match {
        // And pull off the projection
        case Project(Seq(target), _) => new Column(target)

        // This should *never* happen, but let's be informative if the assumption is wrong
        case x => 
          throw new RuntimeException(
            s"Internal error: Expecting Spark's select() to produce a single output projection, but got: \n$x"
          )
      }
  }
}