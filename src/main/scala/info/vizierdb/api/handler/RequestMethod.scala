package info.vizierdb.api.handler

object RequestMethod extends Enumeration
{
  type T = Value
  val GET,
      PUT,
      POST,
      DELETE,
      OPTIONS = Value
}