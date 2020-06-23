package info.vizierdb.commands

import play.api.libs.json.{ JsValue, Reads }

class Arguments(values: Map[String, (JsValue, Parameter)])
{
  def get[T](arg: String)(implicit read:Reads[T]): T = values(arg)._1.as[T]
  def getList(arg: String): Seq[Arguments] =
    values(arg) match { 
      case (j, ListParameter(_, _, components, _, _)) => 
        j.as[Seq[Map[String, JsValue]]].map { Arguments(_, components) }
      case _ => throw new RuntimeException(s"$arg is not a list")
    }

  def pretty(arg:String) = 
  {
    val (v, t) = values(arg)
    t.stringify(v)
  }
  def validate: Seq[String] =
    values.values.flatMap { case (value, param) => param.validate(value) }.toSeq
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