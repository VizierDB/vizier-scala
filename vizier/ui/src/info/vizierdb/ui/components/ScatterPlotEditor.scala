package info.vizierdb.ui.components

import info.vizierdb.ui.components._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.widgets.FontAwesome
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.serialized.FilesystemObject
import info.vizierdb.types.MIME
import info.vizierdb.ui.Vizier
import info.vizierdb.types.DatasetFormat
import play.api.libs.json._
import org.scalajs.dom
import scalatags.JsDom.all._
import rx._
import info.vizierdb.serialized
import scala.concurrent.ExecutionContext.Implicits.global
import info.vizierdb.util.Logging
import info.vizierdb.serializers._
import info.vizierdb.api.websocket
import info.vizierdb.types.ArtifactType
import info.vizierdb.serialized.{
    CommandArgument,
    CommandArgumentList,
    CommandDescription,
    ParameterDescriptionTree,
    DatasetSummary,
    DatasetDescription,
    ArtifactDescription,
    DatasetColumn,
    PackageCommand,
    PropertyList
}
import info.vizierdb.ui.rxExtras.{OnMount, RxBuffer, RxBufferView}
import info.vizierdb.types._
import info.vizierdb.nativeTypes.JsValue
import scala.util.{Success, Failure}
import info.vizierdb.ui.network.BranchSubscription
import info.vizierdb.ui.network.BranchWatcherAPIProxy
import info.vizierdb.ui.widgets.FontAwesome
import java.awt.Font
import scala.concurrent.Future
import info.vizierdb.serialized.CommandArgument



