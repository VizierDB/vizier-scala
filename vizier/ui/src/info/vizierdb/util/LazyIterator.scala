package info.vizierdb.util

class LazyIterator[T](var curr: Option[T], advance: T => Option[T])
	extends Iterator[T]
{

	def hasNext: Boolean = curr.isDefined
	def next(): T = 
	{
		val ret = curr.get
		curr = advance(ret)
		return ret
	}
}