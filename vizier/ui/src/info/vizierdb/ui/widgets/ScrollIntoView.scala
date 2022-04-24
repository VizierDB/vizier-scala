package info.vizierdb.ui.widgets

import org.scalajs.dom.html
import scala.scalajs.js

object ScrollIntoView
{

  object Behavior extends Enumeration
  {
    type T = Value
    val Auto, Smooth = Value
  }
  object Block extends Enumeration
  {
    type T = Value
    val Start, Center, End, Nearest = Value
  }
  object Inline extends Enumeration
  {
    type T = Value
    val Start, Center, End, Nearest = Value
  }

  trait CanScroll
  {
    val root: html.Element
    def scrollIntoView(): Unit =
      ScrollIntoView(root, 
        behavior = ScrollIntoView.Behavior.Smooth, 
        block = ScrollIntoView.Block.Center
      )
  }

  private trait ScrollIntoViewOptions extends js.Object {
    var behavior: js.UndefOr[String] = js.undefined
    var block: js.UndefOr[String] = js.undefined
    var inline: js.UndefOr[String] = js.undefined
  }

  @js.native
  trait OptionedScrollIntoView extends js.Object {
    def scrollIntoView(options: ScrollIntoViewOptions): Unit
  }

  def apply(
    element: html.Element, 
    behavior: js.UndefOr[Behavior.T] = js.undefined,
    block: js.UndefOr[Block.T] = js.undefined,
    inline: js.UndefOr[Inline.T] = js.undefined,
  ) = 
  {
    val a = behavior
    val b = block
    val c = inline
    element.asInstanceOf[OptionedScrollIntoView]
           .scrollIntoView(new ScrollIntoViewOptions {
             behavior = a.map { _.toString.toLowerCase }
             block    = b.map { _.toString.toLowerCase }
             inline   = c.map { _.toString.toLowerCase }
           })
  }
}