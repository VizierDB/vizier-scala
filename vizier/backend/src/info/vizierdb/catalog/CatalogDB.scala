package info.vizierdb.catalog

import scalikejdbc._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.util.TimerUtils

/**
 * A simple instrumentation wrapper around ScalikeJDBC's session constructors. 
 * 
 * The short-term goal with this class is to track down performance bugs due to SQLite's global 
 * lock.  We want to warn when the lock is being held for too long.
 * 
 * The longer-term goal is maybe to allocate some sort of thread-local sessions, allowing 
 * for re-entrant access to SQLite.
 */
object CatalogDB
  extends LazyLogging
{
  val WARNING_CUTOFF = 1000*1000*1000 // ns  (1000 ms)

  def traceLongHolds[T](op : => T): T =
  {
    val (ret, time) = TimerUtils.time(op)
    if(time > WARNING_CUTOFF){
      try { throw new Throwable() }
      catch {
        case t:Throwable => 
          val trace = 
            t.getStackTrace()
             .drop(2)
             .map { _.toString }
             .mkString("\n")
          logger.warn(s"Database lock held for ${time/1000000000.0}s at:\n$trace")
      }
    } else {
      logger.trace(s"Database lock held for ${time/1000000000.0}s")
    }
    return ret 
  }


  def withDB[T]( op: DBSession => T ): T =
    traceLongHolds( DB.autoCommit { implicit s => op(s) })

  def withDBReadOnly[T]( op: DBSession => T ): T = 
    traceLongHolds( DB.readOnly { implicit s => op(s) })
}