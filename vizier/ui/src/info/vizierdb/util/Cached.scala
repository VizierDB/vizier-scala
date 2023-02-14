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