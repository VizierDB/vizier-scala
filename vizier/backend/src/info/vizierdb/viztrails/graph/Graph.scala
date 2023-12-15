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
package info.vizierdb.viztrails.graph

import play.api.libs.json._
import scala.collection.mutable.HashMap

class Graph
{
  val nodes = HashMap[Graph.NodeId, Graph.Node]()
  val edges = HashMap[(Graph.NodeId,Graph.NodeId), Graph.Edge]()

  def node(id: Graph.NodeId): Graph.Node = 
    nodes.getOrElseUpdate(id, { new Graph.Node(this, id) })

  def edge(source: Graph.NodeId, target: Graph.NodeId): Graph.Edge = 
    edges.getOrElseUpdate((source, target), { new Graph.Edge(this, source, target) })
}

object Graph
{
  type NodeId = String

  class Node(graph: Graph, val id: String)
  {
    var label = id
    var meta = HashMap[String, String]()

    def setLabel(v: String): Node = { label = v; this }
    def setMeta(k: String, v: String): Node = { meta.put(k, v); this }

  }

  class Edge(graph: Graph, val source: Graph.NodeId, val target: Graph.NodeId)
  {
    var label = s"$source -> $target"
    var directed = true

    def setLabel(v: String): Edge = { label = v; this }
    def makeDirected: Edge = { directed = true; this }
    def makeUndirected: Edge = { directed = false; this }
  }

  implicit def nodeWrites = new Writes[Node]{
    def writes(o: Node) =
      JsObject(
        Map(
          "id" -> JsString(o.id),
          "label" -> JsString(o.label),
        ) ++ o.meta.mapValues { JsString(_) }
      )
  }
  implicit def edgeWrites = new Writes[Edge]{
    def writes(o: Edge) =
      JsObject(
        Map(
          "source" -> JsString(o.source),
          "target" -> JsString(o.target),
          "label" -> JsString(o.label),
          "directed" -> JsBoolean(o.directed)
        )
      )
  }
  implicit def writes = new Writes[Graph]{
    def writes(o: Graph) = 
      Json.obj(
        "nodes" -> o.nodes.values.toSeq,
        "edges" -> o.edges.values.toSeq
      )
  }
}

