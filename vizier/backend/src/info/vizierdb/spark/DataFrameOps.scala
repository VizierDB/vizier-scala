package info.vizierdb.spark

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Column
import org.apache.spark.sql.catalyst.expressions.NamedExpression
import info.vizierdb.VizierException

object DataFrameOps
{
  def columns(df: DataFrame): Seq[Column] = 
    outputs(df).map { new Column(_) }

  def columnsWhere(df: DataFrame)(cond: String => Boolean): Seq[Column] = 
    outputs(df).filter { x => cond(x.name) }
               .map { new Column(_) }

  def outputs(df: DataFrame): Seq[NamedExpression] =
    df.queryExecution.logical.output
  
  def safeColumnLookup(df: DataFrame, col: String): Column =
    safeColumnLookupOpt(df, col).getOrElse { 
      throw new VizierException(s"Expected to find $col in ${df.columns.mkString(", ")}")
    }

  def safeColumnLookupOpt(df: DataFrame, col: String): Option[Column] =
    safeOutputLookupOpt(df, col)
      .map { new Column(_) }

  def safeOutputLookup(df: DataFrame, col: String): NamedExpression =
    safeOutputLookupOpt(df, col).getOrElse { 
      throw new VizierException(s"Expected to find $col in ${df.columns.mkString(", ")}")
    }
    
  def safeOutputLookupOpt(df: DataFrame, col: String): Option[NamedExpression] =
    df.queryExecution.logical.output
      .find { _.name == col }

}