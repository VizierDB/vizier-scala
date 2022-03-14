# Vizier
### The world's first kernel-free notebook.

Vizier is an interactive, reactive **workbook**: A workflow system with a notebook-style interface.  

### Features

* **No Kernels**: There's no long-running kernel with state to lose if you have to log out.
* **Reproducibility**: Vizier executes cells *in order*, and automatically re-executes cells when their inputs change so your notebook's outputs are always up-to-date.
* **Data Snapshots**: Vizier automatically snapshots data created by each cell, so you can re-run a cell without re-running all of its inputs.
* **Polyglot**: You can combine Python, SQL, and Scala, all seamlessly working with the same data.
* **Code-Optional**: Use a spreadsheet-style interface, or Vizier's "data lenses" to work with your data, code optional!
* **Workflow Snapshots**: Vizier automatically keeps a record of how you edit your workflow so you can always go back to an earlier version.
* **Scalable**: Vizier datasets are backed by Spark and Apache Arrow, allowing you to make big changes fast.
* **Reactive**: Changes are reflected immediately.

**See some [Screenshots](https://vizierdb.info/#features)**

---

### Getting started

Make sure you have JDK (v8 preferred, v11 otherwise) and Python3.X installed.

Download vizier from [releases](https://github.com/VizierDB/vizier-scala/releases) or 
```
wget https://maven.mimirdb.info/info/vizierdb/vizier
```

##### Install Vizier
```
chmod +x vizier
sudo mv vizier /usr/local/bin
```

##### Start Vizier
```
vizier
```
... and open up http://localhost:5000 in your web browser.

##### Or run with Docker

```
docker run -p 5000:5000 --name vizier okennedy/vizier:latest
```
... and open up http://localhost:5000 in your web browser.

---

### More Info

* [Project Website (w/ screenshots)](https://vizierdb.info)
* [Vizier for Jupyter Users](https://github.com/VizierDB/vizier-scala/wiki/Migrating-from-Jupyter)
* [User Documentation](https://github.com/VizierDB/vizier-scala/wiki)
* [Developer Documentation](https://github.com/VizierDB/vizier-scala/blob/master/docs/DEVELOPER.md)

---

### No Kernel?

Unlike most notebooks, Vizier is not backed by a long-running kernel.  Each cell runs in a fresh interpreter.  

Cells communicate by exporting _artifacts_:
* datasets (e.g., Pandas or Spark dataframes)
* files
* parameters
* charts
* python functions

Define and export a function in a python cell, and then use it in a SQL cell.  

Vizier tracks how cells use artifacts and inter-cell dependencies.  When a cell is updated, all cells that depend on it will be automatically re-run.  For example, when you modify the python function, Vizier will automatically re-run the SQL cell.

---

### Compiling

To use this repository you'll need [Scala 2](https://www.scala-lang.org/download/scala2.html) and [Mill](https://com-lihaoyi.github.io/mill/mill/Intro_to_Mill.html#_installation).  

Some useful commands for using this repository

##### Compile Vizier
```
mill vizier.compile
```
Compiled class files will be in `out/vizier/compile/dest`

##### Run Test Cases

We suggest using at least mill version 0.9.8 (released May 27, 2021), as it has somewhat better support for specs2 testing.

```
mill vizier.test
mill vizier.ui.test
```

The UI test cases require `node`.  Install it and then 
```
npm install jsdom
```

To run a single test case:
```
mill vizier.test.testOnly [classname]
```

##### Run Vizier
```
mill vizier.run [vizier arguments]
```

Vizier defaults to running on port 5000 on localhost.

##### Hack on the UI

Vizier takes a few seconds to start.  If you're only editing the UI, you can get a faster development cycle by only rebuilding and reloading the resources directory

```
mill -w vizier.ui.resourceDir
```

Mill's `-w` flag watches for changes to files in the UI directory.  Any changes will trigger a recompile.  Vizier uses the generated resources directory in-situ, so reloading the UI in your web browser will load the updated version.

##### Publish the Repo
```
mill vizier.publishLocal
```

##### Set up bloop/metals
```
mill mill.contrib.Bloop/install
```
