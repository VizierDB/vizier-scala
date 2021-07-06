# Vizier
### The world's first kernel-free notebook.

Vizier is an interactive, reactive **workbook**: A workflow system with a notebook-style interface.  

### Features

* **No Kernels**: There's no long-running kernel with state to lose if you have to log out.
* **Reproducibility**: Vizier executes cells *in order*, and automatically re-executes cells when their inputs change so your notebook's outputs are always up-to-date.
* **Data Snapshots**: Vizier automatically snapshots data created by each cell, so you can re-run a cell without re-running all of its inputs.
* **Polyglot**: You can combine Python, SQL, and Scala, all seamlessly working with the same data.
* **No-code**: Use a spreadsheet-style interface, or Vizier's "data lenses" to work with your data, code optional!
* **Workflow Snapshots**: Vizier automatically keeps a record of how you edit your workflow so you can always go back to an earlier version.
* **Scalable**: Vizier datasets are backed by Spark and Apache Arrow, allowing you to make big changes fast.

---

### Getting started

Install Vizier:
```
wget https://maven.mimirdb.info/info/vizierdb/vizier
chmod +x vizier
sudo mv vizier /usr/local/bin
```

Start Vizier:
```
vizier
```

And open up http://localhost:5000 in your web browser.

---

### More Info

* [Project Website (w/ screenshots)](https://vizierdb.info)
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

```
mill vizier.test
mill vizier.ui.test
```

The UI test cases require `node`.  Install it and then 
```
npm install jsdom
```

##### Run Vizier
```
mill vizier.run [vizier arguments]
```

##### Build the UI (fast)
```
mill vizier.ui.fastOpt
mill vizier.run --connect-from-any-host
```
And load `debug.html` in the browser

Compiled JS outputs will be in `out/ui/fastOpt/dest`

##### Deploy the UI into Vizier
```
mill vizier.ui.fullOpt
cp out/vizier/ui/fullOpt/dest/out.js vizier/resources/ui/vizier.js
```

##### Publish the Repo
```
mill vizier.publishLocal
```

##### Set up bloop/metals
```
mill mill.contrib.Bloop/install
```
