package info.vizierdb.api

import info.vizierdb.catalog.{ CatalogDB, Schema, Metadata }

object GetRegistryKey
{
  def apply(key: String): String =
  {
    CatalogDB.withDBReadOnly { implicit session => 
      Metadata.get(key)
    }
  }
}