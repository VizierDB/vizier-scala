package info.vizierdb.ui.widgets

import scala.scalajs.js
import rx._
import info.vizierdb.ui.components._
import info.vizierdb.serialized
import scala.collection.mutable.Queue


/*
 * This will contain the helper functions to create the data provenance chart
*/

object DatasetProvenance {
    
    /*
     * Use a modified BFS to generate the dependency chart. A few conventions:
     * we will store the artifacts as a 3-tuple, containing the name, depth, and height
     * The depth will represent the steps from the origin artifact, with the main difference
     * from BFS being that we are looking for the earliest ("furthest") appearance of the
     * artifact on the tree, since we don't want any "backward" pointing dependencies. 
     * 
     * It will return a map with keys being artifact names, and values being a tuple of the 
     * relative coordinates (depth, height), and a sequence of connecting artifacts
     */

    def modifiedBFS (
        origin: String,
        artifacts: Map[String, (serialized.ArtifactSummary, WorkflowElement)]
        ) : Map[String, (Int, Int, Seq[String])]= {

       val originLinks: Seq[String] = artifacts(origin)._2.inputs.now.keys.toSeq

        var returnMap: Map[String, (Int, Int, Seq[String])] = Map()
        //val emptyList: Seq[String] = Seq()
        returnMap += (origin -> (0,0,originLinks))
        var toVisit: Queue[String] = Queue(origin)

        var testMap: Map[String,String] = Map()

        var height: Int = 0
        var depth: Int = 0

        while (toVisit.nonEmpty) {
            val curr: String = toVisit.dequeue()
            println ("curr: " + curr)
            val currDepth = returnMap(curr)._1
            if (currDepth > depth) {
                depth = currDepth
                height = 0
            }

            for (art <- returnMap(curr)._3) {
                println("art: " + art)
                val links: Seq[String] = artifacts(art)._2.inputs.now.keys.toSeq
                val temp = returnMap.getOrElse(art, (0,0,Seq()))
                if (temp == (0,0,Seq())) {
                    returnMap += (art ->(currDepth+1, height, links))
                    testMap += (art -> art)
                    height += 1
                }
                else {
                    val currHeight = temp._2
                    returnMap += (art -> (currDepth+1, currHeight, links))
                }
                toVisit.enqueue(art)
            }
        }
        return returnMap
    }

    /*
     *  This will take two arguments - the name (of the origin artifact), and workflows
     */

    def createChart (
        origin: String, 
        artifacts: Map[String, (serialized.ArtifactSummary, WorkflowElement)]
        ) : js.Dictionary[Any] = {

        var vegaChart: js.Dictionary[Any] = js.Dictionary(
            "$schema" -> "https://vega.github.io/schema/vega-lite/v5.json",
            "description" -> "Data Provenance Chart",

            "height" -> 200,
            "width" -> 600,
            
            "mark" -> "bar",
            "layer" -> js.Array(
                js.Dictionary("mark" -> "point"),
                js.Dictionary(
                    "mark" -> js.Dictionary(
                        "type" -> "text",
                        "dx"-> -5,
                        "dy" -> -10,
                        "font" -> "sans-serif",
                        "fontSize" -> 14,
                        "limit" -> 20
                    ),
                    "encoding" -> js.Dictionary(
                        "text" -> js.Dictionary(
                            "field"-> "label"
                        )
                    )
                ),
                js.Dictionary(
                    "mark" -> js.Dictionary(
                        "type" -> "rule",
                        "color" -> "blue"
                    ),
                    "encoding" -> js.Dictionary(
                        "x" -> js.Dictionary("field" -> "x1"),
                        "y" -> js.Dictionary("field" -> "y1"),
                        "x2" -> js.Dictionary("field" -> "x2"),
                        "y2" -> js.Dictionary("field" -> "y2")
                    )
                )
            )
        )

        val chartMap: Map[String, (Int, Int, Seq[String])] = modifiedBFS(origin, artifacts)

        var maxHeight = 0
        var maxDepth = 0

        // Create the points and rules

        var values: js.Array[Any] = js.Array()

        for ((node, value) <- chartMap) {
            val x = value._1 * -1
            val y = value._2
            val links = value._3

            val point = js.Dictionary(
                "depth" -> x,
                "height" -> y,
                "label" -> node
            )

            maxDepth = math.min(maxDepth, x)
            maxHeight = math.max(maxHeight, y)

            values.append(point)

            for (link <- links) {
                val x2 = chartMap(link)._1 * -1
                val y2 = chartMap(link)._2

                val rule = js.Dictionary(
                    "x1" -> x,
                    "y1" -> y,
                    "x2" -> x2,
                    "y2" -> y2
                )
                values.append(rule)
            }
        }

        // make encodings

        val enc = js.Dictionary(
            "x" -> js.Dictionary(
                "field" -> "depth",
                "type" -> "quantitative",
                "scale" -> js.Dictionary(
                    "domain" -> js.Array(maxDepth - 2, 2)
                ),
                "axis" -> null
            ),
            "y" -> js.Dictionary(
                "field" -> "height",
                "type" -> "quantitative",
                "scale" -> js.Dictionary(
                    "domain" -> js.Array(-2, maxHeight + 2)
                ),
                "axis" -> null
            )
        )

        vegaChart("data") = js.Dictionary(
            "values" -> values
        )

        vegaChart("encoding") = enc
        
        return vegaChart

    }
  
}
