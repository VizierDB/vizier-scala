/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
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