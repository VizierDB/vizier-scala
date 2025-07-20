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
package info.vizierdb.commands

import play.api.libs.json._
import info.vizierdb.VizierException

class Arguments(values: Map[String, (JsValue, Parameter)])
{
  def contains(arg: String): Boolean = 
    values.get(arg) match { 
      case None => false 
      case Some((JsNull, _)) => false
      case _ => true
    }
  def get[T](arg: String)(implicit read:Reads[T]): T = 
    getOpt[T](arg).get
  def getOpt[T](arg: String)(implicit read:Reads[T]): Option[T] = 
    try { 
      val (argument, parameter) = values(arg)
      argument match {
        // hack to work around broken frontend
        case JsString("") if parameter.isInstanceOf[ColIdParameter] =>
          if(parameter.required) {
            throw new VizierException(s"Undefined required parameter ${parameter.name}")
          } else { None }
        case JsNull if parameter.required => 
          throw new VizierException(s"Undefined required parameter ${parameter.name}")
        case JsNull => None
        case other => Some(other.as[T])
      }
    } catch {
      case _:JsResultException => 
        throw new VizierException(s"Error parsing $arg ('${values(arg)._1}')")
    }
  def getList(arg: String): Seq[Arguments] =
    values(arg) match { 
      case (j, ListParameter(_, _, components, _, _)) => 
        j.as[Seq[Map[String, JsValue]]].map { Arguments(_, components) }
      case _ => throw new RuntimeException(s"$arg is not a list")
    }
  def getRecord(arg: String): Arguments =
    values(arg) match {
      case (j, RecordParameter(_, _, components, _, _)) =>
        Arguments(j.as[Map[String, JsValue]], components)
      case _ => throw new RuntimeException(s"$arg is not a record")
    }

  def pretty(arg:String) = 
  {
    val (v, t) = values(arg)
    t.stringify(v)
  }
  def validate: Seq[String] =
    values.values.flatMap { case (value, param) => param.validate(value) }.toSeq
  def yaml(indent: String = "", firstIndent: Option[String] = None): String =
    values.map { 
      case (arg, (_, _:ListParameter)) => {
        val list = getList(arg)

        s"${firstIndent.getOrElse(indent)}$arg:"+
          (if(list.size > 0) {
            "\n"+list.map { _.yaml(s"$indent    ", Some(s"$indent  - ")) }.mkString("\n")
          } else { "" })
      }
      case (arg, (value, param)) => 
        s"${firstIndent.getOrElse(indent)}$arg: ${param.stringify(value)}"
    }.mkString(s"\n")
    for((k, v) <- values) {

    }

  def asJson: JsObject =
    JsObject(values.mapValues { case (v, param) => v })

  override def toString = 
    values.map { x => s"${x._1}: ${x._2._2.stringify(x._2._1)}" }.mkString(", ")
}
object Arguments
{
  def apply(values: Map[String, JsValue], parameters: Seq[Parameter]): Arguments =
  {
    new Arguments(
      parameters.map { param =>
        ( param.id, ( values.get(param.id)
                            .getOrElse { param.getDefault },
                      param ))
      }.toMap
    )
  }
  def apply(values: JsObject, parameters: Seq[Parameter]): Arguments =
    apply(values.as[Map[String, JsValue]], parameters)
}

