package info.vizierdb.util

object StringUtils
{
  def ellipsize(text: String, len: Int): String =
        if(text.size > len){ text.substring(0, len-3)+"..." } else { text }
}

