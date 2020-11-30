from typing import Dict, Any, IO


class Artifact(object):
    def __init__(self,
                 name: str,
                 artifact_type: str,
                 name_in_backend: str,
                 file: str
                 ):
        self.name = name
        self.artifact_type = artifact_type
        self.name_in_backend = name_in_backend
        self.file = file


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

    def __getitem__(self, key):
        return self.get_dataset(key)

    def export_module_decorator(self, original_func):
        def wrapper(*args, **kwargs):
            self.read.add(original_func.__name__)
            result = original_func(*args, **kwargs)
            return result
        return wrapper
    
    def wrap_variable(self, original_variable, name):
        self.read.add(name)
        return original_variable
    
    def export_module(self, exp: Any, return_type: Any = None):
        if inspect.isclass(exp):
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
        
        identifier = self.datastore.create_object(value=src,
                                                  obj_type=ARTIFACT_TYPE_PYTHON)
        descriptor = ArtifactDescriptor(identifier, exp_name, ARTIFACT_TYPE_PYTHON)
        self.dataobjects[exp_name] = descriptor
        self.write.add(exp_name)
        
    def get_dataobject_identifier(self, name):
        """Returns the unique identifier for the dataset with the given name.

        Raises ValueError if no dataset with the given name exists.

        Parameters
        ----------
        name: string
            Dataset name

        Returns
        -------
        string
        """
        # Datset names should be case insensitive
        key = name
        if not key in self.dataobjects:
            raise ValueError('unknown dataobject \'' + name + '\'')
        return self.dataobjects[key]
    
    def set_dataobject_identifier(self, name, identifier):
        """Sets the identifier to which the given dataset name points.

        Parameters
        ----------
        name: string
            Dataset name
        identifier: string
            Unique identifier for persistent dataset
        """
        # Convert name to lower case to ensure that names are case insensitive
        self.dataobjects[name] = identifier
        self.write.add(name)

    def create_dataset(self, name, dataset, backend_options = []):
        """Create a new dataset with given name.

        Raises ValueError if a dataset with given name already exist.

        Parameters
        ----------
        name : string
            Unique dataset name
        dataset : vizier.datastore.client.DatasetClient
            Dataset object

        Returns
        -------
        vizier.datastore.client.DatasetClient
        """
        # Raise an exception if a dataset with the given name already exists or
        # if the name is not valid
        if name.lower() in self.datasets:
            # Record access to the datasets
            raise ValueError('dataset \'' + name + '\' already exists')
        if not is_valid_name(name):
            raise ValueError('invalid dataset name \'' + name + '\'')
        # Create list of columns for new dataset. Ensure that every column has
        # a positive identifier
        columns = list()
        if len(dataset.columns) > 0:
            column_counter = max(max([col.identifier for col in dataset.columns]) + 1, 0)
            for col in dataset.columns:
                if col.identifier < 0:
                    col.identifier = column_counter
                    column_counter += 1
                columns.append(
                    DatasetColumn(
                        identifier=col.identifier,
                        name=col.name,
                        data_type=col.data_type
                    )
                )
        rows = dataset.rows
        # Write dataset to datastore and add new dataset to context
        ds = self.datastore.create_dataset(
            columns=columns,
            rows=rows,
            properties=dataset.properties,
            human_readable_name=name,
            backend_options=backend_options
        )
        self.datasets[name.lower()] = ds
        self.write.add(name.lower())
        return DatasetClient(
            dataset = self.datastore.get_dataset(ds.identifier),
            client = self,
            existing_name = name.lower()
        )

    def drop_dataset(self, name):
        """Remove the dataset with the given name.

        Raises ValueError if no dataset with given name exist.

        Parameters
        ----------
        name : string
            Unique dataset name
        """
        # Make sure to record access idependently of whether the dataset exists
        # or not. Ignore read access to datasets that have been written.
        if not name.lower() in self.write:
            self.read.add(name.lower())
        # Remove the context dataset identifier for the given name. Will raise
        # a ValueError if dataset does not exist
        if self.delete is None:
            self.delete = set()
        self.delete.add(name.lower())
        self.remove_dataset_identifier(name)

    def get_dataset(self, name):
        """Get dataset with given name.

        Raises ValueError if the specified dataset does not exist.

        Parameters
        ----------
        name : string
            Unique dataset name

        Returns
        -------
        vizier.datastore.client.DatasetClient
        """
        # Make sure to record access idependently of whether the dataset exists
        # or not. Ignore read access to datasets that have been written.
        if not name.lower() in self.write:
            self.read.add(name.lower())
        # Get identifier for the dataset with the given name. Will raise an
        # exception if the name is unknown
        identifier = self.get_dataset_identifier(name)
        # Read dataset from datastore and return it.
        dataset = self.datastore.get_dataset(identifier)
        if dataset is None:
            raise ValueError('unknown dataset \'' + identifier + '\'')
        return DatasetClient(
            dataset = dataset,
            client = self,
            existing_name = name.lower()
        )

    def get_dataset_identifier(self, name: str) -> str:
        """Returns the unique identifier for the dataset with the given name.

        Raises ValueError if no dataset with the given name exists.

        Parameters
        ----------
        name: string
            Dataset name

        Returns
        -------
        string
        """
        # Datset names should be case insensitive
        key = name.lower()
        if not key in self.datasets:
            raise ValueError('unknown dataset \'' + name + '\'')
        return self.datasets[key].identifier

    def has_dataset_identifier(self, name):
        """Test whether a mapping for the dataset with the given name exists.

        Parameters
        ----------
        name: string
            Dataset name

        Returns
        -------
        bool
        """
        # Dataset names are case insensitive
        return name.lower() in self.datasets

    def new_dataset(self) -> DatasetClient:
        """Get a dataset client instance for a new dataset.

        Returns
        -------
        vizier.datastore.client.DatasetClient
        """
        return DatasetClient(client = self)

    def remove_dataset_identifier(self, name):
        """Remove the entry in the dataset dictionary that is associated with
        the given name. Raises ValueError if not dataset with name exists.

        Parameters
        ----------
        name: string
            Dataset name
        identifier: string
            Unique identifier for persistent dataset
        """
        # Convert name to lower case to ensure that names are case insensitive
        key = name.lower()
        if not key in self.datasets:
            raise ValueError('unknown dataset \'' + name + '\'')
        del self.datasets[key]

    def rename_dataset(self, name, new_name):
        """Rename an existing dataset.

        Raises ValueError if a dataset with given name already exist.

        Raises ValueError if dataset with name does not exist or if dataset with
        new_name already exists.

        Parameters
        ----------
        name : string
            Unique dataset name
        new_name : string
            New dataset name
        """
        # Make sure to record access idependently of whether the dataset exists
        # or not. Ignore read access to datasets that have been written.
        if not name.lower() in self.write:
            self.read.add(name.lower())
        # Add the new name to the written datasets
        self.write.add(new_name.lower())
        # Raise exception if new_name exists or is not valid.
        if self.has_dataset_identifier(new_name.lower()):
            raise ValueError('dataset \'{}\' exists'.format(new_name.lower()))
        if not is_valid_name(new_name):
            raise ValueError('invalid dataset name \'{}\''.format(new_name))
        # Raise an exception if no dataset with the given name exists
        ds = self.datasets.get(name.lower(), None)
        if ds is None:
            raise ValueError('dataset \'{}\' does not exist'.format(name))
        self.drop_dataset(name.lower())
        self.datasets[new_name.lower()] = ds
        self.write.add(new_name.lower())


    def update_dataset(self, 
            name: str, 
            dataset: DatasetClient
        ) -> DatasetClient:
        """Update a given dataset.

        Raises ValueError if the specified dataset does not exist.

        Parameters
        ----------
        name : string
            Unique dataset name
        dataset : vizier.datastore.base.Dataset
            Dataset object

        Returns
        -------
        vizier.datastore.client.DatasetClient
        """
        # Get identifier for the dataset with the given name. Will raise an
        # exception if the name is unknown
        identifier = self.get_dataset_identifier(name)
        # Read dataset from datastore to get the column and row counter.
        source_dataset = self.datastore.get_dataset(identifier)
        if source_dataset is None:
            # Record access to the datasets
            self.read.add(name.lower())
            raise ValueError('unknown dataset \'' + identifier + '\'')
        column_counter = source_dataset.max_column_id() + 1
        # Update column and row identifier
        columns = dataset.columns
        rows = dataset.rows
        # Ensure that all columns has positive identifier
        for col in columns:
            if col.identifier < 0:
                col.identifier = column_counter
                column_counter += 1
        
        #gather up the read dependencies so that we can pass them to mimir 
        # so that we can at least track coarse grained provenance.
        # TODO: we are asumming mimir dataset and datastore 
        #       here and need to generalize this
        read_dep = []
        for dept_name in self.read:
            if not isinstance(dept_name, str):
                raise RuntimeError('invalid read name')
            dept_id = self.get_dataset_identifier(dept_name)
            dept_dataset = self.datastore.get_dataset(dept_id)
            if dept_dataset is not None:
                read_dep.append(dept_dataset.identifier)
        ds = self.datastore.create_dataset(
            columns=columns,
            rows=rows,
            properties=dataset.properties,
            human_readable_name=name,
            dependencies=read_dep
        )
        self.datasets[name.lower()] = ds
        self.write.add(name.lower())
        return DatasetClient(
            dataset = self.datastore.get_dataset(ds.identifier),
            client = self,
            existing_name = name.lower()
        )
        
    def get_dataset_frame(self, name):
        """Get dataset with given name as a pandas dataframe.

        Raises ValueError if the specified dataset does not exist.

        Parameters
        ----------
        name : string
            Unique dataset name

        Returns
        -------
        pandas.Dataframe
        """
        # Make sure to record access idependently of whether the dataset exists
        # or not. Ignore read access to datasets that have been written.
        if not name.lower() in self.write:
            self.read.add(name.lower())
        # Get identifier for the dataset with the given name. Will raise an
        # exception if the name is unknown
        identifier = self.get_dataset_identifier(name)
        # Read dataset from datastore and return it.
        dataset_frame = self.datastore.get_dataset_frame(identifier)
        if dataset_frame is None:
            raise ValueError('unknown dataset \'' + identifier + '\'')
        return dataset_frame
        
    def dataset_from_s3(self, 
        bucket: str, 
        folder: str, 
        file: str, 
        line_extracter: Callable[[re.Match, str], Tuple[str, str]]
          = lambda rematch, line: ('col0', line), 
        additional_col_gen: Optional[Callable[[re.Match, str], Tuple[str, str]]] = None, 
        delimeter: str = ",", 
        line_delimeter: str = "\n"
    ) -> Optional[DatasetClient]:
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
                    for d in data.stream(100*1024):
                        rdat.write(str(d.decode('utf-8')))
                    lines = rdat.getvalue().split(line_delimeter)
                    
                    entry: Dict[str,str] = {}
                    if additional_col_gen is not None:
                        entry = dict({ additional_col_gen(result, line) for line  in lines })
                    line_entries =  dict({ line_extracter(result,line) for line  in lines })
                    entry = {**entry , **line_entries}
                    # The following line seems to be OK only because
                    # the preceding line is a { ** } constructor.  
                    # I have absolutely no clue why the following works,
                    # otherwise.  Either way, let's shut mypy up for now
                    # -OK
                    entry.pop(None, None) # type: ignore
                    
                    # Append unknown dictionary keys to the list
                    for attr in list(set(entry.keys())-set([col.name for col in ds.columns])):
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
    
    def pycell_open(self, file, mode='r', buffering=-1, encoding=None, errors=None, newline=None, closefd=True, opener=None):
        print("***File access may not be reproducible because filesystem resources are transient***")
        return open(file, mode, buffering, encoding, errors, newline, closefd, opener)    
      
    def set_output_format(self, mime_type):
        self.output_format = mime_type

    def show(self, value, mime_type = None, force_to_string = True):
        if not issubclass(type(value), OutputObject):
            if mime_type is not None:
                value = OutputObject(value = value, type = mime_type)
            elif type(value) is str:
                value = TextOutput(value = value)
            elif type(value) is DatasetClient:
                from vizier.api.webservice import server
                ds_handle = server.api.datasets.get_dataset(
                                project_id=self.project_id,
                                dataset_id=value.dataset.identifier,
                                offset=0,
                                limit=10
                            )
                value = DatasetOutput(ds_handle)
            elif issubclass(type(value), BokehLayout):
                value = vizier_bokeh_render(value)
                value = HtmlOutput(value = value)
            elif issubclass(type(value), MatplotlibFigure):
                value = HtmlOutput(value = vizier_matplotlib_render(value))
            elif issubclass(type(value), MatplotlibAxes):
                value = HtmlOutput(value = vizier_matplotlib_render(value.get_figure()))
            else:
                repr_html = getattr(value, "_repr_html_", None)
                if repr_html is not None:
                    value = HtmlOutput(str(repr_html()))
                elif force_to_string:
                    value = TextOutput(value = str(value))
                else:
                    return
        self.stdout.append(value)

    def show_html(self, value):
        self.show(HtmlOutput(value))
    
class Analyzer(ast.NodeVisitor):
    def __init__(self, name):
        self.name = name
        self.source = ''
        # track context name and set of names marked as `global`
        self.context = [('global', ())]

    def visit_FunctionDef(self, node):
        self.context.append(('function', set()))
        if node.name == self.name:
            self.source = "@vizierdb.export_module_decorator\n" + astor.to_source(node)
            self.generic_visit(node)
        self.context.pop()

    # treat coroutines the same way
    visit_AsyncFunctionDef = visit_FunctionDef

    def visit_Assign(self, node):
        self.context.append(('assignment', set()))
        target = node.targets[0]
        if target.id == self.name:
            self.source = "{} = vizierdb.wrap_variable({}, '{}')".format( self.name, astor.to_source(node.value), self.name)
            self.generic_visit(target)
        self.context.pop()
        
    def visit_ClassDef(self, node):
        self.context.append(('class', ()))
        if node.name == self.name:
            self.source = "@vizierdb.export_module_decorator\n" + astor.to_source(node)
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
        if node.id == self.name and (ctx == 'global' or node.id in g):
            print('exported {} at line {} of {}'.format(node.id, node.lineno, self.source))
            
    def get_Source(self):
        return self.source
    