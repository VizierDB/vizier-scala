package info.vizierdb.viztrails


sealed trait PredictedStateChange

/**
 * Recomputation status unknown.  → WAITING
 */
case object MayNeedRecomputation extends PredictedStateChange

/**
 * Recomputation required, but not possible yet.  → STALE (or → RUNNNING if head)
 */
case object WillNeedRecomputation extends PredictedStateChange

/**
 * Recomputation required and possible.  → RUNNING
 */
case object SafeToRecomputeNow extends PredictedStateChange

/**
 * Recomputation not required.  → DONE
 */
case object RecomputationUnnecessary extends PredictedStateChange