package info.vizierdb.api

import info.vizierdb.catalog.{ CatalogDB, Schema, Metadata }

object SetRegistryKey
{
  def apply(key: String, value: String): Boolean =
  {
    CatalogDB.withDB { implicit session => 
      Metadata.put(key, value)
    }
    true
  }
}