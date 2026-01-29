# Using AI to simulate vocabulary acquisition in children
This project aims to use JaCaMo to develop a simulation of vocabulary acquisition in children.  
JaCaMo is built from three major components, Jason (AI Agent configuration), Cartago (environment representation & abstraction), and Moise (organisation structure & definition).

It was developed on Linux and Mac computers however through the use of [Gradle](https://gradle.org/) it is possible to run this simulation on Windows computers.

The code contained here is provided 'as-is' with no warranties or guarantees of any sort, as far as legally permitted.

## Prerequesites
It is the user's responsibility to provide the book texts and utterances; these are not included due to licensing restrictions.
### Corpus of book texts
This is a directory containing as many text files as the user desires.

**NOTE:** One `.txt` file per book, using `UTF-8` encoding.

*Configuration setting:*
```toml
[environment.locations]
books = "./books/"
```

### Corpus of utterances
This is a directory containing `.csv` files containing the utterances to be spoken, adhering to the naming & formatting conventions used in [CHILDES](https://talkbank.org/childes/).

At a minimum, two files are required in this directory:
|File type|Naming requirements|Columns Required|
|---|---|---|
|Utterances|<ol><li>Contains `utterances`</li><li>Ends with `.csv`</li></ol>e.g. `spoken-utterances.csv`|`speaker_code`, `gloss`|
|Partcipant  metadata|<ol><li>Contains `partcipants`</li><li>Ends with `.csv`</li></ol>e.g.`partcipants_metadata.csv`|`role`, `id`|   
---
*Configuration setting:*
```toml
[environment.locations]
utterances = "./utterances/"
```

### Graphviz *(optional, recommended)*
[Graphviz](https://graphviz.org) is required to fully utilise the web-based interogation feature.  After installation, users will be able to see graphical representations of Moise component of the JaCaMo system.

It is left as an exercise for the user to install `graphviz`, should they wish to.  Refer to [Graphviz Downloads](https://graphviz.org/download/) for further instructions.

## Running the simulation
Windows users: `gradlew.bat` is the entry point.  
Mac/Linux users: `gradlew` is a shell script which needs to be made executable before running.

Additionally, opening the project folder in VSCode, with the 'Gradle for Java' extension (Author: vscjava) loaded, will allow the user to run the simulation

## Inspecting the simulation
During simulation execution, the state of the system can be interogated through 3 web pages: [Jason Mind Inspector](http://localhost:3272), [Moise Web View](http://localhost:3271), and [CArtAgO Web View](http://localhost:3273).

## Simulation output
Output will appear in a terminal/console window, a sample of which is shown below.
```
$ ./gradlew

> Task :run
Runtime Services (RTS) is running at 127.0.0.1:63829
Agent mind inspector is running at http://127.0.0.1:3272
CArtAgO Http Server running on http://127.0.0.1:3273
Looking for config file in: <OBFUSCATED>/child_vocabulary_acquisition
Found config file: <OBFUSCATED>/child_vocabulary_acquisition/simulation.conf
Attempting to parse it.
Configuration loaded:
[agents]
   child = 1
   max_siblings = 0
   percent_single_parents = 100.0
   age_start = 3
   age_finish = 5
[[ses]]
   name = Working
   qty = 17514
[[ses]]
   name = Welfare
   qty = 8767
[[ses]]
   name = Professional
   qty = 30142

Starting time is: 2026-01-29T13:10:11.632192
Loading utterances: 996149 entries from 60 files in 691ms.
Loading participant metadata: 6204 entries from 60 files in 5ms.
Attempting to load books from: <OBFUSCATED>/child_vocabulary_acquisition/./src/resources/Books
Moise Http Server running on http://127.0.0.1:3271
[Moise] OrgBoard learning_environ created.
[Moise] scheme created: readingBooksClassroom: classroom_reading_books using artifact SchemeBoard
[Moise] group created: class: classroom using artifact ora4mas.nopl.GroupBoard
[Moise] group created: village: village using artifact ora4mas.nopl.GroupBoard
[Cartago] Workspace home_environ created.
[Cartago] artifact utterances: vocab.Utterances() at home_environ created.
[Cartago] Workspace classroom created.
[Cartago] artifact library: vocab.Library() at classroom created.
[Cartago] Workspace sync created.
[Cartago] artifact datalogger: vocab.DataLogger() at sync created.
[Cartago] artifact synchroniser: vocab.Synchroniser() at sync created.
[parent1] join workspace /main/home_environ: done
[child1] join workspace /main/classroom: done
[classroom_teacher] join workspace /main/classroom: done
[parent1] join workspace /main/sync: done
[parent1] focusing on artifact utterances (at workspace /main/home_environ) using namespace utterances
[classroom_teacher] join workspace /main/learning_environ: done
[classroom_teacher] join workspace /main/sync: done
[classroom_teacher] focusing on artifact library (at workspace /main/classroom) using namespace default
[parent1] focus on utterances: done
[parent1] focusing on artifact synchroniser (at workspace /main/sync) using namespace sync
[classroom_teacher] focus on library: done
[classroom_teacher] focusing on artifact synchroniser (at workspace /main/sync) using namespace sync
[classroom_teacher] focus on synchroniser: done
[classroom_teacher] focusing on artifact class (at workspace /main/learning_environ) using namespace default
[classroom_teacher] focus on class: done
[classroom_teacher] focusing on artifact learning_environ (at workspace /main/learning_environ) using namespace default
[classroom_teacher] focus on learning_environ: done
[child1] join workspace /main/sync: done
[child1] focusing on artifact library (at workspace /main/classroom) using namespace default
[child1] focus on library: done
[child1] focusing on artifact synchroniser (at workspace /main/sync) using namespace sync
[child1] focus on synchroniser: done
[child1] focusing on artifact datalogger (at workspace /main/sync) using namespace sync
[child1] focus on datalogger: done
[child1] focusing on artifact village (at workspace /main/learning_environ) using namespace default
[child1] focus on village: done
[child1] focusing on artifact learning_environ (at workspace /main/learning_environ) using namespace default
[child1] focus on learning_environ: done
[parent1] focus on synchroniser: done
[parent1] focusing on artifact village (at workspace /main/learning_environ) using namespace default
[parent1] focus on village: done
[parent1] focusing on artifact learning_environ (at workspace /main/learning_environ) using namespace default
[parent1] focus on learning_environ: done
[classroom_teacher] I am obliged to commit to tellStudentsToRead on readingBooksClassroom... doing so
Finished day 1 of year 1.
Finished day 2 of year 1.
Finished day 3 of year 1.
Finished day 4 of year 1.
Finished day 5 of year 1.
Starting a new year
Finished day 1 of year 2.
Finished day 2 of year 2.
Finished day 3 of year 2.
Finished day 4 of year 2.
Finished day 5 of year 2.
Starting a new year
Finished day 1 of year 3.
Finished day 2 of year 3.
Finished day 3 of year 3.
Finished day 4 of year 3.
Finished day 5 of year 3.
Data file successfully written.
Shutting down environment...
[GroupBoard] parent1 has quit, role adult removed by the platform!
[GroupBoard] parent1 has quit, role parent removed by the platform!
[SchemeBoard] classroom_teacher has quit, mission tellStudentsToRead removed by the platform!
[GroupBoard] classroom_teacher has quit, role teacher removed by the platform!
[GroupBoard] child1 has quit, role student removed by the platform!
[GroupBoard] child1 has quit, role child removed by the platform!
[GroupBoard] child1 has quit, role offspring removed by the platform!
Shutdown complete.
Results saved to: 26-01-29-13-10-11.csv
```
This is a mixture of output from JaCaMo and simulation output.  Most lines begining `[...]` are from JaCaMo while most of the rest are generated by the code developed for this simulation environment.

Additionally, after completing the simulation, the results are saved to a `.csv` file alongside the `gradlew`/`gradlew.bat` files.  The file name is timestamped with when the simulation started running, with the name being of the form `YY-mm-dd-HH-MM-SS.csv`, encoding the year, month, day, hour, minute, and second, respectively, to ensure multiple simulation runs can be performed without overwriting previous results.

## Configuration of simulation
The only file which needs to be edited, at any time, is `simulation.conf`, which is the main configuration file.

### Primary configuration: `simulation.conf`
This is the primary configuration file of the simulation and the only one which will be explained in this document.

The file is split into 3 sections, all optional: `[agents]`, `[[ses]]`, and `[environment.locations]`.

Unexpected headings and key/value pairs are ignored as much as possible.  
Any syntax errors within the file will result in the default settings being used.  
Refer to [TOML](https://toml.io) for full details of the syntax.  
**In short**:
- each line must be a section heading ('table' in TOML-speak, `[heading]`), an array of tables (`[[table]]`) or a key-value pair (`key = value`)
- Whitespace is ignored, except within strings (which are surrounded by `'` or `"`.)
- Comments begin at a `#` and continue to the end of the line
    - except when it appears within a string

#### Agent configuration, `[agents]`
At the time of writing, 5 'keys' make up this section, all of which are optional: `child`, `max_siblings`, `percent_single_parents`, `age_start`, and `age_finish`.

**Defaults:**
```toml
[agents]
child = 10
max_siblings = 0
percent_single_parents = 100.0
age_start = 3
age_finish = 8
```
Refer to `simulation.conf` for details of the permitted ranges for these values (except `child`, where the onus is on the user to give a sensible value).

#### Socio-Economic Statuses, `[[ses]]`
Unlike the `[agents]` section, it is permissible to have multiple `[[ses]]` lines, as they define a table within an array of tables.  
Under each `[[ses]]` heading, two keys are expected: `name`, and `qty`, representing the name of the SES category and the daily quantity of words to be 'uttered'/'spoken' to the child.
If either key is missing that section is considered invalid, and is ignored.
If all `[[ses]]` entries are invalid (or missing), the default options are used.

**Defaults:**
```toml
[[ses]]
name="Welfare"
qty=8767
[[ses]]
name="Working"
qty=17514
[[ses]]
name="Professional"
qty=30142
```

#### Environment configuration, `[environment.locations]`
At present, only two 'keys' are recognised in this section: `books`, and `utterances`.  
These support both relative and absolute paths.  
Case-sensitivity is dependent on the underlying filesystem, both for directory and file names.  
Windows 10 and Windows 11 support marking individual directories as being case-sensitive.  

> [!WARNING]
> It is the user's responsibility to make sure the directories exists; this will cause an error which isn't able to be recovered from, however the simulation will only get 'stuck' rather than crashing out altogether.

**Defaults:**
```toml
books = "./resources/books"
utterances = "./resources/utterances"
```

### Other configuration files
There are several other configuration files, for various parts of the Gradle and JaCaMo systems; they can be edited if the user wishes to.  