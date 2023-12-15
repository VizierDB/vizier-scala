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

import scala.collection.mutable

class Graph[V]
{
  val vertices = mutable.Map[V, Vertex]()
  val edges = mutable.ListBuffer[Edge]()

  def apply(v: V): Vertex = 
    vertices(v)

  def insert(v: V): Vertex =
  {
    if(vertices contains v){ return vertices(v) } 
    else {
      val vtx = new Vertex(v)
      vertices.put(v, vtx)
      return vtx
    }
  }

  def connect(from: V, to: V): Edge =
    insert(from).connect(to)

  /**
   * Return the vertices in topological order.
   * 
   * If there is a loop in the vertex order, the return values
   * are guaranteed to be sorted for the vertices that do not
   * participate in the loop.  Looping vertices will be returned
   * in arbitrary order
   */
  def topological: Seq[Vertex] =
  {
    val visited = mutable.ArrayBuffer[Vertex]()
    val (candidates: mutable.Queue[Vertex], 
         unvisited: mutable.Map[Vertex, Int]) = 
      {
        val (candidates, unvisited) =
          vertices.values.map { v => v -> v.in.size }
                  .partition { _._2 == 0 }

        (
          mutable.Queue(candidates.map { _._1 }.toSeq:_*),
          mutable.Map(unvisited.toSeq:_*),
        )
      }

    while(!candidates.isEmpty)
    {
      val next = candidates.dequeue()
      visited.append(next)
      for(out <- next.out.keys)
      {
        if(unvisited contains out)
        {
          if(unvisited(out) <= 1)
          {
            unvisited.remove(out)
            candidates.enqueue(out)
          } else {
            unvisited(out) -= 1
          }
        }
      }
    }

    assert(unvisited.isEmpty)

    return visited.toSeq
  }

  class Vertex(val value: V)
  {
    val out = mutable.Map[Vertex, Edge]()
    val in  = mutable.Map[Vertex, Edge]()

    def connect(to: V): Edge =
      connect(insert(to))

    def connect(to: Vertex): Edge =
    {
      if(out contains to){ out(to) }
      else {
        val edge = new Edge(this, to)
        edges.append(edge)
        out.put(to, edge)
        to.in.put(this, edge)
        edge
      }
    }
  }  

  class Edge(val from: Vertex, val to: Vertex)
  {

  }
}