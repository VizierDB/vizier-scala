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
