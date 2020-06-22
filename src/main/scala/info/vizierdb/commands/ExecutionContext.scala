package info.vizierdb.commands

import info.vizierdb.Types
import info.vizierdb.viztrails.Viztrails
import info.vizierdb.viztrails.Artifact

class ExecutionContext(
  val scope: Map[String, Types.Identifier]
)
{
  val inputs = scala.collection.mutable.Map[String, Types.Identifier]()
  val outputs = scala.collection.mutable.Map[String, Artifact]()
  val logEntries = scala.collection.mutable.Buffer[(Array[Byte], String)]()
  var errorMessage: Option[String] = None

  def artifact(name: String, registerInput: Boolean = true): Option[Artifact] = 
  {
    val ret = scope.get(name).flatMap { Artifact.get(_) } 
    if(registerInput){ ret.foreach { a => inputs.put(name, a.id) } }
    ret
  }

  def output(name: String, artifact: Artifact)
  {
    outputs.put(name, artifact)
  }

  def logEntry(content: String, mime: String = "text/plain")
  {
    logEntry(content.getBytes(), mime)
  }
  def logEntry(content: Array[Byte], mime: String)
  {
    logEntries.append( (content, mime) )
  }

  override def toString: String =
    {
      s"SCOPE: { ${scope.map { case (ds, id) => ds+" -> "+id }.mkString(", ")} }"
    }
}