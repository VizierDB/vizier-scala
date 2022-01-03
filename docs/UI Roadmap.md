# UI Roadmap

## Views 

### Project View
- [ ] Menu Bar
- [ ] History View
- [ ] Project Overview
  - [ ] Display a Summary of the Workflow
  - [ ] Mouseover Provenance Info
- [ ] Improve Dataset Display
  - [ ] Edit Button
  - [ ] Download Button
  - [ ] Scroll to view more <- making this efficient will be tricky
- [ ] Override Cell Editor / Display
  - [ ] Add support for overriding cell displays based on cell type
  - [ ] Override Markdown cells to support click-to-edit
  - [ ] Override code cells to display codemirror and support click-to-edit
    - [ ] SQL
    - [ ] Python
    - [ ] Scala
  - [ ] Add support for overriding cell editors based on cell type
    - [ ] Load Dataset
      - [ ] Hide Advanced Options

### Spreadsheet View

- [ ] Basic data viewing in an infinite scrolling list
  - [ ] Find a way to do partial display of scrolling lists w/o giant piles of HTML (p)
  - [ ] Add caching to the existing dataset retrieval code
  - [ ] Dynamic caching of parts of the data in the frontend
    - [ ] Spinners on cells not loaded yet
  - [ ] Provide a way to prefill data that's been cached in the dataset message
- [ ] Basic editing in-situ
  - [ ] Add a websocket-based interface for spreadsheet sessions
  - [ ] Add an "editor" mode for spreadsheets that connects to the websocket instead of the REST API.
  - [ ] Add an "editor" mode for individual spreadsheet cells.
  - [ ] Edited cells are sent to the backend through the websocket
  - [ ] The websocket sends updates for specific cells back to the frontend
    - Want to gate updates by which cells are (a) visible, (b) cached.
  - [ ] Add support for row-level formulas
  - [ ] Add support for arbitrary formulas
- [ ] Editing outputs a vizual cell
  - [ ] Allow outputs to be saved as a vizual script cell
  - [ ] (optional) Live feedback for editing with downstream notebook operations

###### Discussion
This interface should be snappy on small datasets, but also be viable on arbitrarily large datasets as well.  The following design considerations apply:

- There are likely to be large numbers of datasets displayed in notebook view.  We should make a distinction between a spreadsheet in "editing" mode (which is going to want a *stateful* websocket) and a spreadsheet in "display" mode (which can probably get away with *stateless* AJAX requests to cache more data).  
- The backend can't load the entire dataset in all at once.  Use `info.vizierdb.spark.DataFrameCache` to keep only specific pages of data in memory.  This needs to happen BOTH for stateless and stateful requests.
- The frontend should also do some caching.  

**Questions:**
- What should be considered "small" (tentatively: 1 or 5k records)
  - The answer should inform the caching "page" size.  
- There are conceptually two "levels" of caching in the frontend: (i) The actual data, and (ii) the DOM nodes/Rx values responsible for displaying that data.  The question is whether these two levels should be distinct.  We're definitely going to want to do some trickery to display an "infinite" scrolling list, but do we want to add a layer under that to cache data, or to just materialize dom nodes for every data value that we have cached currently?


## Details

### Menu Bar
- Project
  - Exit to Project List
  - Rename Project
  - Link to Project
- Execution
  - Abort
  - Thaw All
  - Freeze All
- Versions
  - Create Branch
  - Rename Branch
  - View History
- Artifacts
  - ... list of artifacts ...
- Caveats (include in the artifacts view?)

##### Comments
- Jan 2, 2022 by OK: Should the artifacts list be a separate info view rather than a menu?

### Load Dataset Cell
- [ ] Basic view
  - Tab-based view for displaying different "data loaders".  Initially:
    - Load from URL
    - Load from File Artifact / Upload
  - Table name
    - Auto-populated when data is uploaded/URL is provided
  - File type
    - Auto-populated based on Regexp when data is uploaded/URL is provided
  - Guess attribute types
    - If auto-selected based on file type
  - Field Names/Types
- [ ] Improve CSV type
  - [ ] Add support for optional fields in view
  - [ ] Add a "delimiter" field
  - [ ] Add a "header" field
- [ ] Add a local filesystem data loader tab
  - [ ] Add endpoints for browsing the local filesystem
  - [ ] Add a UI for interacting with this endpoint (export file path as URL)
  - [ ] Option for "caching" the URL with the project
