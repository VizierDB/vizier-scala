package info.vizierdb.util

object StringUtils
{
  def ellipsize(text: String, len: Int): String =
        if(text.size > len){ text.substring(0, len-3)+"..." } else { text }

  def camelCaseToHuman(str: String) = 
  {
    "([^A-Z])([A-Z])"
      .r
      .replaceAllIn(str, { m => m.group(1) + " " + m.group(2) })
  }
}

