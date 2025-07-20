# -- copyright-header:v4 --
# Copyright (C) 2017-2025 University at Buffalo,
#                         New York University,
#                         Illinois Institute of Technology,
#                         Breadcrumb Analytics.
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#     http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# -- copyright-header:end --
"""Classes to manipulate vizier datasets from within the Python workflow cell.
"""

from typing import TYPE_CHECKING, Optional, Union, Dict, Any, List, cast
if TYPE_CHECKING:
    from pycell.client import VizierDBClient

import datetime
import io
import math
from bokeh.models.sources import ColumnDataSource  # type: ignore[import]

"""Identifier for column data types. By now the following data types are
distinguished: date (format yyyy-MM-dd), int, varchar, real, and datetime
(format yyyy-MM-dd hh:mm:ss:zzzz).
"""
DATATYPE_DATE = 'date'
DATATYPE_DATETIME = 'timestamp'
DATATYPE_INT = 'int'
DATATYPE_SHORT = 'short'
DATATYPE_LONG = 'long'
DATATYPE_REAL = 'real'
DATATYPE_VARCHAR = 'varchar'
DATATYPE_GEOMETRY = "geometry"
DATATYPE_BINARY = 'binary'
DATATYPE_IMAGE = 'image/png'


VIZUAL_DELETE_COLUMN  = "deletecolumn"
VIZUAL_DELETE_ROW     = "deleterow"
VIZUAL_INSERT_COLUMN  = "insertcolumn"
VIZUAL_INSERT_ROW     = "insertrow"
VIZUAL_MOVE_COLUMN    = "movecolumn"
VIZUAL_MOVE_ROW       = "moverow"
VIZUAL_FILTER_COLUMNS = "projection"
VIZUAL_RENAME_COLUMN  = "renamecolumn"
VIZUAL_UPDATE_CELL    = "updatecell"
VIZUAL_SORT           = "sort"


class DatasetColumn(object):
  """Column in a dataset. Each column has a unique identifier and a
  column name. Column names are not necessarily unique within a dataset.

  Attributes
  ----------
  identifier: int
      Unique column identifier
  name: string
      Column name
  data_type: string, optional
      String representation of the column type in the database. By now the
      following data_type values are expected: date (format yyyy-MM-dd), int,
      varchar, real, and datetime (format yyyy-MM-dd hh:mm:ss:zzzz).
  """
  def __init__(self,
               identifier: int = -1,
               name: Optional[str] = None,
               data_type: str = DATATYPE_VARCHAR):
    """Initialize the column object.

    Parameters
    ----------
    identifier: int, optional
        Unique column identifier
    name: string, optional
        Column name
    data_type: string, optional
        String representation of the column data type.
    """
    self.identifier = identifier
    self.name = name
    self.data_type = data_type

  def __str__(self):
    return self.__repr__()

  def __repr__(self) -> str:
    """Human-readable string representation for the column.

    Returns
    -------
    string
    """
    if self.name is not None:
      name = self.name
    else:
      name = "unnamed_dataset"
    if self.data_type is not None:
      name += '(' + str(self.data_type) + ')'
    return name


class MutableDatasetRow(object):
  """Row in a Vizier DB dataset.

  Attributes
  ----------
  identifier: int
      Unique row identifier
  values : list(string)
      List of column values in the row
  caveats: list(bool), optional
      Optional flags indicating whether row cells are annotated
  """
  def __init__(self,
               dataset: "DatasetClient",
               values: List[Any],
               caveats: Optional[List[bool]] = None,
               row_caveat: bool = False,
               identifier: str = ""):
    """Initialize the row object.

    Parameters
    ----------
    identifier: int, optional
        Unique row identifier
    values : list(string)
        List of column values in the row
    caveats: list(bool), optional
        Optional flags indicating whether row cells are annotated
    """
    self.identifier = identifier
    self.values = values
    self.dataset = dataset
    if caveats is not None:
      self.caveats = caveats
    else:
      self.caveats = [False for col in values]
    self.row_caveat = row_caveat

  def __str__(self):
    return self.__repr__()

  def __repr__(self):
    return "<{}>{}".format(
      ", ".join(
        "{}{}".format(
          str(v),
          "*" if c else ""
        ) for v, c in zip(self.values, self.caveats)
      ),
      "*" if self.row_caveat else ""
    )

  def __getitem__(self, key):
    return self.get_value(key)

  def __setitem__(self, key, value):
    return self.set_value(key, value)

  def __contains__(self, key):
    return self.dataset.__contains__(key)

  def get_value(self, column: Union[int, str]) -> Any:
    """Get the row value for the given column.

    Parameters
    ----------
    column : int or string
        Column index, name, or label

    Returns
    -------
    string
    """
    col_index = self.dataset.column_index(column)
    return self.values[col_index]

  def set_value(self, column: Union[int, str], value: Any, comment: Optional[str] = None):
    """Set the row value for the given column.

    Parameters
    ----------
    column : int or string
        Column index, name, or label
    value : string
        New cell value
    keep_annotations: bool, optional
        Flag indicating whether to keep or clear the annotations that are
        associated with this cell
    """
    col_index = self.dataset.column_index(column)
    self.values[col_index] = assert_type(value, self.dataset.columns[col_index].data_type)
    if comment is not None:
      self.caveats[col_index] = True
    else:
      self.caveats[col_index] = False
    self.dataset.add_delta(
      id=VIZUAL_UPDATE_CELL,
      column=col_index,
      row=self.identifier,
      comment=comment,
      value=value
    )


class DatasetClient(object):
  """Client to interact with a Vizier dataset from within a Python workflow
  cell. Provides access to the columns and rows. Allows to insert and delete
  rows, and to update cell values.
  """
  def __init__(self,
               client: "VizierDBClient",
               dataset: Optional[Dict[str, Any]] = None,
               identifier: Optional[str] = None,
               existing_name: str = None):
    """Initialize the client for a given dataset.

    Raises ValueError if dataset columns or rows do not have unique
    identifiers.
    """
    self.client = client
    self.existing_name = existing_name
    self.history: List[Dict[str, Any]] = []

    if dataset is not None:
      self.columns = [
        DatasetColumn(
          identifier=idx,
          name=column["name"],
          data_type=column["type"]
        )
        for (idx, column) in enumerate(dataset["schema"])
      ]
      assert(identifier is not None)
      self.identifier: Optional[str] = identifier
      self._properties = dataset["properties"]

      rowids = dataset["prov"]
      data = dataset["data"]
      col_caveats = dataset["colTaint"]
      row_caveats = dataset["rowTaint"]
      if len(col_caveats) < len(data):
        col_caveats = col_caveats + [None] * (len(data) - len(col_caveats))
      if len(row_caveats) < len(data):
        row_caveats = row_caveats + [False] * (len(data) - len(row_caveats))

      row_data = list(zip(
        rowids,
        data,
        col_caveats,
        row_caveats
      ))


      self._rows = [
        MutableDatasetRow(
          dataset=self,
          identifier=identifier,
          values=[
            import_to_native_type(v, c.data_type)
            for (v, c) in zip(values, self.columns)
          ],
          caveats=caveats,
          row_caveat=row_caveat
        )
        for identifier, values, caveats, row_caveat in row_data
      ]
    else:
      self.identifier = None
      self.columns = list()
      self._properties = {}
      self._rows = list()

  def __getitem__(self, key):
    return self.get_column(key)

  def __str__(self):
    return self.__repr__()

  def __repr__(self):
    return "<{}> ({} rows)".format(
      ", ".join(col.__repr__() for col in self.columns),
      len(self.rows)
    )

  def __contains__(self, key):
    return self.get_column(key) is not None

  def add_delta(self, id: str, **varargs) -> None:
    self.history.append({"id": id, **varargs})

  def save(self, name: Optional[str] = None, use_deltas: bool = False):
    if self.client is None:
      raise ValueError("Client field unset.  Use `vizierdb.create_dataset()` or `vizierdb.update_dataset()` instead.")
    if name is None and self.existing_name is None:
      raise ValueError("This is a new dataset.  Use `ds.save(name = ...)` to specify a name.")
    if name is None:
      assert(self.existing_name is not None)
      self.client.update_dataset(
        name=self.existing_name, 
        dataset=self, 
        use_deltas=use_deltas
      )
    else:
      self.client.create_dataset(
        name=name, 
        dataset=self,
        use_deltas=use_deltas
      )
    self.existing_name = name

  @property
  def properties(self):
    """Get all dataset properties
    """
    return self._properties

  def column_index(self, column_id: Union[int, str]) -> int:
    """Get position of a given column in the dataset schema. The given
    column identifier could either be of type int (i.e., the index position
    of the column), or a string (either the column name or column label).
    If column_id is of type string it is first assumed to be a column name.
    Only if no column matches the column name or if multiple columns with
    the given name exist will the value of column_id be interpreted as a
    label.

    Raises ValueError if column_id does not reference an existing column in
    the dataset schema.
    """
    if isinstance(column_id, int):
      # Return column if it is a column index and withing the range of
      # dataset columns
      if column_id >= 0 and column_id < len(self.columns):
        return column_id
      raise ValueError('invalid column index \'' + str(column_id) + '\'')
    elif isinstance(column_id, str):
      # Get index for column that has a name that matches column_id. If
      # multiple matches are detected column_id will be interpreted as a
      # column label
      name_index = -1
      for i in range(len(self.columns)):
        col_name = self.columns[i].name
        if col_name is not None:
          if col_name.lower() == column_id.lower():
            if name_index == -1:
              name_index = i
            else:
              # Multiple columns with the same name exist. Signal that
              # no unique column was found by setting name_index to -1.
              name_index = -2
              break
      if name_index < 0:
        # Check whether column_id is a column label that is within the
        # range of the dataset schema
        label_index = collabel_2_index(column_id)
        if label_index > 0:
          if label_index <= len(self.columns):
            name_index = label_index - 1
      # Return index of column with matching name or label if there exists
      # a unique solution. Otherwise raise exception.
      if name_index >= 0:
        return name_index
      elif name_index == -1:
        raise ValueError('unknown column \'' + str(column_id) + '\'')
      else:
        raise ValueError('not a unique column name \'' + str(column_id) + '\'')
    else:
      raise ValueError('not a valid column index: ' + str(column_id))

  def delete_column(self, name: Any) -> None:
    """Delete column from the dataset.
    """
    # It is important to fetch the rows before the column is deleted.
    # Otherwise, the list of returned values per row will be missing the
    # value for the deleted columns (for Mimir datasets).
    ds_rows = self.rows
    col_index = self.column_index(name)
    # Delete column from schema
    del self.columns[col_index]
    # Delete all value for the deleted column
    for row in ds_rows:
      del row.values[col_index]
      del row.caveats[col_index]
    self.add_delta(id=VIZUAL_DELETE_COLUMN, column=col_index)

  def get_column(self, name: Any) -> Optional[DatasetColumn]:
    """Get the fist column in the dataset schema that matches the given
    name. If no column matches the given name None is returned.
    """
    for col in self.columns:
      if col.name == name:
        return col
    return None

  def insert_column(self,
                    name: str,
                    data_type: str = DATATYPE_VARCHAR,
                    position: Optional[int] = None
                    ) -> DatasetColumn:
    """Add a new column to the dataset schema.
    """
    if len(self.columns) > 0:
      idx = max(column.identifier for column in self.columns) + 1
    else:
      idx = 0
    column = DatasetColumn(name=name, data_type=data_type, identifier=idx)
    self.columns = list(self.columns)
    if position is not None:
      self.columns.insert(position, column)
      # Add a null value to each row for the new column
      for row in self.rows:
        row.values.insert(position, None)
        row.caveats.insert(position, False)
    else:
      self.columns.append(column)
      # Add a null value to each row for the new column
      for row in self.rows:
        row.values.append(None)
        row.caveats.append(False)
    self.add_delta(
      id=VIZUAL_INSERT_COLUMN, 
      name=name,
      position=position, 
      dataType=data_type,
    )
    return column

  def insert_row(self,
                 values: Optional[List[Any]] = None,
                 position: Optional[int] = None
                 ) -> MutableDatasetRow:
    """Add a new row to the dataset. Expects a list of string values, one
    for each of the columns.

    Raises ValueError if the length of the values list does not match the
    number of columns in the dataset.
    """
    import base64
    # Ensure that there is exactly one value for each column in the dataset
    if values is not None:
      if len(values) != len(self.columns):
        raise ValueError('invalid number of values for dataset schema')
      for v, col in zip(values, self.columns):
        assert_type(v, col.data_type, col.name)
      row = MutableDatasetRow(
        values=[v for v in values],
        dataset=self
      )
    else:
      # All values in the new row are set to the empty string by default.
      row = MutableDatasetRow(
        values=[None] * len(self.columns),
        dataset=self
      )
    if position is not None:
      self.rows.insert(position, row)
    else:
      self.rows.append(row)
    encoded_values: Optional[List[Any]] = None
    if values is not None:
      encoded_values = [
        export_from_native_type(value, col.data_type)
        for value, col in zip(values, self.columns)
      ]
    self.add_delta(
      id=VIZUAL_INSERT_ROW,
      position=position,
      values=encoded_values
    )
    return row

  def get_cell(self, column: Any, row: int) -> Any:
    """Get dataset value for specified cell.

    Raises ValueError if [column, row] does not reference an existing cell.
    """
    if row < 0 or row > len(self.rows):
      raise ValueError('unknown row \'' + str(row) + '\'')
    return self.rows[row].get_value(column)

  def move_column(self, name: str, position: int) -> None:
    """Move a column within a given dataset.

    Raises ValueError if no dataset with given identifier exists or if the
    specified column is unknown or the target position invalid.
    """
    # Get dataset. Raise exception if dataset is unknown
    # Make sure that position is a valid column index in the new dataset
    if position < 0 or position > len(self.columns):
      raise ValueError('invalid target position \'' + str(position) + '\'')
    # Get index position of column that is being moved
    source_idx = self.column_index(name)
    # No need to do anything if source position equals target position
    if source_idx != position:
      self.columns.insert(position, self.columns.pop(source_idx))
      for row in self.rows:
        row.values.insert(position, row.values.pop(source_idx))
        row.caveats.insert(position, row.caveats.pop(source_idx))
      self.add_delta(
        id=VIZUAL_MOVE_COLUMN,
        column=source_idx,
        position=position
      )

  @property
  def rows(self):
    """Fetch rows on demand.
    """
    return self._rows

  def to_bokeh(self, columns: Optional[List[str]] = None):
    """Convert the dataset to a bokeh ColumnDataSource
    """
    if columns is None:
      columns = [
        col.name if col.name is not None else "column_{}".format(col.identifier)
        for col in self.columns
      ]
    return ColumnDataSource({
      column.name:
        [row.get_value(
            column.identifier if column.identifier >= 0 else column.name
          ) for row in self.rows
         ]
      for column in self.columns
    })

  def show_map(self,
               lat_col: Any,
               lon_col: Any,
               label_col: Optional[Any] = None,
               center_lat: Optional[float] = None,
               center_lon: Optional[float] = None,
               zoom: int = 8,
               height: str = "500",
               map_provider: str = 'OSM'
               ) -> None:
    import numpy as np  # type: ignore[import]
    width = "100%"
    addrpts: List[Any] = list()
    lats = []
    lons = []
    for row in self.rows:
      lon, lat = float(row.get_value(lon_col)), float(row.get_value(lat_col))
      lats.append(lat)
      lons.append(lon)
      if map_provider == 'Google':
        addrpts.append({"lat": str(lat), "lng": str(lon)})
      elif map_provider == 'OSM':
        label = ''
        if label_col is not None:
          label = str(row.get_value(label_col))
        rowstr = '[' + str(lat) + ', ' + \
                       str(lon) + ', \'' + \
                       label + '\']'
        addrpts.append(rowstr)

    if center_lat is None:
      center_lat = cast(float, np.mean(lats))

    if center_lon is None:
      center_lon = cast(float, np.mean(lons))

    if map_provider == 'Google':
      import json
      from pycell.wrappers import GoogleMapClusterWrapper
      html = GoogleMapClusterWrapper().do_output(json.dumps(addrpts), center_lat, center_lon, zoom, width, height)
      self.client.show_html(html)
    elif map_provider == 'OSM':
      from pycell.wrappers import LeafletClusterWrapper
      html = LeafletClusterWrapper().do_output(addrpts, center_lat, center_lon, zoom, width, height)
      self.client.show_html(html)
    else:
      print("Unknown map provider: please specify: OSM or Google")

  def show_d3_plot(self, chart_type, keys=list(), labels=list(), labels_inner=list(), value_cols=list(), key_col='KEY', width=600, height=400, title='', subtitle='', legend_title='Legend', x_cols=list(), y_cols=list(), date_cols=list(), open_cols=list(), high_cols=list(), low_cols=list(), close_cols=list(), volume_cols=list(), key=None):
    from pycell.wrappers import D3ChartWrapper

    charttypes = ["table", "bar", "bar_stacked", "bar_horizontal", "bar_circular", "bar_cluster", "donut",
                   "polar", "heat_rad", "heat_table", "punch", "bubble", "candle", "line", "radar", "rose"]

    if chart_type not in charttypes:
      print(("Please specify a valid chart type: one of: " + str(charttypes)))
      return

    if not labels:
      labels = keys

    if not labels_inner:
      labels_inner = value_cols

    data = []
    for key_idx, label in enumerate(labels):
      entry = {}
      entry['key'] = label
      entry['values'] = []
      for idx, label_inner in enumerate(labels_inner):
        inner_entry = {}
        inner_entry['key'] = label_inner
        for row in self.rows:
          if len(keys) == 0 or (len(keys) >= key_idx and row.get_value(key_col) == keys[key_idx]):
            if value_cols and len(value_cols) >= idx:
              inner_entry['value'] = row.get_value(value_cols[idx])
            if x_cols and len(x_cols) >= idx:
              inner_entry['x'] = row.get_value(x_cols[idx])
            if y_cols and len(y_cols) >= idx:
              inner_entry['y'] = row.get_value(y_cols[idx])
            if date_cols and len(date_cols) >= idx:
              inner_entry['date'] = row.get_value(date_cols[idx])
            if open_cols and len(open_cols) >= idx:
              inner_entry['open'] = row.get_value(open_cols[idx])
            if high_cols and len(high_cols) >= idx:
              inner_entry['high'] = row.get_value(high_cols[idx])
            if low_cols and len(low_cols) >= idx:
              inner_entry['low'] = row.get_value(low_cols[idx])
            if close_cols and len(close_cols) >= idx:
              inner_entry['close'] = row.get_value(close_cols[idx])
            if volume_cols and len(volume_cols) >= idx:
              inner_entry['volume'] = row.get_value(volume_cols[idx])
              entry['values'].append(inner_entry)
        data.append(entry)

      if key is not None:
        data = data[data.index(key)]

      html = D3ChartWrapper().do_output(data=data, charttype=chart_type, width=str(width), height=str(height),
          title=title, subtitle=subtitle, legendtitle=legend_title)
      self.client.show_html(html)

  def show(self):
      self.client.show(self)

  def to_json(self, limit: Optional[int] = None):
    rows = self._rows
    if limit is not None:
      rows = rows[:limit]
    return {
      "schema": [
        {
          "name": col.name,
          "type": col.data_type
        } for col in self.columns
      ],
      "data": [
        [
          export_from_native_type(v, c.data_type, c.name)
          for (v, c) in zip(row.values, self.columns)
        ]
        for row in rows
      ],
      "prov": [row.identifier for row in rows],
      "colTaint": [row.caveats if hasattr(row, 'caveats') else [] for row in rows],
      "rowTaint": [row.row_caveat for row in rows],
      "properties": self._properties
    }


def collabel_2_index(label):
    """Convert a column label into a column index (based at 0), e.g., 'A'-> 1,
    'B' -> 2, ..., 'AA' -> 27, etc.

    Returns -1 if the given labe is not composed only of upper case letters
    A-Z.
    """
    # The following code is adopted from
    # https://stackoverflow.com/questions/7261936/convert-an-excel-or-spreadsheet-column-letter-to-its-number-in-pythonic-fashion
    num = 0
    for c in label:
        if ord('A') <= ord(c) <= ord('Z'):
            num = num * 26 + (ord(c) - ord('A')) + 1
        else:
            return -1
    return num


def import_to_native_type(value: Any, data_type: str) -> Any:
  # print("Parsing {} as {}".format(value, data_type))
  if value is None:
    return None
  elif data_type == DATATYPE_GEOMETRY:
    from shapely import wkt  # type: ignore[import]
    return wkt.loads(value)
  elif data_type == DATATYPE_BINARY:
    import base64
    return base64.b64decode(value.encode('utf-8'))
  elif data_type == DATATYPE_DATETIME:
    from datetime import datetime
    try:
      return datetime.fromisoformat(value)
    except ValueError as e:
      raise ValueError("Error importing datetime {}".format(value))
  elif data_type == DATATYPE_DATE:
    from datetime import date
    try:
      return date.fromisoformat(value)
    except ValueError as e:
      raise ValueError("Error importing date {}".format(value))
  elif data_type == DATATYPE_IMAGE:
    from PIL import Image
    import base64
    with io.BytesIO(base64.b64decode(value.encode('utf-8'))) as f:
      i = Image.open(f)
      i.getexif()
      return i
  elif data_type in ["double", "float", "real"]:
    return float(value)
  elif data_type in ["string", "varchar", "int", "long"]:
    return value
  else:
    #print("Unknown type: "+data_type)
    return value


def export_from_native_type(value: Any, data_type: str, context="the value") -> Any:
  assert_type(value, data_type, context)
  if value is None:
    return None
  elif data_type == DATATYPE_GEOMETRY:
    from shapely.geometry import shape  # type: ignore[import]
    return shape(value).wkt
  elif data_type == DATATYPE_BINARY:
    import base64
    return base64.b64encode(bytes(value)).decode('utf-8')
  elif data_type == DATATYPE_IMAGE:
    import base64
    from PIL.Image import Image  # type: ignore[import]
    if(issubclass(type(value), Image)):
      with io.BytesIO() as f:
        value.save(fp=f, format="PNG")
        value = base64.b64encode(f.getbuffer()).decode('utf-8')
    elif type(value) is bytes:
      value = base64.encodebytes(value).decode()
    return value
  elif data_type == DATATYPE_DATETIME or data_type == DATATYPE_DATE:
    return value.isoformat()
  elif data_type in ["double", "float", "real"]:
    if value == math.inf:
      return "infinity"
    elif value == -math.inf:
      return "-infinity"
    else:
      return value
  else:
    return value


PYTHON_TO_VIZIER_TYPES = {
  datetime.date: [DATATYPE_DATE],
  datetime.datetime: [DATATYPE_DATETIME],
  int: [DATATYPE_INT, DATATYPE_SHORT, DATATYPE_LONG, DATATYPE_REAL],
  float: [DATATYPE_REAL],
  str: [DATATYPE_VARCHAR],
  bytes: [DATATYPE_BINARY],
}
VIZIER_TYPES = set(
  v
  for p in PYTHON_TO_VIZIER_TYPES
  for v in PYTHON_TO_VIZIER_TYPES[p]
)
VIZIER_TO_PYTHON_TYPES = {
  v: [
    p
    for p in PYTHON_TO_VIZIER_TYPES
    if v in PYTHON_TO_VIZIER_TYPES[p]
  ]
  for v in VIZIER_TYPES
}


def assert_type(value: Any, data_type: str, context="the value") -> Any:
  if value is None:
    return value
  elif data_type in VIZIER_TO_PYTHON_TYPES:
    valid_python_types = VIZIER_TO_PYTHON_TYPES[data_type]
    for python_type in valid_python_types:
      if isinstance(value, python_type):
        return value
    raise ValueError(f"{context} ({value}) is a {type(value)} but should be a {data_type}")
    # Special-case handling for Geometry types
  elif data_type == DATATYPE_IMAGE:
    from PIL.Image import Image
    if(issubclass(type(value), Image)):
      return value
    else:
      raise ValueError(f"{context} ({value}) is a {type(value)} but not a PIL image")
  elif data_type == DATATYPE_GEOMETRY:
    if not hasattr(value, "__geo_interface__"):
      raise ValueError(f"{context} ({value}) is a {type(value)}, and not a type that supports the geometry interface")
    # Not sure how to validate this...
    return value
  else:
    return value
