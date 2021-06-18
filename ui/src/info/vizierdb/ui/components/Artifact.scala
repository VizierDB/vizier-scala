package info.vizierdb.ui.components

import scala.scalajs.js
import info.vizierdb.ui.network.ArtifactSummary
import info.vizierdb.types.ArtifactType

class Artifact(
  val name: String, 
  val id: Option[String], 
  val t: Option[ArtifactType.T], 
  val mimeType: Option[String]
)
{

  def this(summary: ArtifactSummary)
  {
    this(
      summary.name, 
      (if(summary.id.equals(js.undefined)) { None } else { Some(summary.id.toString) }),
      (if(summary.id.equals(js.undefined)) { None } else { Some(ArtifactType.withName(summary.category)) }),  
      (if(summary.id.equals(js.undefined)) { None } else { Some(summary.objType) })
    )
  }

}