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
package info.vizierdb.util

import org.scalajs.dom

/**
 * A simple wrapper around an object cache.  The cache will be reloaded 
 * after a specified timeout or after manual invalidation.
 */
class Cached[T](
  load: () => T,
  timeoutMsec: Double = 1000*60*10.0 // 10 minutes
)
{
  var cache: Option[T] = None
  var timeout: Int = -1

  /**
   * Invalidate the cache, forcing a reload on the next call
   */
  def invalidate(): Unit =
  {
    cache = None
    if(timeout >= 0){ dom.window.clearTimeout(timeout) }
    timeout = -1
  }

  /**
   * Retrieve the cached item
   * @return        The cached item, re-loaded if necessary
   */
  def get: T =
  {
    if(cache.isEmpty){ 
      cache = Some(load())
      timeout = dom.window.setTimeout( () => invalidate(), timeoutMsec )
    }
    return cache.get
  }
}