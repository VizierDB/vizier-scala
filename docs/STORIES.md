## Use Case 1: Teacher

#### Task 1: Load Enrollment Data

**Context:** Nearly every data-oriented task I do for class involves working with course enrollments.  I have several sources of enrollment data that need to be combined together and filtered (e.g., to drop students who have resigned).  

**Data Sources**

* Official class list: These are complete, but contain less information about each student.
* Departmental class lists: These contain a lot of information (e.g., full student names, preferred names, email addresses, etc...), but do not include students enrolled from other departments.

**Workflow Evolution**

* I started by loading a the departmental class list.
    * This was the first year I've taught two sections of the same class.  I loaded the class lists for both classes and combined them together with a SQL cell.
        * The "Union two datasets together" operation is pretty common.  It might be handy to have a union cell to automate it.
        * I then published the data set locally to allow other notebooks to access it.
* At various points during the semester, I updated the departmental class lists.  During this process, I realized that the data set still contained students who had resigned the class.  
    * I explored the data set a bit (using the list view) and observed that the resigned students had their Grade attribute set to `Resigned YYYY-MM-DD`.  
    * I confirmed this observation by writing a simple SQL DISTINCT query to list the contents of the column.
        * In retrospect, I could have also used the profiler, but this was a profiling attribute that I was going to want to keep a close eye on throughout the semester, so highlighting it made sense.
            * Doubly so, since a data set I loaded significantly later had some resigned students marked with just an `R`.
    * After confirming that the grades had the pattern I expected, I added a SQL query to filter out students with `grade NOT LIKE 'Resigned%'`
        * Eventually, this turned into `grade NOT LIKE 'RESIGNED %' AND grade <> 'R'`
* At one point during the semester, I wanted to make some visualizations to illustrate a point: 
    1. I wanted to create a histogram of first letter of email address
        * I needed to preprocess the data with a SQL query to generate the histogram inputs
        * I created a simple bar chart on the histogram data.
    2. I wanted to create a histogram of the student's ID # mod 26.
        * Same as above, I preprocessed the data with a SQL Query and created a simple bar chart afterwards
* At the midterm, I discovered that the departmental class list was incomplete.  About a half-dozen students were registered from other departments (e.g., math).  I didn't do anything immediately, but...
* Towards the end of the semester, I noticed that some students had vanished from the class list (specifically, the size of the dataset was significantly lower than I expected it to be).  
    * At this point, it was quite useful to have the reminder that I was filtering out students who resigned, and I adjusted the SQL query to count the number of resigned students being filtered out.
    * This turned out to not be the explanation.  I spent a bit of time debugging.  
    * Combined with the missing students from the midterm, I decided to go to the official university-provided class list
* Unfortunately, the university doesn't publish **machine-readable** class lists... 
    * The class list is accessible as an HTML table.  For each class, I used a trick I'd learned a long time ago: Copying the contents of the table and pasting them into a spreadsheet (in this case, LibreOffice).  The resulting spreadsheet ended up a bit messy (lots of extraneous data like row numbers), but three columns were salvageable: University ID #, Full Name (as Last Name, Comma, First Name), and Status ('R' for resigned or empty otherwise).  
        * a few other columns were also inferable.  I had two HTML tables, one for each section, and a lot of missing details were also consistent across all students in the section. 
        * I saved the extracted fields via Libre Office and imported into Vizier
        * I then used Vizier to filter out students with an 'R' status (resigned)
    * With the class list loaded, I used a `LEFT OUTER JOIN` to populate fields in the official class list for the A section with their counterparts from the departmental list
        * Some field
        * Fields for students from other departments were, as expected, missing.  I used the `coalesce` SQL function to assign defaults to some of the missing fields
            * Some fields were consistent by section.  These I could fill in with constants.
            * The `name` and `id #` fields were provided by the new data set
            * I could extract First Name and Last Name by splitting the new dataset on a comma.  This wasn't guaranteed to work, but with only five records was a simple enough hack.  Regular expression-based extraction was the way to go here.
        * The resulting query was... pretty messy.  I could see this being automated with some sort of a "enrich dataset" operation.  (See below)
        * Some of the resulting fields were data (e.g., email) that I could look up manually (easier than creating script)
            * I opened up the relevant datasets in spreadsheet mode and manually copy/pasted in email addresses for the missing handful of students.
            * **Pain Point**: There's no way to easily view the results of a sequence of spreadsheet operations and/or to re-open the final result.  If I ever need to adjust the results, this is asking for pain.
            * **Pain Point**: There's no way to easily identify rows by a key attribute (e.g., ID #).  Rows are assigned ID #s by the system, and if the file changes, the system can't always reassign the same ID number to the same row.
    * I repeated the entire process (duplicating all of the relevant cells) for the B section.

**Enrich Official Class-List Query**

```
SELECT coalesce(c.courseid, 4555) as courseid,
       coalesce(c.classnumber, 10573) as classnumber,
       c.termsourcekey,
       c.term,
       coalesce(c.classnumbersection, 'CSE 250LR A') as classnumbersection,
       h.person_num as employeeid,
       c.institution,
       c.academiclevel,
       c.gendersourcekey,
       c.gender,
       c.grade,
       c.majorsourcekey,
       c.major,
       c.faculty,
       c.coursetypesourcekey,
       c.coursetype,
       c.classmeetingpattern,
       c.principal,
       coalesce(c.name, h.name) AS name,
       coalesce(c.last_name, regexp_extract(h.name, '([^,]+),([^,]+)', 1)) AS last_name,
       coalesce(c.first_name, regexp_extract(h.name, '([^,]+),([^,]+)', 2)) AS first_name,
       coalesce(c.classsection, 'A') as classsection,
       c.room,
       c.classstarttime,
       c.classendtime,
       c.gradingbasis
FROM (SELECT * FROM hub_section_a WHERE status <> 'R') h left outer join section_a c ON c.employeeid = h.person_num
```

#### AI Quiz

**Context:** I held a simple online quiz at the start of the semester to make sure the entire class was on the same page about what constitutes an academic integrity violation.  It was a requirement that everyone in the class complete the quiz by a specific cut-off date or get an F in the class.  I needed a way to quickly check the list of students who completed the quiz against the enrollment list.  

* Step one was gathering enrollment data.  See above for that discussion.  I then imported the dataset into the new notebook.
    * **Pain Point**: I had to copy/paste the enrollment dataset url from the other notebook.  This could have been handled more cleanly
    * **Pain Point**: When the enrollment data changes, I don't want to force a rerun of all of the notebooks that depend on it... but there's also no way to flag that a new "version" of the data is available in the load dataset cell.
* I wrote a simple SQL query to look up students enrolled that didn't have a submitted exam so I could email out warning notifications.
* One of my emails bounced.  It turned out that there was an unexpected record in the enrollment data.  After digging through the system to figure out how a typo could have gotten into the result (and working with the departmental data guy), we came up with a likely hypothesis: A student had changed their name, and as a result their email address was changed to match.

#### Final Exam

**Context:** I'd done a lot of the midterm planning by hand.  For the final exam, I wanted things to be a bit more robust.  

* From the school (dept &lt; school &lt; university), I got a list of students (first and last names) who had a scheduling conflict for my final exam.
   * Challenge: I wasn't completely sure that the names would line up correctly, so I used a left-outer-join on the enrollment dataset to "enrich" the dataset with their email addresses
   * Several students weren't being enriched properly, but I was able to verify that these students had dropped the class (the school's conflict checker did not consider resigns).  I removed these from the conflict list.
   * I wrote a quick python script to format the conflicts in an email "To:" list.  I emailed these students to ask whether they could make a designated make-up time.
   * I added a "Status" column to the dataset, and started manually marking conflicted students based on how they responded to my email
* Next, I wanted to create a dataset that I could use to generate cover sheets for the exams.  The cover sheet needs some basic bigraphical information on it (section, email, student ID, student name), as well as a seat number, an exam variant, and the path to a student photo (so we don't need to check IDs).
    * I started by writing a simple SQL query to pick specific columns of interest (section, email, student ID, student name).  In the query, I also dropped students who would be taking their exams at a separate testing center for accessibility reasons.
    * I have two CSV files that store the room layout.  One CSV file is meant to be loaded into a spreadsheet and pictures the room layout visually, so there are a large number of "empty" cells.  The other CSV file has, for each seat position, a corresponding exam variant (to avoid adjacent/nearby seats getting the same variant).  
        * Loading these was a bit beyond the standard data loading capabilities of the system, so I wrote a simple python script that (see below):
            1. opened the two files and read them in
            2. merged the two files together (creating seat #/exam variant 2-tuples)
            3. filtered out "empty" cells without a corresponding seat
            4. shuffled the seats
            5. scanned through the enrollment dataset, annotating each line with the next seat number and its corresponding variant
    * I also have a collection of photos obtained from the departmental course list.  This collection is incomplete, but stored in a directory, with each photo stored using the base of the student's email address (e.g., `okennedy.jpeg`)
        * `cd Photos; ls > ../available_photos.csv`
        * I wrote a simple SQL query (below) to get the student's email address (UBIT), and produce an email address/path to photo pair.
        * I used a left outer join to enrich the existing dataset with the photo and reorder some columns.
            * Since not everyone had a photo, I put in a default.
    * **Pain Point**: Vizier does not have a way to easily unload a CSV file to a specific path (Spark's CSV writer dumps stuff out to a directory)

**Generating email To: string**

```
print(", ".join(
    "\"{}\" <{}@buffalo.edu>".format(row["name"], row["principal"])
    for row in vizierdb["conflicts"].rows
))
```


**Assigning exam variants**

```
import random

with open("Exam/Classrooms/nsc-225-double-variants.csv") as f:
    seat_exams = [
        seat.strip()
        for row in f.readlines()
        for seat in row.split(",")
    ]

with open("Exam/Classrooms/nsc-225-double.csv") as f:
    seats = zip([
        seat.strip()
        for row in f.readlines()
        for seat in row.split(",")
    ], seat_exams)

seats = [
    (seat, variant)
    for (seat, variant) in seats
    if seat != ""
]

print("{} seats".format(len(seats)))

random.seed(1337)
random.shuffle(seats)

print("Assigning Seats...")

# Get object for dataset with given name.
ds = vizierdb.get_dataset('covers')
ds.insert_column("seat", 'string')
ds.insert_column("exam_variant", 'int')
for row in ds.rows:
    seat, variant = seats.pop()
    row["seat"] = seat
    row["exam_variant"] = int(variant)

print("Saving...")

ds.save()
ds.show()
```

**Extracting Photos**

```
SELECT regexp_extract(photo, '([a-zA-Z0-9]+)', 1) AS ubit,
       concat('photos/', photo) as photo
FROM photos
```

and then...

```
SELECT c.section,
       c.ubit,
       c.person_num,
       coalesce(p.photo, 'no_photo.pdf') AS photo,
       name, 
       seat,
       exam_variant
FROM covers c LEFT OUTER JOIN photos p on c.ubit = p.ubit
```