package info.vizierdb.commands

import play.api.libs.json.{ JsValue, Reads }
import play.api.libs.json.JsNull

class Arguments(values: Map[String, (JsValue, Parameter)])
{
  def contains(arg: String): Boolean = 
    values.get(arg) match { 
      case None => false 
      case Some((JsNull, _)) => false
      case _ => true
    }
  def get[T](arg: String)(implicit read:Reads[T]): T = values(arg)._1.as[T]
  def getOpt[T](arg: String)(implicit read:Reads[T]): Option[T] = 
    values(arg)._1 match {
      case JsNull => None
      case other => Some(other.as[T])
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
}