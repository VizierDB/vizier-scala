package info.vizierdb.spark.caveats

import play.api.libs.json._
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.{ SparkSession, DataFrame }
import org.apache.spark.sql.catalyst.expressions.Expression
import info.vizierdb.util.TimerUtils
import info.vizierdb.Vizier

object QueryWithCaveats
  extends LazyLogging
  with TimerUtils
{
  class ResultTooBig extends Exception("The datsaet is too big to copy.  Try a sample or a LIMIT query instead.")

  val RESULT_THRESHOLD = 10000

  def apply(
    query: String,
    includeCaveats: Boolean,
    limit: Option[Int] = None,
    sparkSession: SparkSession = Vizier.sparkSession,
    views: Map[String, () => DataFrame],
    functions: Map[String, Seq[Expression] => Expression] = Map.empty
  ): DataContainer = 
  {
    apply(
      InjectedSparkSQL(sparkSession)(
        sqlText = query, 
        tableMappings = views, 
        functionMappings = functions, 
        allowMappedTablesOnly = true
      ),
      includeCaveats = includeCaveats, 
      limit = limit,
      computedProperties = Map.empty,
      offset = None,
      cacheAs = None,
      columns = None
    )
  }

  def apply(
    query: DataFrame,
    includeCaveats: Boolean
  ): DataContainer =
  {
    apply(
      query, 
      includeCaveats = includeCaveats, 
      limit = None,
      computedProperties = Map.empty,
      offset = None,
      cacheAs = None,
      columns = None
    )
  }

  def build(
    query: DataFrame, 
    includeCaveats: Boolean
  ): DataFrame =
  {
    // The order of operations in this method is very methodically selected:
    // - AnnotateWithRowIds MUST come before any operation that modifies UNION operators, since
    //   the order of the children affects the identity of their elements.
    // - AnnotateImplicitHeuristics MUST come before any operation that removes View markers (this
    //   includes AnnotateWithRowIds and caveat.trackCaveats)

    var df = query

    /////// Decorate any potentially erroneous heuristics
    df = AnnotateImplicitHeuristics(df)

    /////// ResolvePossible
    df = ResolveLifts(df)


    logger.trace(s"----------- RAW-QUERY-----------\nSCHEMA:{ ${Schema(df).mkString(", ")} }\n${df.queryExecution.explainString(SelectedExplainMode)}")

    /////// Add a __MIMIR_ROWID attribute
    df = AnnotateWithRowIds(df)

    logger.trace(s"----------- AFTER-ROWID -----------\n${df.queryExecution.explainString(SelectedExplainMode)}")


    /////// If requested, add a __CAVEATS attribute
    /////// Either way, after we track the caveats, we no longer need the
    /////// ApplyCaveat decorators
    if(includeCaveats){ df = df.trackCaveats.stripCaveats }
    else              { df = df.stripCaveats }
    
    logger.trace(s"############ \n${df.queryExecution.analyzed.treeString}")
    logger.trace("############")

    logger.trace(s"----------- AFTER-CAVEATS -----------\n${df.queryExecution.explainString(SelectedExplainMode)}")
  
    return df    
  }

  def apply(
    query: DataFrame,
    includeCaveats: Boolean,
    limit: Option[Int],
    computedProperties: Map[String,JsValue],
    offset: Option[Long],
    cacheAs: Option[String],
    columns: Option[Seq[String]]
  ): DataContainer =
  {

    // With/Without caveats ends up with a different table, so 
    // make sure to distinguish the identifiers.
    val cacheIdentifier = cacheAs.map { 
      (
        (if(includeCaveats){ "+caveat:" } else { "-caveat:" })
      ) + _
    }

    // The route to generating results is different, depending on
    // whether we're able to cache or not.
    val (results, resultFields) =
      cacheIdentifier match {
        case Some(id) => {

          // If we're allowed to use the cache...
          logger.trace(s"Checking cache for `$id`")
          val cache = DataFrameCache(id) { build(query, includeCaveats) }

          // With the cache, we can defer limit/offset to the
          // cache.
          val start = offset.getOrElse { 0l }
          val end = start + limit.map { _.toLong }
                                 .getOrElse { cache.size }
          
          // Do our standard sanity check to avoid implosions
          if(end-start > RESULT_THRESHOLD){ 
            throw new ResultTooBig()
          }

          val buffer = logTime("CACHE", cache.df.toString){
            cache(start, end)
          }

          /* return */ (buffer, Schema(cache.df))
        }

        /***************************************/
        case None => {

          // If we're not allowed to use the cache
          var df = build(query, includeCaveats)

          // We can't offload limit/offset to the cache, so
          // we need to modify the query to account for this.
          if(!limit.isEmpty){
            if(offset.isEmpty || offset.get == 0){
              // Limit + no offset is directly supported by spark
              df = df.limit(limit.get)
            } else {
              // Limit + offset requires us to use some manual
              // result row numbering hackery.
              df = AnnotateWithSequenceNumber(df)
              df = df.filter(
                (df(AnnotateWithSequenceNumber.ATTRIBUTE) >= offset.get)
                  and
                (df(AnnotateWithSequenceNumber.ATTRIBUTE) < (offset.get + limit.get))
              )
            }
          } else if(!offset.isEmpty){
            df = AnnotateWithSequenceNumber(df)
            df = df.filter(
              (df(AnnotateWithSequenceNumber.ATTRIBUTE) >= offset.get)
            )            
          }

          logTime("QUERY", df.toString) {
            val buffer = df.cache()
                           .take(RESULT_THRESHOLD+1)
            // We don't want to collect() naively, since if the
            // result ends up being too big, we're going to 
            // bring down the JVM.  Instead, we cap results at
            // RESULT_THRESHOLD.  
            //
            // The simple thing to do here would be to call
            // df.count() and make sure that the result
            // has the right size.  This requires two separate 
            // queries though, so we're going to take a simpler
            // hack: Read 1+RESULT_THRESHOLD rows.  This should
            // be minimally more expensive, while also letting
            // us easily flag cases where RESULT_THRESHOLD is
            // exceeded.
            if(buffer.size >= RESULT_THRESHOLD){ 
              throw new ResultTooBig()
            }

            /* return */ (buffer, Schema(df))
          } // logTime
        } // case None
      }// cacheIdentifier match { ... }

    /////// Create a mapping from field name to position in the output tuples
    val fieldLocationsByCaseInsentiveName = 
      resultFields
        .zipWithIndex
        .map { case (attribute, idx) => 
                  attribute.name.toLowerCase -> idx 
        }
        .toMap

    /////// Compute attribute positions for later extraction
    val fieldIndices:Seq[Int] = 
      columns.getOrElse { query.schema.fieldNames.toSeq }
             .map { field =>
               fieldLocationsByCaseInsentiveName(field.toLowerCase)
             }
    val schema: Seq[StructField] =
      columns match { 
        case None => query.schema.fields.toSeq 
        case Some(colNames) => {
          val fieldLookup = 
            query.schema
                 .fields
                 .map { f => f.name.toLowerCase() -> f }
                 .toMap
          colNames.map { f => fieldLookup(f.toLowerCase()) }
        }
      }
    val identifierAnnotation: Int = 
      fieldLocationsByCaseInsentiveName(AnnotateWithRowIds.ATTRIBUTE.toLowerCase)

    /////// If necessary, extract which rows/cells are affected by caveats from
    /////// the result table.
    val (colTaint, rowTaint): (Seq[Seq[Boolean]], Seq[Boolean]) = 
      if(includeCaveats){
        results.map { row =>
          val annotation = row.getAs[Row](Caveats.ANNOTATION_ATTRIBUTE)
          val columnAnnotations = annotation.getAs[Row](Caveats.ATTRIBUTE_FIELD)
          (
            schema.map { attribute => columnAnnotations.getAs[Boolean](attribute.name) },
            annotation.getAs[Boolean](Caveats.ROW_FIELD)
          )
        }.toSeq.unzip[Seq[Boolean], Boolean]
      } else { (Seq[Seq[Boolean]](), Seq[Boolean]()) }

    /////// Dump the final results.
    DataContainer(
      schema,
      results.map { row => fieldIndices.map { row.get(_) } }.toSeq,
      // use s"" instead of .toString below to handle nulls correctly
      results.map { row => s"${row.get(identifierAnnotation)}" }.toSeq,
      colTaint, 
      rowTaint,
      Seq(),
      computedProperties
    )
  }

  def getSchema(
    query: String,
    sparkSession: SparkSession = MimirAPI.sparkSession
  ): Seq[StructField] = { 
    val df = InjectedSparkSQL(sparkSession)(query, MimirAPI.catalog.allTableConstructors)
    Schema(df)
  }

}