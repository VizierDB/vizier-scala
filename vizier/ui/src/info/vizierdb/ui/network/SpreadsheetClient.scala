package info.vizierdb.ui.network

import rx._
import info.vizierdb.types._
import scala.scalajs.js.timers._
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.api.spreadsheet._
import play.api.libs.json._
import info.vizierdb.util.Logging
import info.vizierdb.serialized.DatasetColumn
import info.vizierdb.util.RowCache
import info.vizierdb.serialized.DatasetRow
import info.vizierdb.nativeTypes._
import scala.collection.mutable
import info.vizierdb.ui.components.dataset._
import scala.concurrent.Promise
import scala.util.Success
import scala.concurrent.Future

class SpreadsheetClient(projectId: Identifier, datasetId: Identifier, api: API)
  extends TableDataSource
  with Logging
{
  var socket = getSocket() 
  var keepaliveTimer: SetIntervalHandle = null

  val connected = Var(false)
  val name = Var("Untitled?")
  var schema = Seq[DatasetColumn]()
  var size = 0l
  val rows = mutable.Map[Long, RowBatch]()
  var table: Option[TableView] = None
  var savePromise: Option[Promise[Identifier]] = None

  val BUFFER_SIZE = 20
  var BUFFER_PAGE = 1000

  protected[ui] def getSocket(): dom.WebSocket =
  {
    logger.info(s"Connecting to ${api.spreadsheet}")
    val s = new dom.WebSocket(api.spreadsheet)
    s.onopen = onConnected
    s.onclose = onClosed
    s.onerror = onError
    s.onmessage = onMessage
    keepaliveTimer = setInterval(20000) { keepalive(s) }
    s
  }

  def send(message: SpreadsheetRequest)
  {
    socket.send(Json.toJson(message).toString)
  }

  def onMessage(message: dom.MessageEvent) =
  {
    logger.trace(s"Got: ${message.data.asInstanceOf[String].take(20)}")
    try {
      Json.parse(message.data.asInstanceOf[String])
          .as[SpreadsheetResponse] match {
            case Connected(name)                 => this.name() = name; connected() = true
            case UpdateSchema(newSchema)         => setSchema(newSchema); table.foreach { _.rebuildHeaderRow() }
            case UpdateSize(newSize)             => setSize(newSize); table.foreach { _.refreshSize }
            case Pong(id)                        => logger.debug(s"Pong $id")
            case DeliverRows(start, data)        => updateData(start, data)
            case DeliverCell(column, row, value) => updateCell(column, row, value)
            case ReportError(err, detail)        => logger.error(err+"\n\n"+detail)
            case SaveSuccessful(newModuleId)     => onSaveSuccess(newModuleId)
          }
    } catch {
      case e: Throwable => 
        logger.error("Error reading spreadsheet socket message")
        logger.error(s"Message content: ${message.data}")
        e.printStackTrace()
    }
  }

  def onSaveSuccess(newModuleId: Identifier)
  {
    if(savePromise.isDefined){ 
      savePromise.get.complete(Success(newModuleId)) 
      savePromise = None
    }
    else { logger.error("Save success without a pending request!") }
  }

  def onConnected(event: dom.Event)
  {
    logger.debug("Connected!")
    send(OpenSpreadsheet(projectId, datasetId))
  }

  def onClosed(event: dom.Event)
  {
    if(keepaliveTimer != null) { 
      clearInterval(keepaliveTimer)
      keepaliveTimer = null
    }
    connected() = false
  }
  def onError(event: dom.Event) = 
  {
    logger.error(s"Error: $event")
  }

  def save(branchId: Identifier, moduleId: Identifier, replace: Boolean): Future[Identifier] =
  {
    savePromise = Some(Promise[Identifier]())
    send(SaveSpreadsheet(branchId, moduleId, replace))
    savePromise.get.future
  }

  def saveAfter(branchId: Identifier, moduleId: Identifier): Future[Identifier] =
    save(branchId, moduleId, replace = false)

  def saveAs(branchId: Identifier, moduleId: Identifier): Future[Identifier] =
    save(branchId, moduleId, replace = true)

  private def keepalive(s: dom.WebSocket)
  {
    send(Ping(0))
  }

  def subscribe(start: Long) =
  {
    assert(start % BUFFER_PAGE == 0, "Request to subscribe to an unaligned buffer page")
    rows.put(start, new RowBatch(start))
    send(SubscribeRows(start, BUFFER_PAGE))
  }

  def unsubscribe(start: Long): Unit =
  {
    assert(start % BUFFER_PAGE == 0, "Request to unsubscribe from an unaligned buffer page")
    if(rows contains start){
      send(UnsubscribeRows(start, BUFFER_PAGE))
      rows.remove(start)
    }
  }

  def prefill(schema: Seq[DatasetColumn], source: RowCache[DatasetRow]): Unit = 
  {
    // There's a bunch of messy state involving subscriptions, page sizes, etc...
    // I'm not going to deal with it right now.  If you hit the following assertion
    // then it's not impossible, but will require a bit of thought.
    assert(rows.isEmpty, "Can't prefill a spreadsheet client once it is in-use")
    BUFFER_PAGE = source.BUFFER_PAGE
    for((start, batch) <- source.cache){
      batch.data match {
        case None       => rows.put(start, new RowBatch(start)) // still waiting on data,  set up a placeholder
        case Some(data) => rows.put(start, new RowBatch(start, data))
      }
      subscribe(start)
    }
  }

  def setSchema(newSchema: Seq[DatasetColumn]): Unit =
  {
    schema = newSchema
    logger.debug(s"New Schema: $schema")
    table.foreach { _.rebuildHeaderRow() }
  }

  def setSize(newSize: Long): Unit =
  {
    size = newSize
    table.foreach { _.refreshSize() }
  }


  def updateData(start: Long, data: Array[Array[SpreadsheetCell]]): Unit =
  {
    var dataOffset: Int = 0
    var row = start
    logger.debug(s"Data updated: ${data.size} rows @ $start")
    while(row < start + data.size){
      val batch = row - row % BUFFER_PAGE
      if(rows contains batch){
        rows(batch).updateRows(start, dataOffset, data)
      }
      dataOffset += (BUFFER_PAGE - (row % BUFFER_PAGE).toInt)
      row = batch + BUFFER_PAGE
    }
    table.foreach { _.refresh(start, data.size) }
  }
  def updateCell(column: Int, row: Long, cell: SpreadsheetCell): Unit =
  {
    val batch = row - row % BUFFER_PAGE
    if(rows contains batch){
      rows(batch).updateCell(column, row, cell) 
    }
    table.foreach { _.refresh(row, 1) }
  }

  class RowBatch(val start: Long, val data: Array[Array[SpreadsheetCell]])
  {
    def this(start: Long) =
      this(start, new Array[Array[SpreadsheetCell]](BUFFER_PAGE))

    def this(start: Long, data: Seq[DatasetRow]) = 
      this(start, 
        data.map { row =>
          if(row.rowAnnotationFlags.isDefined){
            row.values.zip(row.rowAnnotationFlags.get)
               .map { case (v, c) => NormalValue(v, c) }
               .toArray[SpreadsheetCell]
          } else {
            row.values.map { NormalValue(_, false) }
                      .toArray[SpreadsheetCell]
          }
        }.padTo(BUFFER_PAGE, null).toArray
      )

    def apply(col: Int, row: Long): SpreadsheetCell = 
    {
      assert(row >= start && row < start+BUFFER_PAGE)
      Option(data( (row - start).toInt ))
        .map { rowData => if(col >= rowData.size) { ValueInProgress } 
                          else { rowData(col) } }
        .getOrElse { ValueInProgress }
    }

    def updateRows(firstRow: Long, dataOffset: Int, data: Array[Array[SpreadsheetCell]]) =
    {
      assert(firstRow >= start && firstRow < start+BUFFER_PAGE)
      val startIdx = (firstRow - start).toInt
      val count = math.min(BUFFER_PAGE - startIdx, data.size - dataOffset).toInt
      for( i <- (0 until count) ){
        this.data(startIdx+i) = data(dataOffset+i)
      }
    }

    def updateCell(column: Int, row: Long, cell: SpreadsheetCell) =
    {
      assert(row >= start && row < start+BUFFER_PAGE)
      assert(column >= 0 && column < schema.size)
      val offset = (row - start).toInt
      if(this.data(offset) == null) { data(offset) = new Array[SpreadsheetCell](schema.size) }
      data(offset)(column) = cell
    }

  }

  def batchForRow(row: Long): RowBatch =
  {
    val batch = row - row % BUFFER_PAGE
    if(rows contains batch){ return rows(batch) }
    else { subscribe(batch); return rows(batch) }
  }

  def displayCaveat(row: Long, column: Option[Int])
  {
    println(s"WOULD DISPLAY CAVEAT FOR $row:$column")
    // CaveatModal(
    //   projectId = projectId,
    //   datasetId = datasetId,
    //   row = Some(row), 
    //   column = column
    // ).show
  }

  var currentlyEditingCell: Option[(Long, Int)] = None
  var currentlyEditingField: Option[dom.html.Input] = None

  def startEditing(row: Long, column: Int): Unit =
  {
    stopEditing()
    val currentValue = 
      // TODO: Call "Get Expression" before triggering the edit
      batchForRow(row)(column, row) match {
        case NormalValue(JsNull, _) => Some("")
        case NormalValue(JsString(s), _) => Some(s)
        case NormalValue(JsNumber(n), _) => Some(n.toString)
        case ErrorValue(_, _) => Some("")
        case x => logger.debug(s"Don't know how to interpret $x"); None
      }
    if(currentValue.isEmpty){ 
      logger.debug(s"Can't edit $row:$column")
      return
    }
    currentlyEditingField = Some(input(
      `type` := "text",
      value := currentValue.get,
      onkeypress := { event:dom.KeyboardEvent => 
        if(event.keyCode == 13){ // enter key
          event.preventDefault()
          stopEditing()
        }
      },
      autofocus
    ).render)
    currentlyEditingCell = Some((row, column))
    table.foreach { _.refreshCell(row, column) }
    for(i <- currentlyEditingField){
      // Set the input as the input target
      i.focus()
      // Select the entire text
      i.setSelectionRange(0, currentValue.get.length())
    }
  }

  def stopEditing(commit: Boolean = true) =
  {
    if(currentlyEditingCell.isDefined){
      val (row, column) = currentlyEditingCell.get
      if(commit){
        send(EditCell(column, row, JsString(currentlyEditingField.get.value)))
      }

      currentlyEditingCell = None
      currentlyEditingField = None
      table.foreach { _.refreshCell(row, column) }
    }
  }

  def cellAt(row: Long, column: Int, width: Int, xpos: Int): Frag =
  {
    if(currentlyEditingCell == Some((row, column))){
      currentlyEditingField match { 
        case None => 
          RenderCell.spinner(schema(column).dataType)
        case Some(i) => 
          div(
            `class` := "cell", 
            css("width") := s"${width}px",
            left := xpos,
            height := "100%",
            i
          )
      }
        
    } else {
      batchForRow(row)(column, row) match {
        case NormalValue(value, caveat) => 
          RenderCell(
            value, 
            schema(column).dataType, 
            width = width,
            position = xpos,
            caveatted = if(caveat){ Some((trigger: dom.html.Button) => displayCaveat(row, Some(column))) } else { None },
            onclick = { _:dom.Event => startEditing(row, column) }
          )
        case ValueInProgress => 
          RenderCell.spinner(schema(column).dataType)
        case ErrorValue(err, detail) =>
          div(
            `class` := "cell error",
            err,
            onclick := { _:dom.Event => startEditing(row, column) }
          )
      }
    }
  }
  def columnCount: Int = 
    schema.size

  def columnDataType(column: Int): CellDataType =
    schema(column).dataType

  def columnWidthInPixels(column: Int): Int =
    TableView.DEFAULT_CELL_WIDTH

  def columnTitle(column: Int): String = 
    schema(column).name

  def rowClasses(row: Long): Seq[String] = 
    Seq.empty

  def rowCount: Long = 
    size

  def rowCaveat(row: Long): Option[() => Unit] = None

}