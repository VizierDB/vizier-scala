/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
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
package info.vizierdb.spreadsheet

import play.api.libs.json._

/**
 * A lookup table mapping [position in spreadsheet] to [position in source data]
 * 
 * Broadly, this is best thought of as a collection of (low, high) -> start triples:
 * * For spreadsheet positions in [low, high]...
 * * The corresponding source data may be found at (position - low) + start
 * 
 * RangeMap gives us efficient access to the triples, but we need a special 'last'
 * element (modeling the triple (max, ∞, lastStart)) to cope with the fact that RangeMap
 * is closed-world, while our mappings are open-world.  The variable `max` is the lower 
 * bound of this triple.  
 */
class SourceReferenceMap
{

  // Every position >= max is mapped to { lastStart + (position - max) }
  var max: Long = 0l
  var lastStart: Long = 0l

  // Every position < max is mapped according to the offsets in data
  //   position ∊ (low, high, start) is mapped to (start + (position - low))
  val data = new RangeMap[Long]()


  def insert(position: Long, count: Int): Unit =
  {
    // inject a new tail entry if we're inserting above max
    if(position > max)
    {
      data.insert(max, position-1, lastStart)
      lastStart += (position - max)
      max = position
    }
    // move max up past position 
    if(position == max)
    {
      lastStart += position-max
      max = position+count
    }
    // if we're inserting in the middle of an already defined element
    else if(position < max)
    {
      data.inject(position, count, 
        update = {
          (low, _, high, start) => 
            val offset = position - low
            (start, start+offset)
        }
      )
      max += count

    }
  }

  def delete(position: Long, count: Int): Unit =
  {
    if(position < max)
    {
      data.collapse(position, count)
      if(position + count > max) {
        lastStart += Math.min(count, (position + count) - max)
      }
      max -= Math.min(count, max - position)
    }
    else if(position > max)
    {
      data.insert(max, position-1, lastStart)
      lastStart += position - max + count
      max = position
    }
    else if(position + count > max) {
      lastStart += Math.min(count, (position + count) - max)
    }
  }

  def move(from: Long, to: Long, count: Int): Unit =
  {
    // step one: ensure that everything is defined in the map through the greater of from, to, or count
    val expectedMax = Math.max(from+count, to+count)
    if( expectedMax > max )
    {
      data.insert(max, expectedMax-1, lastStart)
      lastStart += (expectedMax - max)
      max = expectedMax
    }

    // Although RangeMap has a 'move' operation, we need to be able to 

    data.bisect(from, 
      update = {
        (low, position, high, start) => 
          val offset = position - low
          (start, start+offset)
      }
    )
    data.bisect(from+count, 
      update = {
        (low, position, high, start) => 
          val offset = position - low
          (start, start+offset)
      }
    )
    val movedRanges = 
      data.collapse(from, count)
    max -= count

    data.inject(to, count,
      update = {
        (low, position, high, start) => 
          val offset = position - low
          (start, start+offset)
      }
    )
    val offset = to - from
    for( (low, high, start) <- movedRanges)
    {
      data.insert(low+offset, high+offset, start)
    }
    max += count
  }

  def apply(position: Long): Option[Long] =
  {
    if(position >= max)
    {
      Some(position - max + lastStart)
    } else {
      data.get(position)
          .map { case (low, _, start) => 
            val offset = position - low
            // println(s"offset: ($position; $low) $offset")
            start + offset 
          }
    }
  }

  def debug()
  {
    println(toString())
  }

  def replace(other: SourceReferenceMap)
  {
    max = other.max
    lastStart = other.lastStart
    data.clear()
    for( (from, to, offset) <- other.data.iterator )
    {
      data.insert(from, to, offset)
    }
  }

  def iterator: Iterator[(Long, Long, Long)] =
    data.iterator ++ Seq( (max, Long.MaxValue, lastStart) ).iterator

  def invertedIterator: Iterator[(Long, Long)] = 
  {
    var last = 0l
    data.iterator.flatMap { case (from, to, _) =>
      val ret = if(last < from){ Some( (last, from-1) ) }
                else { None }
      last = to+1
      /* return */ ret
    }
  }

  override def toString: String = 
    (data.iterator.map { case (from, to, start) =>
           s"  [$from, $to] -> [$start, ${start + (to - from)}]"
      }.toSeq :+ s"  [$max, ∞] -> [$lastStart, ...]"
    ).mkString("\n")
}

object SourceReferenceMap
{
  implicit val format = Format[SourceReferenceMap](
    new Reads[SourceReferenceMap] {
      def reads(json: JsValue): JsResult[SourceReferenceMap] = 
      {
        val ret = new SourceReferenceMap()
        ret.max = (json \ "max").as[Long]
        ret.lastStart = (json \ "lastStart").as[Long]
        for(offset <- (json \ "offsets").as[Seq[JsValue]])
        {
          ret.data.insert(
            (offset \ "from").as[Long],
            (offset \ "to").as[Long],
            (offset \ "offset").as[Long],
          )
        }
        return JsSuccess(ret)
      }
    },
    new Writes[SourceReferenceMap] {
      def writes(o: SourceReferenceMap): JsValue = 
        Json.obj(
          "max" -> o.max,
          "lastStart" -> o.lastStart,
          "offsets" -> o.data.iterator.map { case (from, to, offset) =>
            Json.obj(
              "from" -> from,
              "to" -> to,
              "offset" -> offset
            )
          }.toSeq
        )
    }
  )
}