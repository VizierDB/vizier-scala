package info.vizierdb.commands.mimir

import play.api.libs.json._
import info.vizierdb.commands._
import org.apache.spark.sql.types.StructField
import org.mimirdb.lenses.Lenses
import org.mimirdb.lenses.implementation.{ CommentLensConfig, CommentParams }

object Comment
  extends LensCommand
{ 
  def lens = Lenses.comment
  def name: String = "Comment"
  def lensParameters: Seq[Parameter] = Seq(
    ListParameter(id = "comments", name = "Comments", components = Seq(
      ColIdParameter(id = "expression", name = "Column"),
      RowIdParameter(id = "rowid", name = "Row"),
      StringParameter(id = "comment", name = "Comment")
    ))
  )
  def lensFormat(arguments: Arguments): String = 
    s"ADD COMMENTS"

  def lensConfig(arguments: Arguments, schema: Seq[StructField], dataset: String, context: ExecutionContext): JsValue =
  {
    Json.toJson(
      CommentLensConfig(
        comments = 
          arguments.getList("comments")
                   .map { comment => 
                      CommentParams(
                        comment   = comment.get[String]("comment"),
                        target    = Some(schema(comment.get[Int]("expression")).name),
                        rows      = Some(Seq(comment.get[String]("rowid").toInt)),
                        condition = None
                      )
                   }
      )
    )
  }
  def updateConfig(lensArgs: JsValue, schema: Seq[StructField], dataset: String): Map[String,JsValue] = Map.empty
}