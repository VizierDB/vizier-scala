package info.vizierdb.ui.widgets

import rx._
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._

class SearchWidget(
  placeholderText: String = "Search ..."
)(implicit owner: Ctx.Owner)
{

  val value = Var[Option[String]](None)

  def filter[T](data: Rx[Iterable[T]])(test: (T, String) => Boolean): Rx[Iterable[T]] =
  {
    Rx { 
      value() match {
        case None => data()
        case Some(term) => data().filter { test(_, term) }
      }
    }
  }


  def updateValue(): Unit =
  {
    if(searchInput.value != value.now.getOrElse("")){
      if(searchInput.value.isEmpty){ 
        value() = None
      } else {
        value() = Some(searchInput.value)
      }
    }
  }

  val searchInput = 
    input(
      placeholder := placeholderText,
      onkeydown := { _:dom.Event =>  
                        dom.window.requestAnimationFrame { _ => updateValue() } }
    ).render


  val root = 
    div(`class` := "search_field",
      FontAwesome("search"),
      searchInput,
      Rx { 
        if(value().isEmpty) {
          button(
            FontAwesome("ban"),
            visibility := "hidden"
          )
        } else {
          button(
            FontAwesome("ban"),
            onclick := { _:dom.Event =>
              searchInput.value = ""
              dom.window.requestAnimationFrame( _ => updateValue() )
            }
          )
        }
      }.reactive
    )

}

object SearchWidget
{
  def apply(
    placeholderText: String = "Search..."
  )(implicit owner: Ctx.Owner): SearchWidget = 
    new SearchWidget(
      placeholderText = placeholderText
    )
}