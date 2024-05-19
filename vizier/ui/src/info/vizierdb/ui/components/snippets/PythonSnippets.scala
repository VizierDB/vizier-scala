/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
package info.vizierdb.ui.components.snippets


object PythonSnippets extends SnippetsBase
{
  AddGroup("desktop", "Access and Output")(
    "Get Artifact"            -> """# Get the artifact with the given name.
                                   |ds = vizierdb['ARTIFACT_NAME']""".stripMargin,

    "Get Dataset Dataframe"   -> """# Get read-only pandas dataframe object for dataset with given name.');
                                   |df = vizierdb.get_data_frame('UNIQUE_DS_NAME')""".stripMargin,

    "Print Column Names"      -> """# Iterate over list of dataset columns and print column name
                                   |for col in ds.columns:
                                   |    print(col.name)""".stripMargin,

    "Print Cell Values"       -> """# Iterate over list of dataset rows and print cell value.
                                   |# Reference column by name, label ('A', 'B', ...), or
                                   |# column index (0, 1, ...).
                                   |for row in ds.rows:
                                   |    print(row.get_value('NAME_LABEL_OR_INDEX'))""".stripMargin,

    "Print Cell Annotations"  -> """# Print all annotations for dataset column. Note that
                                   |# rows and columns are referenced by their unique identifiers
                                   |col = ds.columns[ds.column_index('NAME_LABEL_OR_INDEX')]
                                   |for row in ds.rows:
                                   |    annos = ds.annotations.for_cell(col.identifier, row.identifier)
                                   |    for key in annos.keys():
                                   |        for a in annos.find_all(key):
                                   |            print(a.key + ' = ' + str(a.value))""".stripMargin,

    "Output Plot"             -> """# Generate a plot using Bokeh
                                   |# (Expects a dataset named `ds`)
                                   |# See https://bokeh.pydata.org/en/latest/docs/reference.html
                                   |from bokeh.plotting import figure
                                   |plot = figure(title = 'MY_FIGURE')
                                   |plot.line(
                                   |  x = 'X_COLUMN_NAME', 
                                   |  y = 'Y_COLUMN_NAME', 
                                   |  source = ds.to_bokeh()
                                   |)
                                   |show(plot)""".stripMargin,

    "Output Map"              -> """# Render an interactive OpenStreetMap with points marked
                                   |# (Expects a dataset named `ds`)
                                   |ds.show_map(
                                   |  lat_col = 'LATITUDE_COLUMN_NAME',
                                   |  lon_col = 'LONGITUDE_COLUMN_NAME',
                                   |  label_col = 'LABEL_COLUMN_NAME' # Optional
                                   |)""".stripMargin,

    "Export Module"           -> """# Export a variable, a function or a class for use in subsequent cells
                                   |def add_numbers(number_1, number_2):
                                   |    print('adding ' + str(number_1) + ' + ' +str(number_2))
                                   |    return number_1 + number_2
                                   |vizierdb.export_module(
                                   |    add_numbers
                                   |)
                                   |#  Use it in a subsequent step like normal: add_numbers(1,2)""".stripMargin,
  )

  AddGroup("plus", "New")(
    "New Dataset Object"  -> """# Get object containing an empty dataset.
                               |ds = vizierdb.new_dataset()""".stripMargin,

    "Insert Column"       -> """# Create a new column with a given name. The column
                               |# position is optional.
                               |ds.insert_column('COLUMN_NAME', position=0)""".stripMargin,

    "Insert Row"          -> """# When inserting a row the list of values and the row
                               |# position are optional. By default row cells are None.
                               |ds.insert_row(values=['LIST_OF_VALUES_FOR_EACH_COLUMN'], position=0)""".stripMargin,

    "Annotate Cell Value" -> """# Add new annotation for dataset cells. Note that rows
                               |# and columns are referenced by their unique identifiers
                               |col = ds.columns[ds.column_index('NAME_LABEL_OR_INDEX')]
                               |for row in ds.rows:
                               |    annos = ds.annotations.for_cell(col.identifier, row.identifier)
                               |    if not annos.contains('SOME:KEY'):
                               |        annos.add('SOME:KEY', 'SOME_VALUE')
                               |  )""".stripMargin,
  )

  AddGroup("edit", "Update")(
    "Edit Cell Values"        -> """# Iterate over list of dataset rows and update cell value.
                                   |# Reference column by name, label ('A', 'B', ...), or
                                   |# column index (0, 1, ...).
                                   |for row in ds.rows:
                                   |    row.set_value('NAME_LABEL_OR_INDEX', value)""".stripMargin,

    "Update Cell Annotations" -> """# Update all annotations for dataset cell. Note that
                                   |# rows and columns are referenced by their unique identifiers.
                                   |col = ds.columns[ds.column_index('NAME_LABEL_OR_INDEX')]
                                   |for row in ds.rows:
                                   |    annos = ds.annotations.for_cell(col.identifier, row.identifier)
                                   |    for a in annos.find_all('SOME:KEY'):'
                                   |        annos.update(identifier=a.identifier, value='SOME_VALUE')""".stripMargin,

    "Save Dataset"            -> """# Save or update existing dataset with given name.
                                   |# The name is optional unless this is a new dataset.
                                   |ds.save('OPTIONAL_NEW_NAME')""".stripMargin,
  )

  AddGroup("trash", "Delete")(
    "Delete Dataset"           -> """# Delete dataset with the given name from data store.
                                    |vizierdb.drop_dataset('UNIQUE_DS_NAME')""".stripMargin,

    "Delete Dataset Column"    -> """# Delete dataset column by name (only if column names
                                    |# are unique), spreadsheet label ('A', 'B', ...), or
                                    |# column index (0, 1, ...).
                                    |ds.delete_column('NAME_LABEL_OR_INDEX')""".stripMargin,

    "Delete Dataset Row"       -> """# Delete dataset rows by removing them from the list of
                                    |# rows using the row index.
                                    |del ds.rows[index]""".stripMargin,

    "Delete Cell Annotations"  -> """# Delete annotations for dataset cell. Delete is similar to
                                    |# update. Delete an annotation by setting its value to None.
                                    |# Rows and columns are referenced by their unique identifiers
                                    |col = ds.columns[ds.column_index('NAME_LABEL_OR_INDEX')]
                                    |for row in ds.rows:
                                    |    annos = ds.annotations.for_cell(col.identifier, row.identifier)
                                    |    for a in annos.find_all('SOME:KEY'):
                                    |        annos.update(identifier=a.identifier, value=None)""".stripMargin,
  )
}


