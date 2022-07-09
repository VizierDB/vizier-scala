package info.vizierdb.shared

object implicits
{
  implicit def LiftToOption[T](x: T): Option[T] = Some(x)
}