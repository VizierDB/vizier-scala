package info.vizierdb.commands

import scalikejdbc._
import info.vizierdb.types._
import info.vizierdb.Vizier
import info.vizierdb.catalog.Artifact
import org.mimirdb.api.request.QueryTableRequest
// import java.sql.Connection
// import org.apache.commons.dbcp2.DelegatingConnection

class ExecutionContext(
  val scope: Map[String, Identifier]
)
{
  val inputs = scala.collection.mutable.Map[String, Identifier]()
  val outputs = scala.collection.mutable.Map[String, Artifact]()
  val messages = scala.collection.mutable.Buffer[(String, Array[Byte])]()
  var errorMessage: Option[String] = None

  def artifactExists(name: String): Boolean = {
    scope.contains(name.toLowerCase()) || outputs.contains(name.toLowerCase())
  }

  def artifact(name: String, registerInput: Boolean = true): Option[Artifact] = 
  {
    println(s"Retrieving $name")
    if(outputs contains name.toLowerCase()){
      return Some(outputs(name.toLowerCase()))
    }
    val ret = scope.get(name.toLowerCase()).map { id =>
      DB autoCommit { implicit session =>
        Artifact.get(id)(session)   
      }
    }
    if(registerInput){ ret.foreach { a => inputs.put(name.toLowerCase(), a.id) } }
    return ret
  }

  def output(name: String, t: ArtifactType.T, data: Array[Byte]): Artifact =
  {
    val artifact = DB autoCommit { implicit s => Artifact.make(t, data) }
    outputs.put(name.toLowerCase(), artifact)
    return artifact
  }

  def error(message: String)
  {
    errorMessage = Some(message)
  }

  def message(content: String)
  {
    message("text/plain", content.getBytes())
  }
  def message(mime: String, content: Array[Byte])
  {
    messages.append( (mime, content) )
  }
  def displayDataset(artifactId: Identifier) = 
  {
    message("datset/view", 
      QueryTableRequest(
        Artifact.nameInBackend(ArtifactType.DATASET, artifactId),
        columns = None,
        limit = Some(10),
        offset = None,
        includeUncertainty = true
      ).handle.toString.getBytes
    )
  }

  override def toString: String =
    {
      s"SCOPE: { ${scope.map { case (ds, id) => ds+" -> "+id }.mkString(", ")} }"
    }

  def isError = errorMessage.isDefined
}