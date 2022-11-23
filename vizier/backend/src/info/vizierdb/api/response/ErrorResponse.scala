package info.vizierdb.api.response

import info.vizierdb.api.BytesResponse

case class ErrorResponse(response: BytesResponse) extends Exception(response.toString)

object ErrorResponse
{
  def noSuchEntity =
    throw new ErrorResponse(NoSuchEntityResponse())

  def invalidRequest(msg: String) =
    throw new ErrorResponse(VizierErrorResponse("Invalid", msg))
}