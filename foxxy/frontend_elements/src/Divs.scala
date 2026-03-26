package foxxy.frontend_elements

import com.raquo.laminar.api.L.*
implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

import com.raquo.laminar.modifiers.RenderableSeq

object Divs {

  def vDiv(inputMods: Modifier[Div]*) = div(
    overflow.auto,
    alignItems.stretch,
    height    := "100%",
    width     := "100%",
    display.flex,
    flexDirection.column,
    boxSizing.borderBox,
    flexBasis := "0",
    flexGrow  := 1,
    inputMods
  )

  def hDiv(inputMods: Modifier[Div]*) = div(
    overflow.auto,
    alignItems.stretch,
    width     := "100%",
    height    := "100%",
    display.flex,
    flexDirection.row,
    boxSizing.borderBox,
    flexBasis := "0",
    flexGrow  := 1,
    inputMods
  )

  def hDivA(inputMods: Modifier[Div]*) = hDiv(
    overflow.unset,
    width.auto,
    height.auto,
    flexBasis.unset,
    flexGrow.unset,
    inputMods
  )

  def vDivA(inputMods: Modifier[Div]*) = vDiv(
    overflow.unset,
    width.auto,
    height.auto,
    flexBasis.unset,
    flexGrow.unset,
    inputMods
  )

}
