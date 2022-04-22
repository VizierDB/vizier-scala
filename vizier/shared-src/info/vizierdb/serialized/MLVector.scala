package info.vizierdb.serialized

case class MLVector(
  sparse: Boolean,
  size: Int,
  indices: Seq[Int],
  values: Seq[Double]
)