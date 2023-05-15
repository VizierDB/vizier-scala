package info.vizierdb.spreadsheet

/**
 * A simple tree for storing position offsets.
 * 
 * Track inserted and deleted rows by calling insert/delete respectively.
 * 
 * apply() returns the index of a given row in the reference frame before any 
 * of the above were invoked (or None if the row did not exist, i.e., was inserted)
 * 
 * TODO: add some sort of balancing (e.g., something AVL-ish should be viable)
 */
class OffsetMap
{
  var root: Option[Node] = None

  def insert(position: Long, count: Int): Unit =
  {
    root = root.map { _.insert(position, count) }
               .orElse { makeNode(position, count) }
  }

  def delete(position: Long, count: Int): Unit =
  {
    root = root.map { _.delete(position, count) }
               .orElse { makeNode(position, -count) }
  }

  def apply(position: Long): Option[Long] =
  {
    root.map { _.apply(position) }
        .getOrElse { Some(position) }
  }

  private def makeNode(position: Long, count: Int): Option[Node] =
      Some(new Node(0, count, position, None, None))

  def debug()
  {
    root match {
      case None => println("[EMPTY]")
      case Some(r) => r.debug("")
    }
  }

  /**
   * @param leftOffset   Total of all offsets from the prior ref frame to the 
   *                     current ref frame (subtract from position to get to)
   *                     the prior ref frame.
   * @param position     Position in the original reference frame
   * @param balance      AVL Tree Balance Factor
   * @param left         The left subtree
   * @param right        The right subtree
   */
  class Node(
    var leftOffset: Long, 
    var thisOffset: Int,
    var position: Long, 
    var left: Option[Node],
    var right: Option[Node]
  )
  {
    def myPosition = leftOffset + position
    def totalOffset = leftOffset + thisOffset
    def rightPosition = totalOffset + position

    def insert(insertPosition: Long, count: Int): Node =
    {
      if(insertPosition < myPosition)
      {
        // case 1: the insertion goes to the left
        left = left.map { _.insert(insertPosition, count) }
                   .orElse { makeNode(insertPosition, count) }
        leftOffset += count
      } else if(insertPosition < rightPosition) {
        // case 2: the insertion extends this one
        thisOffset += count
      } else {
        // case 2: the insertion goes to the right
        right = right.map { _.insert(insertPosition - totalOffset, count) }
                     .orElse { makeNode(insertPosition - totalOffset, count) }

      }
      return this
    }

    def delete(deletePosition: Long, count: Int): Node =
    {

      if(deletePosition + count > rightPosition)
      {
        // case 3: some of the deletion goes to the right
        val rightCount = Math.min(count, deletePosition + count - rightPosition).toInt

        right = right.map { _.delete(deletePosition - totalOffset, rightCount) }
                     .orElse { makeNode(deletePosition - totalOffset, -rightCount) }
      }

      if(deletePosition < rightPosition && deletePosition+count > myPosition)
      {
        // case 2: some of the deletion goes to me
        val myCount = Math.min(count, deletePosition + count - myPosition).toInt

        thisOffset -= myCount
      }

      if(deletePosition < myPosition)
      {
        // case 1: some of the deletion goes left
        val leftCount = Math.min(count, myPosition - deletePosition).toInt

        left = left.map { _.delete(deletePosition, leftCount) }
                   .orElse { makeNode(deletePosition, -leftCount) }
        leftOffset -= leftCount
      }

      return this
    }

    def apply(x: Long): Option[Long] =
    {
      if(x < myPosition){ 
        return left.map { _.apply(x) }.getOrElse { Some(x) } 
      }
      if(x >= rightPosition){ 
        return right.map { _.apply(x - totalOffset) }.getOrElse { Some(x - totalOffset) } 
      }
      None
    }

    def debug(indent: String): Unit = 
    {
      println(f"${indent.dropRight(1)}`--[$myPosition - $rightPosition] ($leftOffset; $thisOffset)")
      left match {
        case Some(l)  => l.debug(indent + " |" ) 
        case None => println(f"${indent} `-[original data]")
      }
      right match {
        case Some(r)  => r.debug(indent + "  " ) 
        case None => println(f"${indent} `-[original data]")
      }
    }
  }
}