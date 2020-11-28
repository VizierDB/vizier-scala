package info.vizierdb.commands.python

import scala.collection.JavaConversions._
import scala.collection.mutable.Buffer
import java.util.ArrayList
import play.api.libs.json._
import org.apache.spark.sql.types.StructField
import info.vizierdb.catalog.Artifact
import org.mimirdb.api.request.DataContainer
import org.mimirdb.api.request.QueryMimirRequest
import org.mimirdb.api.request.LoadInlineRequest
import info.vizierdb.types._
import org.mimirdb.api.request.QueryTableRequest
import org.mimirdb.spark.SparkPrimitive
import org.python.core.{ PyObject, PyString, PyInteger }
import com.typesafe.scalalogging.LazyLogging


case class PythonDataset(
  client: PythonClient
)
  extends LazyLogging
{
  var schema: Buffer[PythonDatasetColumn] = Buffer.empty
  var artifactId: Identifier = -1
  var nameInBackend: Option[String] = None
  var properties: Map[String,JsValue] = Map.empty
  var rows: ArrayList[PythonDatasetRow] = new ArrayList()
  var name: Option[String] = None

  def populateFrom(artifactName: String, artifact: Artifact): PythonDataset =
  {
    var contents: DataContainer = 
      artifact.getDataset( includeUncertainty = true )
    schema = 
      Buffer(
        contents.schema.map { 
          PythonDatasetColumn(_)
        }:_*
      )
    artifactId = artifact.id
    nameInBackend = Some(artifact.nameInBackend)
    properties = contents.properties
    name = Some(artifactName)
    rows = 
      new ArrayList(
        contents.data
                .zip(contents.colTaint)
                .zip(contents.rowTaint)
                .map { case ((row, caveats), rowCaveat) => 
                   PythonDatasetRow(
                     Buffer(row:_*), 
                     Buffer(caveats:_*), 
                     rowCaveat
                   )
                }
      )
    return this
  }

  override def toString =
    s"<${schema.map { _.toString }.mkString(", ")}> (${rows.size} rows)"

  // See https://www.javadoc.io/doc/org.python/jython-standalone/latest/org/python/core/PyObject.html
  // for a list of these special __python__ functions.

  def __getitem__(key: String): Any =
    return get_column(key)

  def __sub__(key: String): Any =
    key match {
      case "rows" => return rows
      case "properties" => return properties
      case _ => throw new IllegalArgumentException(s"Invalid field '$key'")
    }

  def get_column(key: String): Any =
    get_column(key, false)

  def get_column(key: String, caseSensitive: Boolean): Any =
    schema.find { sch => 
      if(caseSensitive){ sch.name.equals(key) }
      else { sch.name.equalsIgnoreCase(key) }
    } match {
      case Some(col) => col
      case None => null
    }

  def column_index(column_id: Any): Integer =
  {
    column_id match {
      case s:String => 
        schema.zipWithIndex
              .find { _._1.name.equalsIgnoreCase(s) }
              .map { case (col, idx: Int) => new Integer(idx) }
              .getOrElse { null }
      case i:Integer => i
      case _ => 
        throw new IllegalArgumentException(s"Invalid index: $column_id")
    }
  }

  def delete_column(column_id: Any)
  {
    val idx = column_index(column_id)
    schema.remove(idx)
    for(row <- rows){
      row.fields.remove(idx)
      row.caveats.remove(idx)
    }
  }

  def insert_column(name: String, data_type: String): Unit =
    insert_column(name, data_type, null)
  def insert_column(name: String, data_type: String, position: Integer): Unit =
  {
    val col = PythonDatasetColumn(StructField(name, org.mimirdb.spark.Schema.decodeType(data_type)))
    Option(position) match {
      case None => {
        schema.append(col)
        for(row <- rows){
          row.fields.append(null)
          row.caveats.append(false)
        }
      }
      case Some(actualPosition) => {
        schema.insert(actualPosition, col)
        for(row <- rows){
          row.fields.insert(actualPosition, null)
          row.caveats.insert(actualPosition, false)
        }
      }
    }
  }

  def save(): Any = save(null)
  def save(newName: String): Unit =
  {
    val actualName = 
      Option(newName)
          .orElse { name }
          .getOrElse { 
            throw new IllegalArgumentException("This is a new dataset.  Use `ds.save(name = ...)` to specify a name.")
          }
    val (newNameInBackend, newArtifactId) = 
      client.context.outputDataset(actualName)
    LoadInlineRequest(
      schema = schema.map { _.field },
      data = rows.map { _.toJson },
      dependencies = None,//Some(nameInBackend.toSeq),
      resultName = Some(newNameInBackend),
      properties = Some(properties),
      humanReadableName = Some(actualName)
    ).handle
    nameInBackend = Some(newNameInBackend)
    artifactId = newArtifactId
  }

  case class PythonDatasetColumn(field: StructField)
  {
    def name = field.name
    def data_type = org.mimirdb.spark.Schema.encodeType(field.dataType)

    override def toString = s"$name($data_type)"
    def encodeJson(data: Any) = 
      SparkPrimitive.encode(data, field.dataType)
  }

  case class PythonDatasetRow(fields: Buffer[Any], caveats: Buffer[Boolean], hasCaveat: Boolean)
  {
    def toJson = 
      fields.zip(schema)
            .map { case (data, col) => col.encodeJson(data) }
    override def toString =
      ("<"+
        fields.zip(caveats)
              .map { case (data, caveat) => 
                        Option(data).map { _.toString}.getOrElse { "null" } +
                          (if(caveat){"*"}else{""}) }
              .mkString(", ") +
        ">"+(if(hasCaveat){"*"}else{""}))
  }

/*



    def insert_row(self, 
        values: Optional[List[Any]] = None, 
        position: Optional[int] = None
    ) -> DatasetRow:
        """Add a new row to the dataset. Expects a list of string values, one
        for each of the columns.

        Raises ValueError if the length of the values list does not match the
        number of columns in the dataset.

        Parameters
        ----------
        values: list(string), optional
            List of column values. Use empty string if no values are given
        position: int, optional
            Position where row is inserted. If None, the new row is appended to
            the list of dataset rows.

        Returns
        -------
        DatasetRow
        """
        # Ensure that there is exactly one value for each column in the dataset
        if not values is None:
            if len(values) != len(self.columns):
                raise ValueError('invalid number of values for dataset schema')
            row = MutableDatasetRow(
                values=[str(v) for v in values],
                dataset=self
            )
        else:
            # All values in the new row are set to the empty string by default.
            row = MutableDatasetRow(
                values = [None] * len(self.columns),
                dataset=self
            )
        if not position is None:
            self.rows.insert(position, row)
        else:
            self.rows.append(row)
        return row

    def get_cell(self, column, row):
        """Get dataset value for specified cell.

        Raises ValueError if [column, row] does not reference an existing cell.

        Parameters
        ----------
        column : int or string
            Column identifier
        row : int
            Row index

        Returns
        -------
        string
        """
        if row < 0 or row > len(self.rows):
            raise ValueError('unknown row \'' + str(row) + '\'')
        return self.rows[row].get_value(column)

    def move_column(self, name, position):
        """Move a column within a given dataset.

        Raises ValueError if no dataset with given identifier exists or if the
        specified column is unknown or the target position invalid.

        Parameters
        ----------
        name: string or int
            Name or index position of the new column
        position: int
            Target position for the column
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

    def to_bokeh(self, columns = None):
        """Convert the dataset to a bokeh ColumnDataSource

        Parameters
        ----------
        columns: list(string or int) (optional)
            The columns to include.  (default: All columns)

        Returns
        -------
        bokeh.models.sources.ColumnDataSource  
        """

        if columns is None:
            columns = self.columns
        return ColumnDataSource(dict([
            (
                column.name, 
                [ row.get_value(column.identifier if column.identifier >= 0 else column.name) for row in self.rows ]
            )
            for column in self.columns
        ]))
        
    def show_map(self, lat_col, lon_col, label_col=None, center_lat=None, center_lon=None, zoom=8, height="500", map_provider='OSM'):
        import numpy as np # type: ignore[import]
        width="100%"
        addrpts = list()
        lats = []
        lons = []
        for row in self.rows:
            lon, lat = float(row.get_value(lon_col)), float(row.get_value(lat_col))  
            lats.append(lat) 
            lons.append(lon)
            if map_provider == 'Google':
                addrpts.append({"lat":str(lat), "lng":str(lon)})
            elif map_provider == 'OSM':
                label = ''
                if not label_col is None:
                    label = str(row.get_value(label_col))
                rowstr = '[' + str(lat) + ', ' + \
                             str(lon) + ', \'' + \
                             label + '\']'
                addrpts.append(rowstr)
                
        if center_lat is None:
            center_lat = np.mean(lats)
            
        if center_lon is None:
            center_lon = np.mean(lons)
        
        if map_provider == 'Google':
            import json
            from vizier.engine.packages.pycell.packages.wrappers import GoogleMapClusterWrapper
            html = GoogleMapClusterWrapper().do_output(json.dumps(addrpts), center_lat, center_lon, zoom, width, height) 
            self.client.show_html(html)
        elif map_provider == 'OSM':  
            from vizier.engine.packages.pycell.packages.wrappers import LeafletClusterWrapper
            html = LeafletClusterWrapper().do_output(addrpts, center_lat, center_lon, zoom, width, height) 
            self.client.show_html(html)
        else:
            print("Unknown map provider: please specify: OSM or Google")

    def show_d3_plot(self, chart_type, keys=list(), labels=list(), labels_inner=list(), value_cols=list(), key_col='KEY', width=600, height=400, title='', subtitle='', legend_title='Legend', x_cols=list(), y_cols=list(), date_cols=list(), open_cols=list(), high_cols=list(), low_cols=list(), close_cols=list(), volume_cols=list(), key=None):
        from vizier.engine.packages.pycell.packages.wrappers import D3ChartWrapper

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
            data=data[data.index(key)]    
            
        html = D3ChartWrapper().do_output(data=data, charttype=chart_type, width=str(width), height=str(height), 
            title=title, subtitle=subtitle, legendtitle=legend_title)
        self.client.show_html(html)

    def show(self):
        self.client.show(self)
        
class MutableDatasetRow(DatasetRow):
    """Row in a Vizier DB dataset.

    Attributes
    ----------
    identifier: int
        Unique row identifier
    values : list(string)
        List of column values in the row
    """
    def __init__(self, 
        identifier: Optional[str] = None, 
        values: Optional[List[Any]] = None, 
        dataset: Optional[DatasetClient] = None
        ):
        """Initialize the row object.

        Parameters
        ----------
        identifier: int, optional
            Unique row identifier
        values : list(string), optional
            List of column values in the row
        dataset : vizier.datastore.client.DatasetClient, optional
            Reference to dataset that contains the row
        """
        identifier = "-1" if identifier is None else identifier
        super(MutableDatasetRow, self).__init__(
            identifier=identifier,
            values=values
        )
        self.dataset = dataset

    def __getitem__(self, key):
        return self.get_value(key)

    def annotations(self, column):
        """Get annotation object for given row cell.

        Parameters
        ----------
        column : int or string
            Column index, name, or label

        Returns
        -------
        vizier.engine.packages.pycell.client.ObjectMetadataSet
        """
        col_index = self.dataset.column_index(column)
        column_id = self.dataset.columns[col_index].identifier
        return ObjectMetadataSet(
            annotations=self.dataset.annotations.for_cell(
                column_id=column_id,
                row_id=self.identifier
            )
        )

    def get_value(self, column):
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

    def set_value(self, column, value, clear_annotations=False):
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
        self.values[col_index] = value
        if clear_annotations:
            self.dataset.annotations.clear_cell(
                column_id=self.dataset.columns[col_index].identifier,
                row_id=self.identifier
            )


class ObjectMetadataSet(object):
    """Query annotations for a dataset resource."""
    def __init__(self, annotations):
        """initialize the list of resource annotations.

        Parameters
        ----------
        annotations: list(vizier.datastore.annotation.base.DatasetAnnotation)
            List of resource annotations
        """
        self.annotations = annotations

    def contains(self, key):
        """Test if an annotation with given key exists for the resource.

        Parameters
        ----------
        key: string
            Annotation key

        Returns
        -------
        bool
        """
        return not self.find_one(key) is None

    def count(self):
        """Number of annotations for this resource.

        Returns
        -------
        int
        """
        return len(self.annotations)

    def find_all(self, key):
        """Get a list with all annotations that have a given key. Returns an
        empty list if no annotation with the given key exists.

        Parameters
        ----------
        key: string
            Key value for new annotation

        Returns
        -------
        list(vizier.datastore.annotation.base.DatasetAnnotation)
        """
        result = list()
        for anno in self.annotations:
            if anno.key == key:
                result.append(anno)
        return result

    def find_one(self, key):
        """Find the first annotation with given key. Returns None if no
        annotation with the given key exists.

        Parameters
        ----------
        key: string
            Key value for new annotation

        Returns
        -------
        vizier.datastore.annotation.base.DatasetAnnotation
        """
        for anno in self.annotations:
            if anno.key == key:
                return anno

    def keys(self):
        """List of existing annotation keys for the object.

        Returns
        -------
        list(string)
        """
        result = set()
        for anno in self.annotations:
            result.add(anno.key)
        return list(result)

*/
}