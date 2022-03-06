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

import collection.mutable.Buffer
import collection.mutable.HashMap


class GenericSvgAttributes
{
  val stroke: String = null

}

trait SvgComponent[C <: SvgComponent[C]]
{
  def tag: String
  def attributes: Map[String, String]
  val children = Buffer[SvgComponent[_]]()
  val bodyText: String = null
  
  var stroke: String = null
  def stroke(v: String): C = { stroke = v; this.asInstanceOf[C] }
  var strokeWidth: Integer = null
  def strokeWidth(v: Int): C = { strokeWidth = v; this.asInstanceOf[C] }
  var fill: String = null
  def fill(v: String): C = { fill = v; this.asInstanceOf[C] }

  def baseAttributes = Seq[(String, Any)](
    "stroke" -> stroke,
    "stroke-width" -> strokeWidth,
    "fill" -> fill
  ).filter { _._2 != null }.toMap.mapValues { _.toString }

  def render(indent: String = ""): String = 
  {
    val c = children
    val a = (baseAttributes ++ attributes)
              .map { case (k,v) => 
                k+"=\'"+(v.replaceAll("\\\\", "\\\\")
                          .replaceAll("'", "\\'"))+"'"
              }
              .toSeq
    val openTagBody = (tag +: a).mkString(" ")
    if(bodyText != null) {
      s"$indent<$openTagBody>$bodyText</$tag>"
    } else if(c.isEmpty){
      s"$indent<$openTagBody />"
    } else {
      (
        Seq(s"$indent<$openTagBody>") ++
        children.map { _.render(indent + "  ") } ++
        Seq(s"$indent</$tag>")
      ).mkString("\n")
    }
  }
  def add[N <: SvgComponent[N]](x: N): C =
    { children.append(x); this.asInstanceOf[C] }
  def addAndEdit[N <: SvgComponent[N]](x: N)(op: N => Unit): C =
    { children.append(x); op(x); this.asInstanceOf[C] }
}

abstract class AbstractSvgComponent[C <: SvgComponent[C]]
  extends SvgComponent[C]
{
  val tag: String
  def attributes = attributeMap.toMap
  val attributeMap = HashMap[String,String]()
}

case class LeafSvgComponent(
  val tag: String, 
  val attributes: Map[String,String],
  override val bodyText: String = null
) extends SvgComponent[LeafSvgComponent]

trait SvgConstructors[C <: SvgComponent[C]]
{
  def add[N <: SvgComponent[N]](x: N): C
  def addAndEdit[N <: SvgComponent[N]](x: N)(op: N => Unit): C

  def leaf(tag: String, bodyOverride: String = null)(attrs: (String,Any)*) = 
    add(LeafSvgComponent(
      tag, 
      attrs.filter { _._2 != null }.toMap.mapValues { _.toString },
      bodyOverride
    ))

  def g = addAndEdit(new SvgG)(_)
  def path = addAndEdit(new SvgPath)(_)
  def circle(cx: Int, cy: Int, r: Int) = 
    leaf("circle")(
      "cx" -> cx,
      "cy" -> cy,
      "r" -> r
    )
  def line(segment: ((Int,Int),(Int,Int)), stroke: String = null, strokeWidth: Integer = null) =
    leaf("line")(
      "x1" -> segment._1._1,
      "y1" -> segment._1._2,
      "x2" -> segment._2._1,
      "y2" -> segment._2._2,
      "stroke" -> stroke,
      "strokeWidth" -> strokeWidth
    )
  def rect(x: Int, y: Int, w: Int, h: Int, rx: Integer = null) =
    leaf("rect")(
      "x" -> x,
      "y" -> y,
      "width" -> w,
      "height" -> h,
      "rx" -> rx
    )
  def text(x: Int, y: Int, text: String, className: String = null) = 
    leaf("text", text)(
      "x" -> x,
      "y" -> y,
      "class" -> className
    )
}

class SvgG 
  extends AbstractSvgComponent[SvgG]
  with SvgConstructors[SvgG]
{
  val tag = "g"

  def transform(op: String) = 
    attributeMap.put("transform", 
      attributeMap.get("transform")
                  .map { _ + " " + op }
                  .getOrElse { op }
    )

  def translate(x: Int, y: Int) = 
    transform(s"translate($x $y)")

  def scale(x: Int) = 
    transform(s"scale($x)")

  def scale(x: Int, y: Int) = 
    transform(s"scale($x $y)")

  def rotate(a: Int) = 
    transform(s"rotate($a)")

  def rotate(a: Int, x: Int, y: Int) = 
    transform(s"rotate($a $x $y)")
  
  def skewX(a: Int) = 
    transform(s"skewX($a)")

  def skewY(a: Int) = 
    transform(s"skewY($a)")
}

class SvgDefs extends AbstractSvgComponent[SvgDefs]
{
  val tag = "defs"

  def marker(id: String, w: Int, h: Int, refX: Int = 0, refY: Int = 0)
            (op: Marker => SvgDefs) = 
    add(new Marker(id, w, h, refX, refY)) 
  
  class Marker(id: String, w: Int, h: Int, refX: Int, refY: Int) 
    extends SvgComponent[Marker]
    with SvgConstructors[Marker]
  {
    val tag = "marker"
    val attributes = Map(
      "id" -> id,
      "markerWidth" -> w.toString,
      "markerHeight" -> h.toString,
      "refX" -> refX.toString,
      "refY" -> refY.toString
    )
  }

}

class Svg(var width: Int, var height: Int) 
  extends SvgComponent[Svg]
  with SvgConstructors[Svg]
{
  val tag = "svg"
  def attributes = Map[String,String](
    "viewbox" -> s"0 0 $width $height",
    "xmlns"   -> "http://www.w3.org/2000/svg",
    "height"  -> height.toString,
    "width"   -> width.toString
  )
  lazy val defs: SvgDefs = {
    val v = new SvgDefs()
    children.append(v)
    v
  }
}


class SvgPath extends SvgComponent[SvgPath]
{
  val tag = "path"
  val start: String = null
  val end: String = null

  def attributes: Map[String,String] = 
    Map("d" -> steps.mkString(" "))

  val steps = Buffer[String]()

  def step(instruction: String): SvgPath =
    { steps.append(instruction); this }

  def move(x: Int, y: Int) =
    step(s"M $x $y")

  def moveOffset(dx: Int, dy: Int) =
    step(s"m $dx $dy")

  def line(x: Int, y: Int) =
    step(s"L $x $y")

  def lineOffset(dx: Int, dy: Int) =
    step(s"l $dx $dy")

  def lineX(x: Int) =
    step(s"H $x")

  def lineXOffset(dx: Int) =
    step(s"h $dx")

  def lineY(y: Int) =
    step(s"V $y")

  def lineYOffset(dy: Int) =
    step(s"v $dy")

  def cubic(controlX1: Int, controlY1: Int)
           (controlX2: Int, controlY2: Int)
           (x: Int, y: Int) =
    step(s"C $controlX1 $controlY1 $controlX2 $controlY2 $x $y")

  def cubicOffset(dControlX1: Int, dControlY1: Int)
                 (dControlX2: Int, dControlY2: Int)
                 (dx: Int, dy: Int) =
    step(s"c $dControlX1 $dControlY1 $dControlX2 $dControlY2 $dx $dy")

  def quadratic(controlX: Int, controlY: Int)
               (x: Int, y: Int) =
    step(s"Q $controlX $controlY $x $y")

  def quadraticOffset(dControlX: Int, dControlY: Int)
                     (dx: Int, dy: Int) =
    step(s"q $dControlX $dControlY $dx $dy")

  def close() =
    step("Z")
}

