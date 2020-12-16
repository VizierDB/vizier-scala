package info.vizierdb.commands

object TemplateParameters
{
  val COLUMN = 
    ColIdParameter(id = "column", name = "Column")

  val SCHEMA = 
    ListParameter(name = "Schema (leave blank to guess)", id = "schema", required = false, components = Seq(
      StringParameter(name = "Column Name", id = "schema_column", required = false),
      EnumerableParameter(name = "Data Type", id = "schema_datatype", required = false, values = EnumerableValue.withNames(
        "String"         -> "string",
        "Real"           -> "real",
        "Float"          -> "float",
        "Bool"           -> "boolean",
        "16-bit Integer" -> "short",
        "32-bit Integer" -> "int",
        "64-bit Integer" -> "long",
        "1 Byte"         -> "byte",
        "Date"           -> "date",
        "Date+Time"      -> "timestamp",
      ), default = Some(0))
    )),

}