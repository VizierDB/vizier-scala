# Copyright (C) 2017-2020 New York University,
#                         University at Buffalo,
#                         Illinois Institute of Technology.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


from typing import Dict, Any, IO, List, Tuple, Optional, Callable
import json
import sys
import ast
import astor  # type: ignore[import]
import inspect
import pandas
import os
import re
from datetime import datetime
from pycell.dataset import DatasetClient
from pycell.plugins import vizier_bokeh_render, vizier_matplotlib_render
from bokeh.models.layouts import LayoutDOM as BokehLayout  # type: ignore[import]
from matplotlib.figure import Figure as MatplotlibFigure  # type: ignore[import]
from matplotlib.axes import Axes as MatplotlibAxes  # type: ignore[import]

ARTIFACT_TYPE_DATASET  = "Dataset"
MIME_TYPE_DATASET      = "dataset/view"
ARTIFACT_TYPE_FUNCTION = "Function"
MIME_TYPE_PYTHON       = "application/python"

OUTPUT_TEXT    = "text/plain"
OUTPUT_HTML    = "text/html"
OUTPUT_DATASET = "dataset/view"


class Artifact(object):
  def __init__(self,
               name: str,
               artifact_type: str,
               mime_type: str,
               artifact_id: int
               ):
    self.name = name
    self.artifact_type = artifact_type
    self.artifact_id = artifact_id


class ArtifactProxy(object):
  def __init__(self,
               client: "VizierDBClient",
               artifact_name: str):
    self.__client = client
    self.__artifact = None
    self.__artifact_name = artifact_name

  def load_if_needed(self):
    if self.__artifact is None:
      self.__artifact = self.__client[self.__artifact_name]

  def __getattr__(self, name):
    self.load_if_needed()
    return self.__artifact.__getattribute__(name)

  def __setattr__(self, name, value):
    if name == "_ArtifactProxy__client" or name == "_ArtifactProxy__artifact" or name == "_ArtifactProxy__artifact_name":
      super(ArtifactProxy, self).__setattr__(name, value)
    else:
      self.load_if_needed()
      return self.__artifact.__setattribute__(name, value)

  def __call__(self, *args, **kwargs):
    self.load_if_needed()
    return self.__artifact.__call__(*args, **kwargs)

  @property
  def __class__(self):
    self.load_if_needed()
    return self.__artifact.__class__


class VizierDBClient(object):
  """The Vizier DB Client provides access to datasets that are identified by
  a unique name. The client is a wrapper around a given database state.
  """
  def __init__(self,
               artifacts: Dict[str, Artifact],
               source: str,
               raw_output: IO,
               project_id: str
               ):
    self.artifacts = artifacts
    self.source = source
    self.project_id = project_id
    self.raw_output = raw_output
    self.datasets = {}
    self.py_objects = {}

  def __getitem__(self, key: str) -> Any:
    key = key.lower()
    if key not in self.artifacts:
      raise ValueError("unknown artifact \'{}\'".format(key))
    artifact = self.artifacts[key]
    if artifact.artifact_type == ARTIFACT_TYPE_DATASET:
      return self.get_dataset(key)
    elif artifact.artifact_type == ARTIFACT_TYPE_FUNCTION:
      return self.get_module(key)
    else:
      raise ValueError("Unsupported artifact \'{}\' ({} [{}])".format(
                key,
                artifact.artifact_type,
                artifact.mime_type
              ))

  def vizier_request(self,
                     event: str,
                     has_response: bool = False,
                     **fields
                     ) -> Optional[Dict[str, Any]]:
    self.raw_output.write(json.dumps({
      "event": event,
      **fields
    }) + "\n")
    self.raw_output.flush()
    if has_response:
      response = sys.stdin.readline()
      return json.loads(response)

  def get_artifact_proxies(self) -> Dict[str, ArtifactProxy]:
    return {
      self.artifacts[artifact].name: 
        ArtifactProxy(client=self, artifact_name=self.artifacts[artifact].name)
      for artifact in self.artifacts
      if self.artifacts[artifact].artifact_type == ARTIFACT_TYPE_FUNCTION
    }

  def get_module(self, name: str) -> Any:
    name = name.lower()
    if name in self.py_objects:
      return self.py_objects[name]
    if name not in self.artifacts:
      raise ValueError("unknown module \'{}\'".format(name))
    if self.artifacts[name].artifact_type != ARTIFACT_TYPE_FUNCTION:
      raise ValueError("\'{}\' is not a module".format(name))
    response = self.vizier_request(
        event="get_blob",
        name=name,
        has_response=True
      )

    def output_exported(x):
      self.py_objects[name] = x
      return None

    def return_type(dt):
      def wrap(x):
        return x
      return wrap

    variables = {
      "return_type": return_type,
      "export": output_exported,
      "vizierdb": self
    }

    exec("@export\n" + response["data"], variables, variables)
    return self.py_objects[name]

  def get_dataset(self, name: str) -> DatasetClient:
    """Get dataset with given name.

    Raises ValueError if the specified dataset does not exist.
    """
    name = name.lower()
    if name in self.datasets:
      return self.datasets[name]
    if name not in self.artifacts:
      raise ValueError("unknown dataset \'{}\'".format(name))
    if self.artifacts[name].artifact_type != ARTIFACT_TYPE_DATASET:
      raise ValueError("\'{}\' is not a dataset".format(name))
    response = self.vizier_request(
      event="get_dataset",
      has_response=True,
      name=name
    )
    assert(response["event"] == "dataset")
    ds = DatasetClient(
      client=self,
      dataset=response["data"],
      identifier=response["artifactId"],
      existing_name=name
    )
    self.datasets[name] = ds
    return ds

  def create_dataset(self,
                     name: str,
                     dataset: DatasetClient,
                     backend_options: List[Tuple[str, str]] = []
                     ) -> None:
    """Save a new dataset in Vizier with given name.

    Raises ValueError if a dataset with given name already exist.
    """
    name = name.lower()
    if name in self.artifacts:
      raise ValueError('dataset \'{}\' already exists'.format(name))
    response = self.vizier_request("save_dataset",
      name=name,
      has_response=True,
      dataset=dataset.to_json()
    )
    dataset.identifier = response["artifactId"]
    dataset.name_in_backend = response["artifactId"]
    self.datasets[name] = dataset
    self.artifacts[name] = Artifact(name=name,
                                    artifact_type=ARTIFACT_TYPE_DATASET,
                                    mime_type=MIME_TYPE_DATASET,
                                    artifact_id=response["artifactId"]
                                    )

  def update_dataset(self,
                     name: str,
                     dataset: DatasetClient
                     ) -> DatasetClient:
    """Update a given dataset.

    Raises ValueError if the specified dataset does not exist.
    """
    name = name.lower()
    if name not in self.artifacts:
      raise ValueError('dataset \'{}\' already exists'.format(name))
    response = self.vizier_request("save_dataset",
      name=name,
      has_response=True,
      dataset=dataset.to_json
    )
    dataset.identifier = response["artifactId"]
    dataset.name_in_backend = response["artifactId"]
    self.datasets[name] = dataset
    self.artifacts[name] = Artifact(name,
                                    ARTIFACT_TYPE_DATASET,
                                    MIME_TYPE_DATASET
                                    )

  def drop_dataset(self, name: str) -> None:
    """Remove the dataset with the given name.

    Raises ValueError if no dataset with given name exist.
    """
    name = name.lower()
    if name not in self.artifacts:
      raise ValueError('dataset \'{}\' does not exist'.format(name))
    self.vizier_request("delete_artifact",
      name=name,
      has_response=False,
    )
    del self.artifacts[name]
    if name in self.datasets:
      del self.datasets[name]

  def new_dataset(self) -> DatasetClient:
    """Get a dataset client instance for a new dataset.
    """
    return DatasetClient(client=self)

  def rename_dataset(self, name: str, new_name: str) -> None:
    """Rename an existing dataset.

    Raises ValueError if a dataset with given name already exist.

    Raises ValueError if dataset with name does not exist or if dataset with
    new_name already exists.
    """
    name = name.lower()
    new_name = new_name.lower()
    if name not in self.artifacts:
      raise ValueError('dataset \'{}\' does not exist'.format(name))
    if new_name in self.artifacts:
      raise ValueError('dataset \'{}\' exists'.format(new_name.lower()))
    if not is_valid_name(new_name):
      raise ValueError('invalid dataset name \'{}\''.format(new_name))

    self.artifacts[new_name] = self.artifacts[name]
    del self.artifacts[name]
    if name in self.datasets:
      self.datasets[name].existing_name = new_name
      self.datasets[new_name] = self.datasets[name]
      del self.datasets[name]
    if name in self.py_objects:
      self.py_objects[new_name] = self.py_objects[name]
      del self.py_objects[name]

  def pycell_open(self,
                  file: str,
                  mode: str = 'r',
                  buffering: int = -1,
                  encoding: Optional[str] = None,
                  errors: Any = None,
                  newline: Optional[str] = None,
                  closefd: Optional[bool] = True,
                  opener: Optional[Any] = None
                  ) -> IO:
    print("***File access may not be reproducible because filesystem resources are transient***")
    return open(file, mode, buffering, encoding, errors, newline, closefd, opener)

  def show(self,
           value: Any,
           mime_type: Optional[str] = None,
           force_to_string: bool = False
           ) -> None:
    if force_to_string:
      value = str(value)
      if mime_type is None:
        mime_type = OUTPUT_TEXT
    if mime_type is None:
      if type(value) is str:
        mime_type = OUTPUT_TEXT
      elif type(value) is DatasetClient:
        value = json.dumps({
          "name": value.existing_name,
          "artifactId": value.identifier,
          "projectId": self.project_id,
          "offset": 0,
          "dataCache": value.to_json(limit=20),
          "rowCount": len(value.rows),
          "created": datetime.now().astimezone().isoformat()
        })
        mime_type = OUTPUT_DATASET
      elif issubclass(type(value), BokehLayout):
        value = vizier_bokeh_render(value)
        mime_type = OUTPUT_HTML
      elif issubclass(type(value), MatplotlibFigure):
        value = vizier_matplotlib_render(value)
        mime_type = OUTPUT_HTML
      elif issubclass(type(value), MatplotlibAxes):
        value = vizier_matplotlib_render(value.get_figure())
        mime_type = OUTPUT_HTML
      else:
        repr_html = getattr(value, "_repr_html_", None)
        if repr_html is not None:
          value = str(repr_html())
          mime_type = OUTPUT_HTML
        else:
          raise ValueError("Don't know how to show {}.\nTry show(value, force_to_string = True)".format(value))
    else:
      value = str(value)

    self.vizier_request("message",
      stream="stdout",
      content=value,
      mimeType=mime_type,
      has_response=False
    )

  def show_html(self, value):
    self.show(value, mime_type=OUTPUT_HTML)

  def export_module(self, exp: Any, name_override: Optional[str] = None, return_type: Any = None):
    if name_override is not None:
      exp_name = name_override
    elif inspect.isclass(exp):
      exp_name = exp.__name__
    elif callable(exp):
      exp_name = exp.__name__
    else:
      # If its a variable we grab the original name from the stack
      lcls = inspect.stack()[1][0].f_locals
      for name in lcls:
        if lcls[name] == exp:
          exp_name = name
    src_ast = ast.parse(self.source)
    analyzer = Analyzer(exp_name)
    analyzer.visit(src_ast)
    src = analyzer.get_Source()
    if return_type is not None:
      if type(return_type) is type:
        if return_type is int:
          return_type = "pyspark_types.IntegerType()"
        if return_type is str:
          return_type = "pyspark_types.StringType()"
        if return_type is float:
          return_type = "pyspark_types.FloatType()"
        if return_type is bool:
          return_type = "pyspark_types.BoolType()"
        else:
          return_type = str(return_type)
    src = "@return_type({})\n{}".format(return_type, src)

    if exp_name in self.artifacts:
      if name_override is None:
        raise ValueError("An artifact named '{}' already exists.  Try vizierdb.export_module(exp, name_override=\"{}\")".format(exp_name, exp_name))

    response = self.vizier_request("save_artifact",
        name=exp_name,
        data=src,
        mimeType=MIME_TYPE_PYTHON,
        artifactType=ARTIFACT_TYPE_FUNCTION,
        has_response=True
      )

    self.artifacts[exp_name] = Artifact(
      name=exp_name,
      artifact_type=ARTIFACT_TYPE_FUNCTION,
      mime_type=MIME_TYPE_PYTHON,
      artifact_id=response["artifactId"]
    )
    if exp_name in self.datasets:
      del self.datasets[exp_name]

  def get_data_frame(self, name: str) -> pandas.DataFrame:
    """Get dataset with given name as a pandas dataframe.

    Raises ValueError if the specified dataset does not exist.
    """
    import pyarrow as pa  # type: ignore
    from pyspark.rdd import _load_from_socket  # type: ignore
    from pyspark.sql.pandas.serializers import ArrowCollectSerializer  # type: ignore
    name = name.lower()
    if name not in self.artifacts:
      raise ValueError("Unknown artifact: '{}'".format(name))
    if self.artifacts[name].artifact_type != ARTIFACT_TYPE_DATASET:
      raise ValueError("Artifact '{}' is not a dataset".format(name))

    response = self.vizier_request("get_data_frame",
      name=name,
      includeUncertainty=True,
      has_response=True
    )
    results = list(_load_from_socket((response['port'], response['secret']), ArrowCollectSerializer()))
    batches = results[:-1]
    batch_order = results[-1]
    ordered_batches = [batches[i] for i in batch_order]
    table = pa.Table.from_batches(ordered_batches)
    return table.to_pandas()

  def dataset_from_s3(self,
                      bucket: str,
                      folder: str,
                      file: str,
                      line_extracter: Callable[["re.Match", str], Tuple[str, str]]
                        = lambda rematch, line: ('col0', line),
                      additional_col_gen: Optional[Callable[["re.Match", str], Tuple[str, str]]] = None,
                      delimeter: str = ",",
                      line_delimeter: str = "\n"
                      ) -> Optional[DatasetClient]:
    from minio import Minio  # type: ignore[import]
    from minio.error import ResponseError  # type: ignore[import]
    from minio.select.errors import SelectCRCValidationError  # type: ignore[import]
    client = Minio(os.environ.get('S3A_ENDPOINT', 's3.vizier.app'),
                   access_key=os.environ.get('AWS_ACCESS_KEY_ID', "----------------------"),
                   secret_key=os.environ.get('AWS_SECRET_ACCESS_KEY', "---------------------------"))
    try:
      import io

      ds = self.new_dataset()
      objects = client.list_objects_v2(bucket, prefix=folder, recursive=True)

      subObjs = []
      for obj in objects:
        subObjs.append(obj)

      for logObj in subObjs:
        log = logObj.object_name
        result = re.match(file, log)
        # Check file name suffix is .log
        if result:
          data = client.get_object(bucket, log)
          rdat = io.StringIO()
          for d in data.stream(100 * 1024):
            rdat.write(str(d.decode('utf-8')))
          lines = rdat.getvalue().split(line_delimeter)

          entry: Dict[str, str] = {}
          if additional_col_gen is not None:
            entry = dict({additional_col_gen(result, line) for line in lines})
          line_entries = dict({line_extracter(result, line) for line in lines})
          entry = {**entry, **line_entries}
          # The following line seems to be OK only because
          # the preceding line is a { ** } constructor.
          # I have absolutely no clue why the following works,
          # otherwise.  Either way, let's shut mypy up for now
          # -OK
          entry.pop(None, None)  # type: ignore

          # Append unknown dictionary keys to the list
          for attr in list(set(entry.keys()) - set([col.name for col in ds.columns])):
            ds.insert_column(attr)

          # Make sure the record is in the right order
          row = [
            entry.get(col.name, None)
            for col in ds.columns
          ]

          ds.insert_row(values=row)

          return ds

    except SelectCRCValidationError:
      return None
      pass
    except ResponseError:
      return None
      pass


class Analyzer(ast.NodeVisitor):
  def __init__(self, name):
    self.name = name
    self.source = ''
    # track context name and set of names marked as `global`
    self.context = [('global', ())]

  def visit_FunctionDef(self, node):
    self.context.append(('function', set()))
    if node.name == self.name:
      self.source = astor.to_source(node)
      self.generic_visit(node)
    self.context.pop()

  # treat coroutines the same way
  visit_AsyncFunctionDef = visit_FunctionDef

  def visit_Assign(self, node):
    self.context.append(('assignment', set()))
    target = node.targets[0]
    if target.id == self.name:
      self.source = astor.to_source(node.value)
      self.generic_visit(target)
    self.context.pop()

  def visit_ClassDef(self, node):
    self.context.append(('class', ()))
    if node.name == self.name:
      self.source = astor.to_source(node)
      self.generic_visit(node)
    self.context.pop()

  def visit_Lambda(self, node):
    # lambdas are just functions, albeit with no statements, so no assignments
    self.context.append(('function', ()))
    self.generic_visit(node)
    self.context.pop()

  def visit_Global(self, node):
    assert self.context[-1][0] == 'function'
    self.context[-1][1].update(node.names)

  def visit_Name(self, node):
    ctx, g = self.context[-1]
    # if node.id == self.name and (ctx == 'global' or node.id in g):
    # print('exported {} at line {} of {}'.format(node.id, node.lineno, self.source))

  def get_Source(self):
    return self.source


def is_valid_name(name: str) -> bool:
  """Returns Ture if a given string represents a valid name (e.g., for a
  dataset). Valid names contain only letters, digits, hyphen, underline, or
  blanl. A valid name has to contain at least one digit or letter.
  """
  allnums = 0
  for c in name:
    if c.isalnum():
      allnums += 1
    elif c not in ['_', '-', ' ']:
      return False
  return (allnums > 0)
