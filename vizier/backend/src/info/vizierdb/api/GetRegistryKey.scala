package info.vizierdb.api

import info.vizierdb.catalog.{ CatalogDB, Schema, Metadata }
import info.vizierdb.api.response._

object GetRegistryKey
{
  def apply(key: String): String =
  {
    CatalogDB.withDBReadOnly { implicit session => 
      Metadata.getOption(key)
              .getOrElse {
                ErrorResponse.noSuchEntity
              }
    }
  }
}