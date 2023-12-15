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
package info.vizierdb.util


import scala.collection.GenTraversableOnce

/**
 * Utilities for globally enabling or disabling experimental
 * configuration options.  Usually configuration should happen
 * either in a test case, or in the startup process of whatever
 * UI is being used (e.g., Mimir.scala for command line opts)
 *
 * Within the code, use either isEnabled, or ifEnabled to 
 * branch based on the specific experimental option.
 */
object ExperimentalOptions {

  var enabled:Set[String] = 
    System.getenv("MIMIR_EXPERIMENTS") match {
      case null => Set.empty
      case "" => Set.empty
      case x => x.split(",").toSet
    }

  /**
   * Enable one experimental option
   */
  def enable(option: String): Unit = 
    enabled = enabled + option
  /**
   * Disable one experimental option
   */
  def disable(option: String): Unit = 
    enabled = enabled - option

  /**
   * Enable a set of options
   */
  def enable(options: GenTraversableOnce[String]): Unit =
    { enabled = (enabled ++ options) }
  /**
   * Disable a set of options
   */
  def disable(options: GenTraversableOnce[String]): Unit =
    { enabled = (enabled -- options) }

  /**
   * For unit tests ONLY: Execute a block of code with options enabled
   */
  def withEnabled[A](options: GenTraversableOnce[String], cmd: (() => A)): A =
  {
    val optionStack = enabled
    enable(options)
    val ret = cmd()
    enabled = optionStack
    ret
  }

  /**
   * Test whether an option is enabled
   */
  def isEnabled(option: String): Boolean =
    enabled contains option

  /**
   * Execute code iff an option is enabled
   */
  def ifEnabled[A](opt: String, cmd: (() => A)): Option[A] =
    { if(enabled contains opt){ Some(cmd()) } else { None } }

  /**
   * Branch depending on whether an option is enabled
   */
  def ifEnabled[A](opt: String, thenCmd: (() => A), elseCmd: () => A): A =
    { if(enabled contains opt){ thenCmd() } else { elseCmd() }  }

}