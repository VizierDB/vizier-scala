
## Vizier 0.3
- [ ] Lightweight Spark Runtime

## Vizier 0.2
- [ ] Import / Export
    - [x] Import
    - [ ] Export
- [ ] "Scala Script" (code)
- [x] The final lenses
    - [x] "Comment Lens" (comment)
    - [x] "Pivot" (pivot)
    - [x] "Geocode" (geocode)
- [ ] Minor Bugs
    - [x] "Font MIME encoding"
    - [x] Report SQL parse exceptions properly 
        - [x] org.apache.spark.sql.catalyst.parser.ParseException
        - [x] org.apache.spark.sql.AnalysisException
    - [ ] Selection pushdown happening too aggressively https://github.com/UBOdin/mimir-api/issues/33
- [ ] Copyright Comments
- [x] Add a task to publish SNAPSHOT releases to local coursier repo

## Vizier 0.1
- [x] Set up a simple, naive background worker (Java's ForkJoinPool.)
- [x] Link to Mimir-API
    - [x] "Load Dataset" (load)
    - [x] "Empty Dataset" (empty)
    - [x] "Clone Dataset" (clone)
    - [x] "Unload Dataset" (unload)
    - [x] "SQL Query" (query)
- [x] Develop web-api-async compatible API
    - [x] Import built web-ui
- [x] File Upload/Download
- [x] Implement sub-process execution
    - [x] "Python Script" (code)
- [x] "Markdown Code" (code)
- [x] Import remaining Vizier packages
    - [x] "Simple Chart" (chart)
    - [x] "Basic Sample" (basic_sample)
    - [x] "Manually Stratified Sample" (manual_stratified_sample)
    - [x] "Automatically Stratified Sample" (automatic_stratified_sample)
    - [x] "Delete Column" (deleteColumn)
    - [x] "Delete Row" (deleteRow)
    - [x] "Drop Dataset" (dropDataset)
    - [x] "Insert Column" (insertColumn)
    - [x] "Insert Row" (insertRow)
    - [x] "Move Column" (moveColumn)
    - [x] "Move Row" (moveRow)
    - [x] "Filter Columns" (projection)
    - [x] "Rename Column" (renameColumn)
    - [x] "Rename Dataset" (renameDataset)
    - [x] "Sort Dataset" (sortDataset)
    - [x] "Update Cell" (updateCell)
    - [x] "Fix Key Column" (repair_key)
    - [x] "Missing Value Lens" (missing_value)
    - [x] "Fix Sequence" (missing_key)
    - [x] "Merge Columns" (picker)
    - [x] "Detect Field Types" (type_inference)
    - [x] "Shape Detector" (shape_watcher)

## Later

- [ ] Make rowids more "elegant" for Vizual operations
- [ ] R script again (via Arrow?)