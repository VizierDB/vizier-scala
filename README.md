# vizier-scala
### The world's first kernel-free notebook.

Vizier is an interactive, reactive **workbook**: A workflow system with a notebook-style interface.  

### Features

* No Kernels: There's no long-running kernel with state to lose if you have to log out.
* Reproducibility: Vizier automatically re-executes cells when their inputs change, so your notebook's outputs are always up-to-date
* Data Snapshots: Vizier automatically snapshots data created by each cell, so you can re-run a cell without re-running all of its inputs
* Polyglot: You can combine Python, SQL, and Scala, all seamlessly working with the same data.
* No-code: Use a spreadsheet-style interface, or Vizier's "data lenses" to work with your data, no code required!
* Workflow Snapshots: Vizier automatically keeps a record of how you edit your workflow so you can always go back to an earlier version.
* Scalable: Vizier datasets are backed by Spark and Apache Arrow, allowing you to make big changes without fuss.

---

### More Info

* [Project Website (w/ screenshots)](https://vizierdb.info)
* [User Documentation](https://github.com/VizierDB/vizier-scala/wiki)
* [Developer Documentation](https://github.com/VizierDB/vizier-scala/blob/master/docs/DEVELOPER.md)

---

### No Kernel?

Unlike most notebooks, Vizier is not backed by a long-running kernel.  Each cell runs in a fresh interpreter.  

Cells communicate by creating "artifacts":
* datasets (e.g., Pandas or Spark dataframes)
* files
* parameters
* charts
* python code

For example, you can define and export a function in a python cell, and use it as a User Defined Function in a SQL cell.  
Vizier tracks which artifacts a cell uses, so that if you change something, it knows which cells need to be re-run.
When an artifact is updated (e.g., when you modify the function), every cell that used it (e.g., the SQL cell) will be re-executed.


