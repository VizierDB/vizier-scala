package info.vizierdb.util

object ClassLoaderUtils
{
  def withContextClassloader[T](classloader: ClassLoader)(f: => T): T =
  {
    val originalClassloader = Thread.currentThread().getContextClassLoader()
    Thread.currentThread().setContextClassLoader(classloader)
    try {
      f
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassloader)
    }
  }
}