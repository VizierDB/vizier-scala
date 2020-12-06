## Gotchas

If you take nothing else away from this document, read this section.  It highlights the oddities of this codebase.  These are things that will cause you grief if you try to make changes without realizing what's going on.

#### ScalikeJDBC

[ScalikeJDBC](http://scalikejdbc.org/) is a scala-native wrapper around JDBC connections (analogous to Python's Alchemy).  It makes *extensive* use of scala implicits, which can be a little confusing if you're just getting started with scala, so let's start with a quick primer on the part of implicits that you need to know.

Global state is annoying, but can be more programmer friendly when you need to keep passing that state into functions.  For example, let's say you have a JDBC connection.  You need to keep passing that connection into every function that might potentially need it.  That makes code hard to read and write, because you always have that extra argument hanging off the end.  It can be easier just to dump the connection into a global variable and use it when needed.  Global state, however, drives functional people mad (for good reasons).

Scala (kind of?) avoids the pitfals of global state through something it calls implicit parameters.  Take the following function:
```
def foo(implicit x: Bar) = x.baz
```
To call this function, you don't need to pass the `x` parameter explicitly.  You would just write
```
println(s"${foo}")
```
Of course, this example is a little too simple... if you actually try the following, the compiler will yell at you:
```
def myFunction() = 
{
  println(s"${foo}")
}
```
What's happening under the hood here is that instead of manually passing a `Bar` object around, the compiler tries to automatically pick and plug in a `Bar` object that you happen to have lying around.  Since you don't have one, it gets confused.  Let's try the following: 
```
def myFunction() = 
{
  val x = Bar(baz = "stuff")
  println(s"${foo}")
}
```
That still won't work, because the compiler won't look for just any old variables.  You have to explicitly mark a variable as implicit for it to work, like so:
```
def myFunction() = 
{
  implicit val x = Bar(baz = "stuff")
  println(s"${foo}")
}
```
Again, looking under the hood, when you call foo (without parameters), the compiler will see the *implicit* `x` parameter of type `Bar`, try to find a `Bar` object in scope (possibly with a different name), and automatically plug that object in to the function call.

----

ScalikeJDBC makes *extensive* use of implicit variables to keep track of session state.  Specifically, in this codebase you will see a lot of functions with `(implicit session: DBSession)`.  These are functions that **DO** assume that you have an open session.  Conversely, functions without this parameter assume that you **DO NOT** have an implicit session active.

To create a session, use one of the following two constructs:
```
DB.readOnly { implicit s => /* your code goes here */ }
```
or
```
DB.autoCommit { implicit s => /* your code goes here */ }
```

**Gotcha**: Nested session creation is *verbotten*.  If you find code myseriously hanging or getting SQL timeouts, you are creating a nested session.  

The following guidelines apply with respect to session creation:

* **info.vizierdb.api**: All classes/methods assume that you *DO NOT* have a session.
* **info.vizierdb.artifacts**: Classes/methods do not care whether you have a session.
* **info.vizierdb.catalog**: Classes/methods assume that you *DO* have a session.
* **info.vizierdb.commands**: Classes/methods assume that you *DO NOT* have a session.
* **info.vizierdb.filestore**: Classes/methods do not care whether you have a session.
* **info.vizierdb.viztrails.MutableProject**: Assumes that you *DO NOT* have a session.
* **info.vizierdb.viztrails.Provenance**: Assumes that you *DO* have a session.
* **info.vizierdb.viztrails.Scheduler**: Assumes that you *DO* have a session.

## Overview

#### `src/main/scala/info/vizierdb`

* `types`: A repository of Enums and typecasts for the entire project
* `Vizier`: The entry point into Vizier -- Main function, initialization, etc...
* `VizierAPI`: Jetty routes (to handlers in `.api`)
* `VizierURLs`: URL constructors for the routes in `VizierAPI` (would be nice to automate this eventually).

#### `src/main/scala/info/vizierdb/catalog`

Objects for the Vizier ORM.  Classes in this package **must not create DB sessions**; Instead mark methods with implicit session parameters where needed.

* `Metadata`: Preferences/general settings for the local install
* `Project`: A single project/notebook
    * has many `Artifact`: A dataset, file, exported function, etc... created in the course of workflow execution.
    * has many `Branch`: A sequence of workflows, iterated over time.  The most recent workflow is referred to as the `head`
        * has many `Workflow`: A sequence of cells.
            * has many `Cell`: A module at a specific position in a workflow and any resulting execution state.
                * has one `Module`: A command identifier coupled with a set of arguments to that command.  A single module may be shared by multiple cells.
                * may have one `Result`: The outcome of executing a cell.  A single result may be shared by multiple cells.
                    * has many [Input]`ArtifactRef`: An artifact referenced in the execution of this cell.
                        * has one `Artifact`
                    * has many [Output]`ArtifactRef`: An artifact created by executing this cell.
                        * has one `Artifact`
                    * has many `Message`: Logging or data visualization outputs generated as a result of executing a cell.

A `Workflow` is the heart of Vizier, and represents an ordered sequence of `Module`s in the notebook.  `Cell` serves as a many-many relationship table, assigning specific `Module`s to (i) Their position in the workflow, and (ii) The `Result` of executing them at that point in the workflow.  This may seem a little overcomplicated, but it is necessary.
* A single `Module` may be re-used across workflows.  In principle, we could create a copy of the entire workflow every time we modify it, but apart from wasting space, this makes it *much* harder to track relationships between workflows.
* A single `Result` may *also* be re-used across workflows (e.g., when we discover that a module's inputs --- `InputArtifactRef` that is --- remain unchanged).
* Thus, `Cell` acts as a 3-way many/many relationship between `Workflow`, `Module` and `Result`.  Note that the `Result` relationship is optional (e.g., if we have not yet figured out whether the cell needs to be recomputed).  

The `Artifact` class is a little tricky for two reasons.  First, an `Artifact` can be of multiple types (see `types.ArtifactType`), dictating how the artifact behaves.  Second, two of these types are odd: 
* An `ArtifactType.DATASET` does not live in Vizier, but rather in Mimir/Spark.  
* An `ArtifactType.FILE` also does not live in Vizier, but rather in the local filesystem.
The `Artifact` records is mainly present as a placeholder in both cases.


The ``Schema`` class is a master specification of the database state that backs the ORM.  Scalikejdbc only has limited support for structure creation, and no support for Schema migrations.  The Schema class does both.  

**Gotcha**: When changing one of the ORM classes listed above, make sure to apply relevant changes to the Schema class.  Additionally as of right now the Schema is locked in: Any changes must be made by adding migrations.

The ``binders`` object contains instructions for Scalikejdbc to translate Enums in `info.vizierdb.types`. 

#### `src/main/scala/info/vizierdb/commands`

Implementation of module command implementations, each of which extend the `Command` trait.  A command has four parts:

* A `name` that is how the command is displayed in the UI
* A list of `parameters` that describe the fields that appear in the UI
* A `format` that generates a short summary text describing what the command is doing based on its arguments.
* A `handle`r that implements the command logic.

To register a `Command`, use `Commands.register` (see `Commands` itself for examples).  Commands are registered as part of a package (with a string identifier), and each command has a unique `commandId` (also a string).  

There are three main classes involved in implementing a `Command`: 

Implementations of the `Parameter` trait are used to describe arguments to the cell.  Each `Parameter` implementation corresponds to a different widget in the UI.  Specific details vary for each parameter type, but all Parameter constructors have the following fields:
* `id`: A unique identifier for this parameter (used in conjunction with `Arguments`)
* `name`: A human-readable description for the parameter.  This is what is shown in the UI.
* `required`: If true, the UI will not allow users to omit/leave blank this parameter (default: true)
* `default`: A default value for the parameter (default: null/None)
* `hidden`: If true, the UI will pass around this parameter, but will not display it to the user (default: false)

At time of writing, available parameters include: 
* `BooleanParameter`: A checkbox
* `CodeParameter`: A coding block.  The `language` field must be set to a language supported by the UI (currently `sql`, `scala`, `python`, or `r`)
* `ColIdParameter`: A column selector.  When this parameter is used, there must also be a `DatasetParameter` with `id = "dataset"`; The list of columns will be drawn from this dataset.  The "value" of this parameter is the `Int` position of the selected column.
* `DatasetParameter`: A dataset selector.  The list of datasets used will be appropriate for the point in the notebook.  The returned value will be the `String` **userFacingName** of the dataset.
* `DecimalParameter`: A field where the user can enter any `Double`.
* `FileParameter`: A file upload area.  The uploaded/linked file will be provided as a `FileArgument`
* `IntParameter`: A field where the user can enter any `Int`.
* `ListParameter`: A variable-height 2-d grid of elements, each specified as a parameter.  The `name` field of component parameters will be used as the column header.
* `RecordParameter`: A grouping of multiple parameters into a single row in the UI.  Behaves like a `ListParameter`, but with only a single row.
* `RowIdParameter`: A field where the user can enter any row identifier as a `String`.  Mainly for use in the spreadsheet, and not for human use.
* `EnumerableParameter`: A drop-down menu.  The `values` are `EnumerableValue` instances including a `name` (the user-facing name) and a `value` (a `String`) that is provided during command execution.
* `StringParameter`: A field where the user can enter any `String`.

-----

The `Arguments` class is used to streamline access to parameter values provided by the UI (which are natively Json objects).  `Arguments` are accessed via four methods:
* `get[Type](id)`: Returns the parameter with id `id`, casting it to type `Type`.  (It would be fantastic if we could make this more strongly typed, but that level of Scala-fu is not worth it at this point)
* `getOpt[Type](id)`: Like `get`, but returns an `Option[Type]`.
* `getList(id)`: For use with `ListParameter`, returns a `Seq[Arguments]` with one entry per row in the list.
* `getRecord(id)`: For use with `RecordParameter`, returns the nested `Arguments` object.

-----

Finally, `ExecutionContext` provides key/value access to artifacts created by preceding cells, and is passed to `Command.handle`.   This class is currently a little slapdash, and needs clean-up, but basically *ALL* interactions with artifacts should go through it (since it also automatically records accesses).  All artifact access is **case-insensitive**.  

Basic functions include
* `artifactExists(name)`: Returns `true` if the specified artifact is in-scope
* `artifact(name)`: Returns the `Artifact` object associated with the specified name, or `None` if the name is undefined
* `output(name, artifact)`: Inserts/replaces the artifact associated with `name` with `artifact`
* `error(message)`: Logs an error message.
* `message([mimeType, ]content)`: Logs a status message.  If the optional `mimeType` is provided, one of several special display handlers in the UI can be invoked.  For example passing `MIME.HTML` (=`"text/html"`) will cause the message to be rendered as HTML rather than plaintext.

A few additional utility methods also exist to streamline common tasks (e.g., displaying datasets in the output).  

Two methods in particular are notable: `outputDataset` and `outputFile`.  Both datasets and files live outside of the Vizier state, and the corresponding `Artifact` objects are simply placeholders.  Thus, calling `outputDataset` or `outputFile`, creates the placeholder, but it is still up to the caller to create the resulting object.

Finally, I'll mention the `updateArguments` and `updateJsonArguments` methods.  Both methods do basically the same thing: Create a new module with some its arguments replaced and relink the current cell to the new module.  If misused, this **HAS THE POTENTIALY TO DO VERY VERY BAD THINGS TO REPRODUCIBILITY, SUMMON A HELLSWARM, AND CAUSE THE END OF DAYS**.  They exist for two very specific use-cases:

1. As part of its normal operation, the `Command` is able to infer some of its parameters from the input.  For example, `LoadDataset` is able to infer the schema of the loaded dataset.  When parameters are inferred, it often makes sense to codify the inferred parameters in the module specification to allow the user to override them.  This is *only safe if you can guarantee that executing the module before and after the replacement will produce *exactly* the same output.

2. As part of its normal operation, the `Command` has non-deterministic behavior.  For example, `command.sample._` need to generate a random sample seed.  It is *safe to store seed values to make subsequent cell executions deterministic*.


#### `src/main/scala/info/vizierdb/viztrails`

Most of the execution logic lives here, between two classes: `Provenance` and `Scheduler`.  

`Provenance` is a set of utility methods for dealing with sequences of `Cells` (i.e., subsets of `Workflows`).  Its methods serve three main roles:
* Given a sequence of `Cell` objects, link their `OutputArtifactRef`s together to compute the state (scope) of a workflow after those cells were executed.
* Determine whether the `Result` for a `Cell` is safe for re-use from a previous execution (by comparing the `InputArtifactRef`s to the scope generated by the preceding cells).
* Compute the next `Cell` in an incomplete `Workflow` to need re-execution.

In general `Provenance` is also responsible for maintaining the Cell state machine.
```
Clone Cell
         \
          v
    --- WAITING +----------> DONE
   /       |     \            ^
  /        v      \           |
  |     BLOCKED ---+-> ERROR  |
  |        |      /           /
   \       v     /           /
    `--> STALE -+-----------`
           ^
          /
  New Cell

```
* **STALE**: The `Cell` does not have a `Result`, or it has been determined that the `Cell`'s `Result` is out-of date, AND there is nothing preventing the cell from being executed right now.  **STALE** cells are generally being actively computed.
* **BLOCKED**: Like **STALE**, but at least one other cell needs to be executed before this cell can be executed.
* **WAITING**: The `Cell` has a `Result`, but it is not known whether the `Result` is out-of-date or not.  Generally, this happens when a preceding cell was modified, and it's not clear whether one of the datasets accessed by this cell will be changed.
* **DONE**: The `Cell` has an up-to-date `Result`.
* **ERROR**: Execution of `Cell` or one of its preceding `Cell`s failed.

`Scheduler` handles actually executing cells that need (re-)execution.  Broadly, it looks for `Cell`s in the **STALE** state, executes them, and then invokes `Provenance` to update the remaining `Cell` state machines. There are four main methods here: 
* `schedule(workflowId)`: Asynchronously executes any `Cell`s in the specified `Workflow` for as long as execution is possible.
* `abort(workflowId)`: If the specified `Workflow` has executing cells, cancel execution.
* `isWorkflowPending(workflowId)`: Return `true` if the specified `Workflow` is currently using resources on execution.
* `joinWorkflow(workflowId)`: Block the current thread until there is no further computation happening on the specified `Workflow`.

**Gotcha**: Scheduler operates asynchronously, so it's important that any Workflow construction is completed *and committed* before you call `Scheduler.schedule`.  Essentially, make sure that you call `Scheduler.schedule` *outside* of the `DB.autocommit` block used to create the workflow.  See, for example, `info.vizierdb.api.AppendModule`

Currently, the heavy asynchronous lifting is done by Java's `ForkJoinPool` and `ForkJoinTask` (which is subclassed by `Scheduler.WorkflowExecution`).  These iteratively identify the next `Cell` for execution, and then call `Scheduler.processSynchronously`, which loads all relevant context, allocates a `Result` object, and invokes the appropriate `Command` handler.

-----

The last thing in this package is the `MutableProject` class.  This is mainly meant for testing purposes, but kind of acts like a "client" wrapper for `Catalog`.  It's not really complete, but is meant to abstract away some of the immutability of the append-only catalog model.

#### `src/main/scala/info/vizierdb/api`

This package includes all of the handlers referenced in `VizierAPI`.  These adopt the API handler model used by `mimir-api`.  Nothing super fancy here.
