# UI Roadmap

## Views 

### Project View
- [x] Menu Bar
  - [ ] Link to datasets (#17)
  - [ ] Execution (find better name) Menu
    - [ ] Stop Cell Execution
    - [ ] Freeze execution
    - [ ] Enable/Disable Notifications (#39)
  - [ ] Link to branches
    - [ ] Change branch
    - [ ] Set default branch (#5)
- [ ] History View
- [ ] Project Overview
  - [x] Display a Summary of the Workflow
  - [ ] Mouseover Provenance Info
- [ ] Settings view
  - [ ] Create/manage a local pyenv for the project (#58)
  - [ ] Pip package management (server mode will need a special implementation) (#13)
  - [ ] Import maven artifacts (#94)
  - [ ] Link to spark status (#86)
- [ ] Improve Dataset Display
  - [x] Edit Button
  - [ ] "pop-out" button
  - [ ] Download Button
  - [x] Scroll to view more <- making this efficient will be tricky
  - [ ] "Show Provenance" button (#27)
- [ ] Cell UI design
  - [ ] Clean up the general cell format
  - [ ] Clean up "pick a cell type" UX
    - [ ] Add heuristic highlights (e.g., Load Dataset on an empty notebook) (#33)
  - [ ] Include last updated/last ran date/time
  - [ ] "Pop-out" cells (#114)
- [ ] Improve Table of Contents (#65)
  - [ ] Add H1-H6 level tags
  - [ ] Put the tags into a hierarchical structure
  - [ ] Allow hierarchies/sections to be collapsed
  - [ ] Add dependency display (re #27)
- [ ] Override Cell Editor / Display
  - [ ] Add support for overriding cell displays based on cell type
  - [ ] Improve Markdown cells
    - [ ] Support click-to-edit
    - [ ] Emphasize cells (special formatting)
  - [ ] Override code cells to display codemirror and support click-to-edit
    - [ ] SQL
    - [ ] Python
    - [ ] Scala
  - [ ] Add support for overriding cell editors based on cell type
    - [ ] Load Dataset
      - [ ] Hide Advanced Options
      - [ ] Local file picker (#22)
        - [ ] Provide "import" option to upload the file
      - [ ] Uploaded/Internally-Generated file picker
      - [ ] Pick datasets published locally (#132)
      - [ ] File uploader
        - [ ] Add a progress indicator for uploads (#20)
      - [ ] Provide a way to specify 
      - [ ] Format-specific options
        - [ ] CSV (Header, Field Delimiter, Line Delimiter(s)) (#145)
        - [ ] Google Sheets "Picker"
      - [ ] Specify a "key" column
    - [ ] Publish Dataset
      - [ ] Publish Locally (#132)
      - [ ] "Save" file picker for local filesystem
- [ ] Generate Swagger

### Spreadsheet View

- [x] Basic data viewing in an infinite scrolling list
  - [x] Find a way to do partial display of scrolling lists w/o giant piles of HTML (p)
  - [x] Add caching to the existing dataset retrieval code
  - [x] Dynamic caching of parts of the data in the frontend
    - [x] Spinners on cells not loaded yet
  - [x] Provide a way to prefill data that's been cached in the dataset message
- [x] Basic editing in-situ
  - [x] Add a websocket-based interface for spreadsheet sessions
  - [x] Add an "editor" mode for spreadsheets that connects to the websocket instead of the REST API.
  - [x] Add an "editor" mode for individual spreadsheet cells.
  - [x] Edited cells are sent to the backend through the websocket
  - [x] The websocket sends updates for specific cells back to the frontend
    - Want to gate updates by which cells are (a) visible, (b) cached.
  - [x] Add support for row-level formulas
  - [X] Add support for arbitrary formulas
  - [ ] Add support for registering an invalid formula so that the user can revisit it to continue editing.
  - [ ] Add support for editing existing formulas
  - [ ] Add UX for selecting rows/multiple cells/columns (#54)
  - [ ] Add UX for copy/pasting formulas
  - [ ] Add UX for inserting/deleting/moving rows
  - [ ] Add UX for inserting/deleting/moving columns
  - [ ] Add "Create a chart" UX (#50)
- [ ] Editing outputs a vizual cell
  - [ ] Allow outputs to be saved as a vizual script cell (#21)
  - [ ] Allow outputs to be read in from a vizual script cell
  - [ ] Automated "repair" of vizual cell problems (changed schema/missing rowids/etc...)
  - [ ] (optional) Live feedback for editing with downstream notebook operations
  - [ ] Live feedback for upstream changes modifying the source data
    - [ ] Triage mode for resolving changes to schema
    - [ ] Record/diff rowids to which updates are applied ()
- [ ] Undo support
  - [ ] Add an "Undo log"
  - [ ] Show the last n entries from the undo log in a sidebar (#49)
- [ ] Support for Caveats
  - [ ] Propagate caveats through cells/rows
  - [ ] (togglable) Caveat display on right (clicking caveat opens sidebar) (#47)
  - [ ] Mouse-over caveat highlights cell(s), mouse-over cell highlights caveats 
  - [ ] Display Global caveats (#61)

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
