package info.vizierdb.commands

import scalikejdbc._
import info.vizierdb.types._
import info.vizierdb.Vizier
import info.vizierdb.catalog.Artifact
import org.mimirdb.api.request.QueryTableRequest
import info.vizierdb.VizierException
import info.vizierdb.catalog.binders._

class ExecutionContext(
  val projectId: Identifier,
  val scope: Map[String, Identifier]
)
{
  val inputs = scala.collection.mutable.Map[String, Identifier]()
  val outputs = scala.collection.mutable.Map[String, Option[Artifact]]()
  val messages = scala.collection.mutable.Buffer[(String, Array[Byte])]()
  var errorMessage: Option[String] = None

  /**
   * Check to see if the specified artifact appears in the scope
   */
  def artifactExists(name: String): Boolean = {
    scope.contains(name.toLowerCase()) || outputs.contains(name.toLowerCase())
  }

  /**
   * Retrieve the specified artifact
   *
   * @param   name            The user-facing name of the artifact
   * @returns                 The Artifact object assoicated with this name
   */
  def artifact(name: String, registerInput: Boolean = true): Option[Artifact] = 
  {
    println(s"Retrieving $name")
    if(outputs contains name.toLowerCase()){
      val ret = outputs(name.toLowerCase())
      if(ret.isEmpty){ 
        throw new VizierException(s"$name was already deleted.")
      }
      return Some(ret.get)
    }
    val ret = scope.get(name.toLowerCase()).map { id =>
      DB autoCommit { implicit session =>
        Artifact.get(id)(session)   
      }
    }
    if(registerInput){ ret.foreach { a => inputs.put(name.toLowerCase(), a.id) } }
    return ret
  }

  /**
   * Retrieve the specified dataset
   *
   * @param   name            The user-facing name of the dataset (relative to the scope)
   * @returns                 The backend name corresponding to the specified dataset
   */
  def dataset(name: String, registerInput: Boolean = true): Option[DatasetIdentifier] = 
    artifact(name, registerInput)
      .map { a => if(a.t != ArtifactType.DATASET) { 
                    throw new VizierException(s"$name is not a dataset (it's actually a ${a.t})" )
                  } else { a.nameInBackend } }

  /**
   * Retrieve all datasets in scope
   */
  def allDatasets: Map[String, Artifact] =
  {
    val datasets = 
      DB.readOnly { implicit s => 
        val a = Artifact.syntax
        withSQL { 
          select
            .from(Artifact as a)
            .where.in(a.id, scope.values.toSeq)
              .and.eq(a.t, ArtifactType.DATASET)
        }.map { Artifact(_) }.list.apply()
      }.map { a => a.id -> a }.toMap
    scope.flatMap { case (userFacingName, artifactId) => 
                          datasets.get(artifactId).map { userFacingName -> _ } }
         .toMap
  }

  /**
   * Allocate and output an artifact
   *
   * @param   name            The user-facing name of the artifact
   * @param   t               The type of the artifact
   * @param   data            The content of the dataset
   * @returns                 The newly allocated Artifact object
   */
  def output(name: String, t: ArtifactType.T, data: Array[Byte], mimeType: String = "text/plain"): Artifact =
  { 
    val artifact = DB autoCommit { implicit s => Artifact.make(projectId, t, mimeType, data) }
    outputs.put(name.toLowerCase(), Some(artifact))
    return artifact
  }

  /**
   * Output an existing artifact (possibly under a new name)
   *
   * @param    name           The new name of the artifact
   * @param    artifact       The artifact to output
   */
  def output(name: String, artifact: Artifact) =
  {
    outputs.put(name.toLowerCase(), Some(artifact))
  }

  /**
   * Delete an artifact from the scope
   *
   * @param     name          The artifact to delete
   */
  def delete(name: String) =
  {
    outputs.put(name.toLowerCase(), None)
  }

  /**
   * Allocate a new dataset object and register it as an output
   * 
   * @param   name            The user-facing name of the dataset
   * @returns                 The newly allocated backend-facing name and its identifier
   */
  def outputDataset(name: String): (String, Identifier) =
    { val ds = output(name, ArtifactType.DATASET, Array[Byte](), "mimir/dataset"); (ds.nameInBackend, ds.id) }

  /**
   * Allocate a new dataset object and register it as an output
   * 
   * @param   name            The user-facing name of the dataset
   * @returns                 The newly allocated backend-facing name
   */
  def outputFile(name: String): String =
    output(name, ArtifactType.DATASET, Array[Byte]()).nameInBackend

  /**
   * Record that this execution failed with the specified name
   * 
   * @param   message         The error message to communicate to the user
   */
  def error(message: String)
  {
    errorMessage = Some(message)
  }

  /**
   * Communicate a message to the end-user.
   * 
   * @param   content         The text message to communicate to the user
   */
  def message(content: String)
  {
    message("text/plain", content.getBytes())
  }

  /**
   * Communicate a message to the end-user.
   *
   * @param   mimeType        The MIME-Type of the message content
   * @param   content         The text to communicate
   */
  def message(mime: String, content: String)
  {
    message(mime, content.getBytes())
  }

  /**
   * Communicate a message to the end-user.
   *
   * @param   mimeType        The MIME-Type of the message content
   * @param   content         The bytes of data to communicate
   */
  def message(mime: String, content: Array[Byte])
  {
    messages.append( (mime, content) )
  }

  /**
   * Communicate a dataset to the end user.
   * 
   * @param   artifactId      The artifact identifier of the dataset to display 
   */
  def displayDataset(artifactId: Identifier) = 
  {
    message("datset/view", 
      QueryTableRequest(
        table = Artifact.nameInBackend(ArtifactType.DATASET, artifactId),
        columns = None,
        limit = Some(10),
        offset = None,
        includeUncertainty = true,
        profile = Some(false)
      ).handle.toString.getBytes
    )
  }

  override def toString: String =
    {
      s"SCOPE: { ${scope.map { case (ds, id) => ds+" -> "+id }.mkString(", ")} }"
    }

  def isError = errorMessage.isDefined
}