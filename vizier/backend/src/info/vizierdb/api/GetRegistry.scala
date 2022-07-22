package info.vizierdb.api

import info.vizierdb.catalog.{ CatalogDB, Schema, Metadata }

object GetRegistry
{
  def apply(): Map[String, String] =
  {
    CatalogDB.withDBReadOnly { implicit session => 
      Metadata.all
    }.toMap 
  }
}