package info.vizierdb.api.response

import org.mimirdb.api.Response

class ErrorResponse(response: Response) extends Exception

object ErrorResponse
{
  def noSuchEntity =
    throw new ErrorResponse(NoSuchEntityResponse())

  def invalidRequest(msg: String) =
    throw new ErrorResponse(VizierErrorResponse("Invalid", msg))
}