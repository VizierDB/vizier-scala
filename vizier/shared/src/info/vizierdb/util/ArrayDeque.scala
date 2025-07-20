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
import scala.reflect.ClassTag

class ArrayDeque[T](initialCapacity: Int = 100)(implicit tag: ClassTag[T])
  extends mutable.Seq[T]
{
  var firstPosition = 0
  var lastPosition = 0
  var buffer = new Array[T](initialCapacity)

  def iterator = 
    if(firstPosition <= lastPosition){
      buffer.view.slice(firstPosition, lastPosition).iterator
    } else {
      buffer.view.drop(firstPosition).iterator ++ 
        buffer.view.take(lastPosition).iterator
    }

  def grow() = 
  {
    val tmp = new Array[T](buffer.size * 2)
    for((elem, idx) <- iterator.zipWithIndex){
      tmp(idx) = elem
    }
    lastPosition = size
    firstPosition = 0
    buffer = tmp
  }

  def length: Int =
    if(firstPosition <= lastPosition) { lastPosition - firstPosition }
    else { buffer.size - firstPosition + lastPosition }

  def growIfNeeded() =
    if(size >= buffer.size - 2){ grow() }

  def prepend(elem: T) =
  {
    growIfNeeded()
    firstPosition -= 1
    if(firstPosition < 0) { firstPosition += buffer.size }
    buffer(firstPosition) = elem
  }

  def removeFirst: T =
  {
    if(firstPosition == lastPosition) { throw new IndexOutOfBoundsException() }
    val ret = buffer(firstPosition)
    firstPosition += 1
    if(firstPosition >= buffer.size) { firstPosition -= buffer.size }
    return ret
  }

  def append(elem: T) =
  {
    growIfNeeded()
    buffer(lastPosition) = elem
    lastPosition += 1
    if(lastPosition >= buffer.size) { lastPosition -= buffer.size }
  }

  def removeLast: T =
  {
    if(firstPosition == lastPosition) { throw new IndexOutOfBoundsException() }
    lastPosition -= 1
    if(lastPosition < 0) { lastPosition += buffer.size }
    return buffer(lastPosition)
  }

  def indexToBuffer(idx: Int): Int = 
  {
    if(idx < 0 || idx >= size){ throw new IndexOutOfBoundsException() }
    if(idx + firstPosition < buffer.size) { return idx + firstPosition }
    else { return idx + firstPosition - buffer.size }
  }

  def update(idx: Int, elem: T) =
  {
    buffer(indexToBuffer(idx)) = elem
  }

  def apply(idx: Int): T = 
  {
    buffer(indexToBuffer(idx))
  }

}