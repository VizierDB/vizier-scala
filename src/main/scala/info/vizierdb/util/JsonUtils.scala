package info.vizierdb.util

import play.api.libs.json._

object JsonUtils
{

  def prettyJsPath(path: JsPath): String =
  {
    path.path.foldLeft("$") { 
      case (accum, IdxPathNode(idx)) => 
        accum+"["+idx+"]"
      case (accum, KeyPathNode(key)) => 
        accum+"."+key
      case (accum, recur:RecursiveSearch) => 
        accum+"."+recur.key+"*"
    }
  }

  def prettyJsonParseError(exc: JsResultException): Seq[String] =
  {
    exc.errors.flatMap { 
      case (path, errors) => 
        val prettyPath = s"At ${prettyJsPath(path)}: "
        errors.flatMap { err => 
          err.messages.map {
            prettyPath + _
          }
        }
    }
  }
}