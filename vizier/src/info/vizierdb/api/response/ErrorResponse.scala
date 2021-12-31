package info.vizierdb.api.response

import info.vizierdb.api.Response

case class ErrorResponse(response: Response) extends Exception(response.toString)

object ErrorResponse
{
  def noSuchEntity =
    throw new ErrorResponse(NoSuchEntityResponse())

  def invalidRequest(msg: String) =
    throw new ErrorResponse(VizierErrorResponse("Invalid", msg))
}