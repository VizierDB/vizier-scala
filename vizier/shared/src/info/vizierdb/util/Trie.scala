package info.vizierdb.util

import scala.collection.mutable

class Trie[T]
{
  val children = mutable.Map[Char, Trie[T]]()
  val elements = mutable.ArrayBuffer[T]()

  def add(key: String, value: T): Unit =
  {
    if(key.isEmpty()) { elements += value; return }

    children.getOrElseUpdate(key.head, { new Trie[T]() })
            .add(key.tail, value)
  }

  def prefixMatch(prefix: String): Set[T] =
  {
    if(prefix.isEmpty()){ return all }
    else { 
      children.get(prefix.head)
              .map { _.prefixMatch(prefix.tail) }
              .getOrElse { Set.empty }
    }
  }

  def all: Set[T] =
    (children.values.flatMap { _.all } ++ elements).toSet
}

object Trie
{
  def apply[T](): Trie[T] = new Trie[T]
  def apply[T](elems: (String, T)*): Trie[T] = ofSeq(elems)
  def ofSeq[T](elems: Seq[(String, T)]): Trie[T] = 
  {
    val t = new Trie[T]
    for((key, value) <- elems) { t.add(key, value) }
    return t
  }
}