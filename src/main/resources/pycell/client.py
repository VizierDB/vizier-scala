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


from typing import Dict, Any, IO, List, Tuple, Optional
import json
import sys
from pycell.dataset import DatasetClient

ARTIFACT_TYPE_DATASET = "Dataset"
MIME_TYPE_DATASET     = "dataset/view"
ARTIFACT_TYPE_PYTHON  = "Function"
MIME_TYPE_PYTHON      = "application/python"


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
    self.raw_output = IO
    self.datasets = {}

  def __getitem__(self, key: str) -> DatasetClient:
    return self.get_dataset(key)

  def vizier_request(self,
                     event: str,
                     has_response: bool = False,
                     **fields
                     ) -> Optional[Dict[str, Any]]:
    self.raw_output.print(json.dumps({
      "event": event,
      **fields
    }))
    if has_response:
      response = sys.stdin.readline()
      return json.loads(response)

  def get_dataset(self, name: str) -> None:
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
      dataset=dataset.to_json
    )
    dataset.identifier = response["artifactId"]
    dataset.name_in_backend = response["artifactId"]
    self.datasets[name] = dataset
    self.artifacts[name] = Artifact(name,
                                    ARTIFACT_TYPE_DATASET,
                                    MIME_TYPE_DATASET
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
    self.vizier_request("delete_artifact",
      name=name.lower(),
      has_response=False,
    )
    del self.artifacts[name]
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
    self.datasets[new_name] = self.datasets[name]
    del self.artifacts[name]
    del self.datasets[name]

#     def export_module_decorator(self, original_func):
#         def wrapper(*args, **kwargs):
#             self.read.add(original_func.__name__)
#             result = original_func(*args, **kwargs)
#             return result
#         return wrapper
    
#     def wrap_variable(self, original_variable, name):
#         self.read.add(name)
#         return original_variable
    
#     def export_module(self, exp: Any, return_type: Any = None):
#         if inspect.isclass(exp):
#             exp_name = exp.__name__
#         elif callable(exp):
#             exp_name = exp.__name__
#         else:
#             # If its a variable we grab the original name from the stack 
#             lcls = inspect.stack()[1][0].f_locals
#             for name in lcls:
#                 if lcls[name] == exp:
#                     exp_name = name
#         src_ast = ast.parse(self.source)
#         analyzer = Analyzer(exp_name)
#         analyzer.visit(src_ast)
#         src = analyzer.get_Source()
#         if return_type is not None:
#             if type(return_type) is type:
#                 if return_type is int:
#                     return_type = "pyspark_types.IntegerType()"
#                 if return_type is str:
#                     return_type = "pyspark_types.StringType()"
#                 if return_type is float:
#                     return_type = "pyspark_types.FloatType()"
#                 if return_type is bool:
#                     return_type = "pyspark_types.BoolType()"
#             else:
#                 return_type = str(return_type)
#             src = "@return_type({})\n{}".format(return_type, src)
        
#         identifier = self.datastore.create_object(value=src,
#                                                   obj_type=ARTIFACT_TYPE_PYTHON)
#         descriptor = ArtifactDescriptor(identifier, exp_name, ARTIFACT_TYPE_PYTHON)
#         self.dataobjects[exp_name] = descriptor
#         self.write.add(exp_name)


        
#     def get_dataset_frame(self, name):
#         """Get dataset with given name as a pandas dataframe.

#         Raises ValueError if the specified dataset does not exist.

#         Parameters
#         ----------
#         name : string
#             Unique dataset name

#         Returns
#         -------
#         pandas.Dataframe
#         """
#         # Make sure to record access idependently of whether the dataset exists
#         # or not. Ignore read access to datasets that have been written.
#         if not name.lower() in self.write:
#             self.read.add(name.lower())
#         # Get identifier for the dataset with the given name. Will raise an
#         # exception if the name is unknown
#         identifier = self.get_dataset_identifier(name)
#         # Read dataset from datastore and return it.
#         dataset_frame = self.datastore.get_dataset_frame(identifier)
#         if dataset_frame is None:
#             raise ValueError('unknown dataset \'' + identifier + '\'')
#         return dataset_frame
        
#     def dataset_from_s3(self, 
#         bucket: str, 
#         folder: str, 
#         file: str, 
#         line_extracter: Callable[[re.Match, str], Tuple[str, str]]
#           = lambda rematch, line: ('col0', line), 
#         additional_col_gen: Optional[Callable[[re.Match, str], Tuple[str, str]]] = None, 
#         delimeter: str = ",", 
#         line_delimeter: str = "\n"
#     ) -> Optional[DatasetClient]:
#         client = Minio(os.environ.get('S3A_ENDPOINT', 's3.vizier.app'),
#                access_key=os.environ.get('AWS_ACCESS_KEY_ID', "----------------------"),
#                secret_key=os.environ.get('AWS_SECRET_ACCESS_KEY', "---------------------------"))
        
#         try:
#             import io
            
#             ds = self.new_dataset()
#             objects = client.list_objects_v2(bucket, prefix=folder, recursive=True)
            
#             subObjs = [] 
#             for obj in objects:
#                 subObjs.append(obj)
                   
#             for logObj in subObjs:
#                 log = logObj.object_name 
#                 result = re.match(file, log)
#                 # Check file name suffix is .log
#                 if result: 
#                     data = client.get_object(bucket, log)
#                     rdat = io.StringIO()
#                     for d in data.stream(100*1024):
#                         rdat.write(str(d.decode('utf-8')))
#                     lines = rdat.getvalue().split(line_delimeter)
                    
#                     entry: Dict[str,str] = {}
#                     if additional_col_gen is not None:
#                         entry = dict({ additional_col_gen(result, line) for line  in lines })
#                     line_entries =  dict({ line_extracter(result,line) for line  in lines })
#                     entry = {**entry , **line_entries}
#                     # The following line seems to be OK only because
#                     # the preceding line is a { ** } constructor.  
#                     # I have absolutely no clue why the following works,
#                     # otherwise.  Either way, let's shut mypy up for now
#                     # -OK
#                     entry.pop(None, None) # type: ignore
                    
#                     # Append unknown dictionary keys to the list
#                     for attr in list(set(entry.keys())-set([col.name for col in ds.columns])):
#                         ds.insert_column(attr)
                            
#                     # Make sure the record is in the right order
#                     row = [
#                         entry.get(col.name, None)
#                         for col in ds.columns
#                     ]
                    
#                     ds.insert_row(values=row)
        
#             return ds
                    
#         except SelectCRCValidationError:
#             return None
#             pass
#         except ResponseError:
#             return None
#             pass
    
#     def pycell_open(self, file, mode='r', buffering=-1, encoding=None, errors=None, newline=None, closefd=True, opener=None):
#         print("***File access may not be reproducible because filesystem resources are transient***")
#         return open(file, mode, buffering, encoding, errors, newline, closefd, opener)    
      
#     def set_output_format(self, mime_type):
#         self.output_format = mime_type

#     def show(self, value, mime_type = None, force_to_string = True):
#         if not issubclass(type(value), OutputObject):
#             if mime_type is not None:
#                 value = OutputObject(value = value, type = mime_type)
#             elif type(value) is str:
#                 value = TextOutput(value = value)
#             elif type(value) is DatasetClient:
#                 from vizier.api.webservice import server
#                 ds_handle = server.api.datasets.get_dataset(
#                                 project_id=self.project_id,
#                                 dataset_id=value.dataset.identifier,
#                                 offset=0,
#                                 limit=10
#                             )
#                 value = DatasetOutput(ds_handle)
#             elif issubclass(type(value), BokehLayout):
#                 value = vizier_bokeh_render(value)
#                 value = HtmlOutput(value = value)
#             elif issubclass(type(value), MatplotlibFigure):
#                 value = HtmlOutput(value = vizier_matplotlib_render(value))
#             elif issubclass(type(value), MatplotlibAxes):
#                 value = HtmlOutput(value = vizier_matplotlib_render(value.get_figure()))
#             else:
#                 repr_html = getattr(value, "_repr_html_", None)
#                 if repr_html is not None:
#                     value = HtmlOutput(str(repr_html()))
#                 elif force_to_string:
#                     value = TextOutput(value = str(value))
#                 else:
#                     return
#         self.stdout.append(value)

#     def show_html(self, value):
#         self.show(HtmlOutput(value))
    
# class Analyzer(ast.NodeVisitor):
#     def __init__(self, name):
#         self.name = name
#         self.source = ''
#         # track context name and set of names marked as `global`
#         self.context = [('global', ())]

#     def visit_FunctionDef(self, node):
#         self.context.append(('function', set()))
#         if node.name == self.name:
#             self.source = "@vizierdb.export_module_decorator\n" + astor.to_source(node)
#             self.generic_visit(node)
#         self.context.pop()

#     # treat coroutines the same way
#     visit_AsyncFunctionDef = visit_FunctionDef

#     def visit_Assign(self, node):
#         self.context.append(('assignment', set()))
#         target = node.targets[0]
#         if target.id == self.name:
#             self.source = "{} = vizierdb.wrap_variable({}, '{}')".format( self.name, astor.to_source(node.value), self.name)
#             self.generic_visit(target)
#         self.context.pop()
        
#     def visit_ClassDef(self, node):
#         self.context.append(('class', ()))
#         if node.name == self.name:
#             self.source = "@vizierdb.export_module_decorator\n" + astor.to_source(node)
#             self.generic_visit(node)
#         self.context.pop()

#     def visit_Lambda(self, node):
#         # lambdas are just functions, albeit with no statements, so no assignments
#         self.context.append(('function', ()))
#         self.generic_visit(node)
#         self.context.pop()

#     def visit_Global(self, node):
#         assert self.context[-1][0] == 'function'
#         self.context[-1][1].update(node.names)

#     def visit_Name(self, node):
#         ctx, g = self.context[-1]
#         if node.id == self.name and (ctx == 'global' or node.id in g):
#             print('exported {} at line {} of {}'.format(node.id, node.lineno, self.source))
            
#     def get_Source(self):
#         return self.source


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
