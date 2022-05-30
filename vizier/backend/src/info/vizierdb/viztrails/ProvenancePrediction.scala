package info.vizierdb.viztrails

case class ProvenancePrediction(
  val reads: Set[String] = Set.empty,
  val deletes: Set[String] = Set.empty,
  val writes: Set[String] = Set.empty,
  val openWorldReads: Boolean = true,
  val openWorldWrites: Boolean = true,
)
{
  def definitelyReads(i: String*) = 
    copy( reads = i.toSet )
  def definitelyWrites(i: String*) = 
    copy( writes = i.toSet )
  def definitelyDeletes(i: String*) = 
    copy( deletes = i.toSet )
  def andNothingElse = 
    copy( openWorldReads = false, openWorldWrites = false )

}

object ProvenancePrediction
{
  def default = ProvenancePrediction()

  def definitelyReads(i: String*) = 
    ProvenancePrediction( reads = i.toSet )
  def definitelyWrites(i: String*) = 
    ProvenancePrediction( writes = i.toSet )
  def definitelyDeletes(i: String*) = 
    ProvenancePrediction( deletes = i.toSet )
  def empty = 
    ProvenancePrediction( openWorldReads = false, openWorldWrites = false )
}
