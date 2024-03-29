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


trait TimerUtils 
{
  protected def logger: com.typesafe.scalalogging.Logger

  def time = TimerUtils.time _

  def logTime[F](
    descriptor: String, 
    context: String = "",
    log:(String => Unit) = logger.debug(_)
  )(anonFunc: => F): F = 
    TimerUtils.logTime(descriptor, context, log)(anonFunc)

}

object TimerUtils
{
  def time[F](anonFunc: => F): (F, Long) = 
  {  
    val tStart = System.nanoTime()
    val anonFuncRet:F = anonFunc  
    val tEnd = System.nanoTime()
    (anonFuncRet, tEnd-tStart)
  }  

  def logTime[F](
    descriptor: String, 
    context: String = "",
    log:(String => Unit) = println(_)
  )(anonFunc: => F): F = 
  {
    val (anonFuncRet, nanoTime) = time(anonFunc)
    val sep = if(context.equals("")){""} else {" "}
    log(s"$descriptor: ${nanoTime / 1000000000.0} s$sep$context")
    anonFuncRet
  }
}
