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
package info.vizierdb

object types 
{
  type Identifier = String

    object ExecutionState extends Enumeration
  {
    type T = Value

    val DONE      = Value(1, "DONE")     /* The referenced execution is correct and up-to-date */
    val ERROR     = Value(2, "ERROR")    /* The cell or a cell preceding it is affected by a notebook 
                                            error */
    val WAITING   = Value(3, "WAITING")  /* The referenced execution follows a stale cell and *may* be 
                                            out-of-date, depending on the outcome of the stale cell  */
    val BLOCKED   = Value(4, "BLOCKED")  /* The referenced execution is incorrect for this workflow and 
                                            needs to be recomputed, but is blocked on another one */
    val STALE     = Value(5, "STALE")    /* The referenced execution is incorrect for this workflow and 
                                            needs to be recomputed */
    val CANCELLED = Value(6, "CANCELLED")/* Execution of the cell or a cell preceding it was 
                                            cancelled (equivalent to ERROR) */
    val FROZEN    = Value(7, "FROZEN")   /* The cell has temporarily been removed from the workflow */
    val RUNNING   = Value(8, "RUNNING")  /* The referenced execution is incorrect for this workflow and 
                                            is currently being recomputed */

    def translateToClassicVizier(state: T): Int = 
    {
      state match {
        case DONE => 4      // MODULE_SUCCESS
        case ERROR => 3     // MODULE_ERROR
        case WAITING => 0   // MODULE_PENDING
        case BLOCKED => 0   // MODULE_PENDING
        case STALE => 1     // MODULE_RUNNING
        case RUNNING => 1   // MODULE_RUNNING
        case CANCELLED => 2 // MODULE_CANCELLED
        case FROZEN => 5    // MODULE_SUCCESS
      }
    }

    def isRunningOrPendingState(state: T) = 
    {
      state match { 
        case DONE => false
        case ERROR => false
        case WAITING => true
        case BLOCKED => true
        case STALE => true
        case RUNNING => true
        case CANCELLED => false
        case FROZEN => false
      }
    }

  }

  object StreamType extends Enumeration
  {
    type T = Value

    val STDOUT = Value(1, "stdout")
    val STDERR = Value(2, "stderr")
  }
}

