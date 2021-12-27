package info.vizierdb.api

case class FormattedError(
  message: String
) extends Exception(message)

object FormattedError
{
  def apply(
    e: Throwable, 
    msg: String, 
    className: String = "org.mimirdb.api.FormattedError"
  ): FormattedError = 
    FormattedError(msg)

}