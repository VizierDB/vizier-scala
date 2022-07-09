package info.vizierdb.serialized

case class MLVector(
  sparse: Boolean,
  size: Int,
  indices: Seq[Int],
  values: Seq[Double]
)
{
  def length = if(sparse){ size } else { values.size }

  def show(len: Int) =
  {
    s"[${iterator.take(len).mkString(", ")}${if(length > len){ s", ... ${length-len} more" } else { "" }} ]"
  }

  def vectorLength = length

  def iterator = 
    if(sparse){  
      new Iterator[Double]{
        val nextShownIdx = indices.iterator.buffered
        val valueIter = values.iterator
        var idx = 0
        def hasNext: Boolean = idx < vectorLength
        def next: Double =
        {
          if(!nextShownIdx.hasNext) { return 0.0 }
          else {
            if(idx == nextShownIdx.head){ 
              nextShownIdx.next
              idx += 1
              return valueIter.next
            } else {
              idx += 1
              return 0.0
            }
          }
        }
      }
    } else { values.iterator }
}