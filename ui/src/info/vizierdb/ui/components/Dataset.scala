package info.vizierdb.ui.components

import rx._
import org.scalajs.dom
import scalatags.JsDom.all._
import scalajs.js
import info.vizierdb.ui.rxExtras._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.types._
import info.vizierdb.encoding

class Dataset(id: Identifier)
             (implicit val owner: Ctx.Owner)
{
  val columns = RxBuffer[Column]()
  val rows = RxBuffer[Row]()
  val name = Var[String]("unnamed")

  def this(serialized: encoding.Dataset)
          (implicit owner: Ctx.Owner) =
  {
    this(serialized.id.toString)
    loadColumns(serialized.columns)
    loadRows(serialized.rows)
    name() = serialized.name
  }

  def loadColumns(serializedColumns: Seq[encoding.DatasetColumn]) =
  {
    columns.clear()
    for(col <- serializedColumns){
      columns.append(new Column(col))
    }
  }

  def loadRows(serializedRows: Seq[encoding.DatasetRow]) =
  {
    rows.clear()
    for(row <- serializedRows){
      rows.append(new Row(row))
    }
  }


  class Column(
    id: Identifier,
    name: String,
    dataType: String
  ){
    val root = th(
      `class` := "column_header",
      name,
      span(`class` := "column_type", s"($dataType)")
    )
    def this(encoded: encoding.DatasetColumn){
      this(encoded.id.toString, encoded.name, encoded.`type`)
    }
  }

  class Row(
    id: Identifier,
  ){
    val values = RxBuffer[Cell]()
    val isAnnotated = Var[Boolean](false)

    private val view = RxBufferView(tr(), values.rxMap { _.root })
    def root:dom.Node = view.root
    
    def this(encoded: encoding.DatasetRow){
      this(encoded.id.toString)
      val inputs = 
        encoded.values.zip(
          encoded.rowAnnotationFlags
        )
      for( (value, isCaveatted) <- inputs ){
        values.append( new Cell(value, isCaveatted) )
      }
      isAnnotated() = encoded.rowIsAnnotated
    }

  }

  class Cell(
    value: js.Dynamic,
    isCaveatted: Boolean
  ){
    def valueString: String = 
      if(value == null) { "" }
      else if(value.equals(js.undefined)){ "" }
      else { value.toString }

    lazy val root:dom.Node = 
      td(
        `class` := "dataset_value "+
          (if(isCaveatted){ "caveatted" } else { "not_caveatted" }),
        valueString
      )
  }

  private val columnView = RxBufferView(tr(), columns.rxMap { _.root })
  private val rowView = RxBufferView(tbody(), rows.rxMap { _.root })

  lazy val root = div(
    `class` := "dataset",
    Rx { h3(name()) },
    table(
      thead(columnView.root),
      rowView.root
    )
  )

}