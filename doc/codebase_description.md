---
title: "Child Vocabulary Acquisition Simulation -- Codebase Description"
subtitle: "Developer Reference"
---

# Table of Contents

1. [Introduction](#introduction)
2. [Project Structure](#project-structure)
3. [Architecture Overview](#architecture-overview)
   - [JaCaMo Framework](#jacamo-framework)
   - [System Architecture Diagram](#system-architecture-diagram)
4. [Build System and Dependencies](#build-system-and-dependencies)
   - [Gradle Configuration](#gradle-configuration)
   - [Dependencies](#dependencies)
   - [Build Tasks](#build-tasks)
5. [Configuration System](#configuration-system)
   - [simulation.conf](#simulationconf)
   - [ConfigFile.java](#configfilejava)
   - [Supporting Classes](#supporting-configuration-classes)
6. [Custom Launcher](#custom-launcher)
   - [VocabLauncher.java](#vocablauncherjava)
7. [Agent Layer (Jason / AgentSpeak)](#agent-layer)
   - [Common Includes](#common-includes)
   - [Child Agent (child.asl)](#child-agent)
   - [Parent Agent (parent.asl)](#parent-agent)
   - [Teacher Agent (teacher.asl)](#teacher-agent)
8. [Environment Layer (CArtAgO Artefacts)](#environment-layer)
   - [Library.java](#libraryjava)
   - [Utterances.java](#utterancesjava)
   - [Synchroniser.java](#synchroniserjava)
   - [DataLogger.java](#dataloggerjava)
   - [WordsLearnt.java](#wordslearntjava)
9. [Organisational Layer (Moise)](#organisational-layer)
   - [learning_environ.xml](#learning_environxml)
10. [JaCaMo Project File](#jacamo-project-file)
11. [Simulation Lifecycle](#simulation-lifecycle)
    - [Startup Sequence](#startup-sequence)
    - [Daily Cycle](#daily-cycle)
    - [Yearly Progression](#yearly-progression)
    - [Shutdown Sequence](#shutdown-sequence)
12. [Word Learning Algorithm](#word-learning-algorithm)
13. [Concurrency and Synchronisation](#concurrency-and-synchronisation)
14. [Data Flow](#data-flow)
15. [Logging Configuration](#logging-configuration)
16. [Known Limitations and Unimplemented Features](#known-limitations)

---

# 1. Introduction {#introduction}

This document provides a detailed, developer-focused description of the Child Vocabulary Acquisition (CVA) simulation codebase. It is intended for developers who wish to understand, maintain, or extend the system.

The CVA simulation models how children acquire vocabulary through exposure to spoken language (at home) and written text (at school), with exposure volumes determined by socio-economic status (SES). It is built on the JaCaMo multi-agent platform, version 1.3.0.

---

# 2. Project Structure {#project-structure}

```
child_vocabulary_acquisition/
+-- build.gradle                          # Gradle build configuration
+-- settings.gradle                       # Gradle settings
+-- child_vocabulary_acquisition.jcm      # JaCaMo project definition
+-- simulation.conf                       # Runtime configuration (TOML)
+-- logging.properties                    # Java logging configuration
+-- README.md                             # Project readme
+-- gradlew / gradlew.bat                 # Gradle wrapper scripts
+-- src/
|   +-- agt/                              # Agent source files (AgentSpeak)
|   |   +-- child.asl                     # Child agent plans
|   |   +-- parent.asl                    # Parent agent plans
|   |   +-- teacher.asl                   # Teacher agent plans
|   |   +-- inc/                          # Shared agent includes
|   |   |   +-- common.asl               # Plans common to all agents
|   |   |   +-- child_common.asl          # Plans common to child agents
|   |   |   +-- adult_common.asl          # Plans common to adult agents
|   +-- env/                              # Java environment classes
|   |   +-- VocabLauncher.java            # Custom JaCaMo launcher
|   |   +-- Synchroniser.java            # Simulation coordination artefact
|   |   +-- Library.java                  # Book corpus artefact
|   |   +-- Utterances.java              # Speech corpus artefact
|   |   +-- DataLogger.java              # Statistics recording artefact
|   |   +-- WordsLearnt.java             # Word-level tracking artefact
|   |   +-- ConfigFile.java              # TOML configuration parser
|   |   +-- Agents.java                  # Agent configuration data holder
|   |   +-- Environment.java             # Environment path configuration
|   |   +-- SES.java                     # Socio-economic status model
|   |   +-- CircularQueue.java           # Generic circular queue utility
|   |   +-- WordCountRow.java            # Annual statistics record
|   |   +-- WordLearntRow.java           # Learned word record
|   |   +-- HashMapBB.java              # Custom indexed belief base
|   +-- int/                              # Interaction definitions (empty)
|   +-- org/                              # Moise organisation definitions
|       +-- learning_environ.xml          # Organisational specification
```

---

# 3. Architecture Overview {#architecture-overview}

## JaCaMo Framework {#jacamo-framework}

JaCaMo integrates three technologies:

- **Jason** -- A BDI (Belief-Desire-Intention) agent programming language based on AgentSpeak(L). Agents are defined in `.asl` files containing beliefs, goals, and plans.
- **CArtAgO** -- Common ARTifact infrastructure for AGents in Open environments. Provides the artefact abstraction for environment entities that agents interact with via operations and observable properties.
- **Moise** -- A model for organisational structures. Defines roles, groups, schemes, and norms that constrain agent behaviour.

## System Architecture Diagram {#system-architecture-diagram}

The system comprises four workspaces:

| Workspace | Artefacts | Purpose |
|---|---|---|
| `classroom` | `Library` | School environment -- book reading |
| `home_environ` | `Utterances` | Home environment -- speech exposure |
| `sync` | `Synchroniser`, `DataLogger`, `WordsLearnt` | Coordination and data recording |
| `learning_environ` | (Moise groups and schemes) | Organisational structure |

**Agent-artefact interactions:**

- **Child agents** focus on: `Library`, `Synchroniser`, `DataLogger`, `WordsLearnt`, plus Moise group artefacts.
- **Parent agents** focus on: `Utterances`, `Synchroniser`, plus Moise group artefacts.
- **Teacher agent** focuses on: `Library`, `Synchroniser`, plus Moise group artefacts.

---

# 4. Build System and Dependencies {#build-system-and-dependencies}

## Gradle Configuration {#gradle-configuration}

The project uses Gradle with the Shadow plugin for fat JAR creation. Key configuration in `build.gradle`:

- **Entry point:** `vocab.VocabLauncher` (overrides the default `jacamo.infra.JaCaMoLauncher`)
- **Source sets:** `src/env`, `src/agt`, `src/org`, `src/int`, `src/java`
- **Default task:** `run`

## Dependencies {#dependencies}

| Dependency | Version | Purpose |
|---|---|---|
| `org.jacamo:jacamo` | 1.3.0 | Multi-agent platform |
| `com.opencsv:opencsv` | 5.12.0 | CSV file parsing and writing |
| `com.electronwill.night-config:toml` | 3.8.3 | TOML configuration file parsing |
| `com.gradleup.shadow` | 8.3.3 | Fat JAR packaging (Gradle plugin) |

## Build Tasks {#build-tasks}

| Task | Description |
|---|---|
| `gradle` / `gradle run` | Compile and run the simulation |
| `gradle shadowJar` | Create a self-contained executable JAR |
| `gradle clean` | Remove `bin/`, `build/`, and `log/` directories |

---

# 5. Configuration System {#configuration-system}

## simulation.conf {#simulationconf}

The runtime configuration file uses TOML syntax and contains three sections:

**`[agents]`** -- Controls agent population:

| Key | Type | Default | Range | Description |
|---|---|---|---|---|
| `child` | int | 10 | User discretion | Number of child agents |
| `max_siblings` | int | 0 | 0--2 | Siblings per child (not implemented) |
| `percent_single_parents` | float | 100.0 | 0.0--100.0 | Percentage of single-parent households |
| `age_start` | int | 3 | 0--5 | Starting age of children |
| `age_finish` | int | 8 | 5--10 | Finishing age of children |

**`[[ses]]`** -- SES categories (array of tables). Each entry requires `name` (string) and `qty` (int) keys. Defaults: Welfare (8,767), Working (17,514), Professional (30,142).

**`[environment.locations]`** -- Paths to data directories:

| Key | Default | Description |
|---|---|---|
| `utterances` | `./resources/utterances` | Directory containing utterance CSV files |
| `books` | `./resources/books` | Directory containing book text files |

## ConfigFile.java {#configfilejava}

**Package:** `vocab`
**Location:** `src/env/ConfigFile.java`

Parses `simulation.conf` using the NightConfig TOML library. Key design decisions:

- All settings have compile-time defaults (defined as `static final` constants).
- Out-of-range numeric values are clamped rather than rejected.
- If `age_start == age_finish`, `age_finish` is incremented by 1.
- If no valid `[[ses]]` entries exist, the three defaults are used.
- Missing or blank file paths fall back to default resource locations.

**Public API:**

- `static ConfigFile createDefault()` -- Returns a ConfigFile with all defaults.
- `static ConfigFile parse(Path configFile)` -- Parses the given TOML file.
- `Agents getAgents()` / `HashSet<SES> getSES()` -- Accessors.

## Supporting Configuration Classes {#supporting-configuration-classes}

**Agents.java** (`src/env/Agents.java`)
Simple data holder with getters for child count, max siblings, percent single parents, age start, and age finish. Constructed by `ConfigFile`.

**SES.java** (`src/env/SES.java`)
Represents a socio-economic status category with a name and daily word quantity. Equality is determined solely by name (via `equals`/`hashCode` override), allowing `HashSet` to deduplicate categories. Provides a `defaults()` factory method returning the three standard categories.

**Environment.java** (`src/env/Environment.java`)
Holds two path strings: `utteranceLocation()` and `bookLocation()`.

**CircularQueue.java** (`src/env/CircularQueue.java`)
Generic circular queue (`CircularQueue<T>`) used for round-robin distribution of SES categories across parent agents. Supports `add(Collection)` and `get()` (which advances and wraps the internal pointer).

---

# 6. Custom Launcher {#custom-launcher}

## VocabLauncher.java {#vocablauncherjava}

**Package:** `vocab`
**Location:** `src/env/VocabLauncher.java`
**Extends:** `jacamo.infra.JaCaMoLauncher`

The custom launcher is the application entry point. It overrides `createAgs()` to inject dynamically generated agents before JaCaMo processes the `.jcm` file.

### Startup sequence within `createAgs()`:

1. **`loadCustomConfig()`** -- Locates and parses `simulation.conf` from the working directory.
2. **`initialiseArtifacts()`** -- Calls static initialisers on `DataLogger`, `Synchroniser`, `Utterances`, and `Library` to pre-load data before the MAS starts.
3. **`addChildAgents()`** -- Creates N child agents from a template `JaCaMoAgentParameters`:
   - Source file: `child.asl`
   - Belief base: `HashMapBB` with indexed pattern `word(key,_)` for O(1) word lookups
   - Artefact focuses: `classroom.library`, `sync.synchroniser`, `sync.datalogger`, `sync.wordsLearnt`
   - Role: `child` in the `village` group
   - Initial beliefs: `age(X)`, `activityState("Idle")`
   - Initial goals: `join_school`, `join_home_group(home_N)` (with randomised home assignment)
4. **`addParentAgents()`** -- Creates parent agents:
   - Belief base: `HashMapBB` with indexed patterns `words_to_speak(_)` and `iterationsLeft(_)`
   - Total parents = `numFamily * (1 + (100 - percentSingleParents) / 100)`
   - First `numFamily` parents get `create_home_group(N)` goal and SES assignment via circular queue.
   - Extra parents (for two-parent households) get `join_home_group(home_N)` goal.
   - The SES queue starting position is randomised to avoid deterministic assignment.
5. **`addSiblingAgents()`** -- Placeholder; not implemented.
6. **`super.createAgs()`** -- Standard JaCaMo agent creation from the `.jcm` file (creates the teacher agent).

### Key design note:

The `main()` method is a near-verbatim copy of `JaCaMoLauncher.main()`, changed only to instantiate `VocabLauncher` instead of `JaCaMoLauncher`. The `create()` method is also overridden to remove the `createController()` call. The `createCustomPlatforms()` method is copied because it is `private` in the parent class.

---

# 7. Agent Layer (Jason / AgentSpeak) {#agent-layer}

All agents are written in AgentSpeak(L), Jason's agent programming language. Agents operate on a BDI (Belief-Desire-Intention) cycle: they maintain beliefs about the world, adopt goals (desires), and select plans (intentions) to achieve those goals.

## Common Includes {#common-includes}

### common.asl (`src/agt/inc/common.asl`)

Included by all agents. Provides:

- Standard JaCaMo template includes (`common-cartago.asl`, `common-moise.asl`, `org-obedient.asl`).
- `print_listItems` -- A recursive plan for printing list contents to the console (debugging utility).

### child_common.asl (`src/agt/inc/child_common.asl`)

Included by `child.asl`. Provides:

- **`setState(State)`:** Updates the `activityState` belief.
- **`join_home_group(HomeGroup)`:** Waits for the Moise group to exist, then focuses on it and adopts the `offspring` role.
- **`join_school`:** Waits for the classroom group, then focuses and adopts the `student` role.
- **`+sync::newYear` (atomic):** At year-end, waits until idle, then retrieves aggregate statistics by querying the belief base (`words::unique_seen`, `words::unique_heard`, and `.count` of learned words) and reports them to `DataLogger` via `addAnnualStats`.
- **`+sync::finalise` (atomic):** At simulation end, retrieves all learned words via `.findall(aoa(Word,Age), words::word(Word,[_,_,Age]) & Age\==0, WordAoA)` and reports them to `WordsLearnt` via `addLearnedWords`, then signals `childAgentFinalised`.
- **`+sync::agent_age(NewAge)` (atomic):** Updates the agent's `age` belief when the synchroniser increments the age.

### adult_common.asl (`src/agt/inc/adult_common.asl`)

Included by `parent.asl` and `teacher.asl`. Provides:

- `read_book_aloud_to_child(Title, Child)` -- Retrieves book text from the Library artefact and sends it to the child via `listen_to_speech`.

## Child Agent {#child-agent}

**File:** `src/agt/child.asl`
**Includes:** `child_common.asl`

### Word data structure

Each word is tracked as a belief in the `words` namespace: `words::word(Word,[Seen,Heard,AgeLearned])`, where:

- `Seen` -- number of times the word has been encountered in books
- `Heard` -- number of times the word has been encountered in speech
- `AgeLearned` -- the age at which the word was "learnt" (0 if not yet learnt)

Word data is stored in the agent's belief base using `HashMapBB` -- a custom belief base implementation (see Section 13) that provides O(1) indexed lookups by word. The first time a word is queried, a test-goal plan in `child_common.asl` creates the initial belief with zeroed counters.

### Plans

**`word_heard_checker(Word)`** -- Queries the word's belief via `?words::word(Word,[Seen,Heard,AgeLearned])`. If this is the first time the word has been heard (`Heard == 0`), increments the `words::unique_heard` counter. Then updates the word belief with the incremented `Heard` count via `+words::word(Word,[Seen,Heard+1,AgeLearned])`.

**`word_seen_checker(Word)`** -- Same as above but for the `Seen` count and `words::unique_seen` counter.

**`try_learn_word(Word)`** -- Queries the word's belief. If `AgeLearned == 0` and the word meets the learning threshold (see Section 12), updates the belief to set `AgeLearned` to the child's current age.

**`read_a_book(Title)` [source guard: must come from the teacher]** -- Retrieves book text from the Library, iterates over each word calling `word_seen_checker` and `try_learn_word`, then notifies the teacher of completion via `finishedReading(Title)`.

**`+listen_to_speech(Words)` [atomic]** -- Triggered when another agent (typically the teacher) speaks words directly. Iterates over the word list, calls `word_heard_checker` and `try_learn_word` for each, then sends `finishedListening` to the speaker.

**`+listen_to_utterances(Utterances, Counter)` [atomic]** -- Triggered by parent agents. Processes a batch of utterances (list of word lists). After processing, records a `finished(Counter, Parent)` belief. The corresponding `+finished(_,Parent)` plan waits until 20 batches have been received before sending `finishedUtterances` back to the parent. This batching approach (20 iterations) prevents the agent's messaging system from being overwhelmed by a single enormous message.

## Parent Agent {#parent-agent}

**File:** `src/agt/parent.asl`
**Includes:** `adult_common.asl`

### Plans

**`create_home_group(Number)`** -- Creates a Moise sub-group (`indv_home`) under the `village` group:

1. Looks up and focuses on the village artefact.
2. Creates a new group named `home_N`.
3. Adopts the `parent` role.
4. Waits for the group to be well-formed.
5. Waits for all expected parents to join (via `find_parents`).
6. Distributes household information (parent list, SES) to all residents.
7. Signals the synchroniser via `groupCreated`.

**`join_home_group(GroupName)`** -- For second parents in two-parent households. Waits for the group to exist, then focuses and adopts the `parent` role.

**`find_parents(NumParents)`** -- Recursive plan that checks if the expected number of parents have joined. On failure, waits 100ms and retries.

**`+sync::status("StartHome")`** -- Triggered by the synchroniser. Delegates to `speak_to_child`.

**`speak_to_child` [atomic]** -- Core speech delivery plan:

1. Determines the total daily word count from SES configuration.
2. Divides by the number of parents in the household.
3. Delivers words in 20 iterations, each requesting `DesiredWords = RemainingWords / Counter` words from the `Utterances` artefact via `getBulkUtterances`.
4. Sends each batch to the child via `listen_to_utterances`.

**`+finishedUtterances`** -- Triggered when the child confirms all 20 batches processed. Removes the belief, then signals `finishedHome` to the synchroniser.

## Teacher Agent {#teacher-agent}

**File:** `src/agt/teacher.asl`
**Includes:** `adult_common.asl`

The teacher agent is the only statically-defined agent (declared in the `.jcm` file rather than dynamically created).

### Initial belief

`students_found(0)` -- tracks how many students are in the class.

### Plans

**`find_my_students`** -- Waits for the synchroniser to reach "Ready" status, then:

1. Queries the classroom group for all agents playing the `student` role.
2. Records the student list and count.
3. Sends each student a belief identifying this agent as their teacher (`school::my_teacher(Me)`).
4. Signals `schoolReady` to the synchroniser.

**`read_books` [atomic]** -- The main daily plan, triggered by `StartSchool`:

1. Signals `startSchool` to the synchroniser.
2. Checks the children's age and selects a strategy:
   - Age < 5: Always reads aloud (`read_book_aloud_to_child`).
   - Age 5--7: 50% chance of reading aloud vs. telling students to read independently.
   - Age > 7: Always tells students to read independently.

**`tell_students_to_read_book` [atomic]** -- Selects a random book title, retrieves its word count, sends an `achieve` goal (`read_a_book(Title)`) to all students, and records `sent_instruction_to_read_book(Title, Student)` beliefs. Has a failure plan that silently succeeds if preconditions are not met.

**`+finishedListening` [atomic]** -- Triggered when a child finishes listening to a read-aloud session. Tracks completion via `finished(Child)` beliefs. When all students have reported, cleans up beliefs and signals `finishSchool` to the synchroniser.

**`+finishedReading(Title)` [atomic]** -- Triggered when a child finishes reading a book independently. Tracks completion by counting `finishedReading` beliefs. When all students have reported, cleans up `finishedReading` and `sent_instruction_to_read_book` beliefs and signals `finishSchool` to the synchroniser.

**`+sync::status("StartSchool")`** -- Resets the `read_books` goal for each new day via `resetGoal`.

---

# 8. Environment Layer (CArtAgO Artefacts) {#environment-layer}

All artefacts extend `cartago.Artifact` and reside in the `vocab` package.

## Library.java {#libraryjava}

**Location:** `src/env/Library.java`
**Workspace:** `classroom`

Manages the corpus of book texts.

### Data structures

- `HashMap<String, String[]> bookWords` -- Maps filename to pre-split word arrays. Populated lazily on first access to each book.
- `ArrayList<String> bookTitles` -- List of book filenames. Populated during static initialisation.
- `ReentrantLock loadingLock` -- Ensures thread-safe lazy loading.

### Static initialisation

`loadBookTitles(String path)` -- Called by `VocabLauncher.initialiseArtifacts()`. Scans the specified directory for `.txt` files and stores their filenames in the `bookTitles` list.

### Observable properties

- `book_count` -- Total number of books available.

### Operations

**`get_bookTitleRandomly()`** -- Returns a random book filename.

**`get_bookByTitle(String title)`** -- Returns the book's text as a `String[]` (pre-split word array). If the book hasn't been loaded yet, acquires `loadingLock` and reads the file from disk, splitting by spaces and filtering empty strings. Handles UTF-8 BOM (byte order mark: `EF BB BF`).

**`getWordCountByBookTitle(String title)`** -- Returns the word count of a book (loads the book if necessary). This exists because Jason's `.length` internal action causes a `StackOverflowError` on very long lists.

## Utterances.java {#utterancesjava}

**Location:** `src/env/Utterances.java`
**Workspace:** `home_environ`

Manages the corpus of child-directed speech utterances.

### Data structures

- `HashMap<String, ArrayList<String[]>> utterancesMap` -- Maps speaker codes to lists of pre-split utterance word arrays.
- `HashMap<String, ArrayList<String>> participantRoleToCodeMapping` -- Maps participant roles to speaker code lists.

### Static initialisation

`loadUtterances(String filePath)` -- Called by `VocabLauncher`. Delegates to:

1. `loadUtterances()` -- Processes CSV files containing `utterances` in their filename, extracting `speaker_code` and `gloss` columns. Utterances are pre-split into `String[]` word arrays during loading to avoid repeated splitting at access time.
2. `loadParticipants()` -- Processes CSV files containing `participants` in their filename, extracting `role` and `id` columns.

Both use the private helper `HashMapFromCSVFiles()` which:

- Validates the base path exists and is a directory.
- Filters files by `.csv` extension and filename substring.
- Uses OpenCSV's `CSVReaderHeaderAware` for header-aware parsing.
- Assumes the first requested column is the key, the second is the value.

### Observable properties

- `num_speaker_codes_available` -- Number of distinct speaker codes.
- `utterances_available` -- Total number of utterances loaded.

### Operations

**`getRandomUtterance()`** -- Returns a random speaker code, a random utterance from that speaker (as a word array), and the utterance length.

**`getBulkUtterances(double numWordsRequired)`** -- Accumulates random utterances until the total word count meets or exceeds `numWordsRequired`. Returns the collected utterances as an `Object[]` of `String[]` arrays and the actual word count provided.

## Synchroniser.java {#synchroniserjava}

**Location:** `src/env/Synchroniser.java`
**Workspace:** `sync`

The central coordination artefact that controls the simulation lifecycle through a state machine.

### State machine

```
NotReady -> Ready -> StartSchool -> SchoolStarted -> SchoolFinished
    -> StartHome -> HomeFinished -> (back to StartSchool, or newYear, or Finished)
```

### Static state

- `numFamily`, `numParents` -- Set during initialisation.
- `homeGroups`, `numGroupsCreated` -- Track home group creation during startup.
- `completedYearCount`, `cyclesCompleted` -- Track simulation progress.
- `parentsFinished` -- Counter for daily home-phase completion.
- `childrenFinalised` -- Counter for end-of-simulation finalisation.
- `CYCLES_PER_YEAR = 365` -- Days per simulated year.
- `ReentrantLock lock` -- Prevents duplicate end-of-day/end-of-simulation processing.

### Observable properties

| Property | Type | Description |
|---|---|---|
| `status` | String | Current lifecycle state |
| `year` | int | Current year number (1-based) |
| `day` | int | Current day number (1-based) |
| `agent_age` | int | Current age of child agents |
| `children_finished` | int | Number of children that have finalised |

### Operations

**`groupCreated(String groupName)`** -- Called by parent agents after creating a home group. When all families are registered, transitions to "Ready".

**`schoolReady()`** -- Called by the teacher. Transitions to "StartSchool". Guarded: only accepts calls from `classroom_teacher`.

**`startSchool()`** -- Called by the teacher. Transitions to "SchoolStarted". Guarded: teacher only.

**`finishSchool()`** -- Called by the teacher. Transitions through "SchoolFinished" to "StartHome". Guarded: teacher only.

**`finishedHome()`** -- Called by each parent agent. Increments `parentsFinished`. When the count reaches `numParents`, `tryLock()` ensures exactly one thread resets the counter and calls `endOfDay()`; concurrent threads that also see the condition met fail to acquire the lock and return without action.

**`childAgentFinalised()`** -- Called by each child at simulation end. Increments `childrenFinalised` and updates the `children_finished` observable property, then blocks (via `await("allChildrenFinished")`) until all children have reported. Once released, `tryLock()` ensures only one thread writes output files via `DataLogger.writeFiles()`, shuts down the MAS via `RuntimeServicesFactory.get().stopMAS()`, and prints the output file locations.

### Private methods

**`endOfDay()`** -- Increments the day counter. If more days remain in the year, transitions to "StartSchool". If the year is complete, signals `newYear`, increments the age, and either starts a new year or signals `finalise`.

**`isTeacher(String agentName)`** -- Guard helper that checks if the calling agent is `classroom_teacher`.

## DataLogger.java {#dataloggerjava}

**Location:** `src/env/DataLogger.java`
**Workspace:** `sync`

Records simulation statistics and writes output CSV files.

### Static state

- `timeStampString` -- Formatted start time used in output filenames (`YY-mm-dd-HH-MM-SS`).
- `ArrayList<WordCountRow> wordCountRows` -- Accumulated annual statistics.

### Operations

**`addAnnualStats(String agentName, String ses, int age, int uniqueSeen, int uniqueHeard, int learned)`** -- Called by child agents at each year-end. Appends a `WordCountRow` to the list.

### Static methods

**`initialise()`** -- Captures the start timestamp.

**`writeFiles()`** -- Called by the Synchroniser at simulation end. Writes two CSV files:

1. **Summary file** (`YY-mm-dd-HH-MM-SS summary.csv`): Columns: Name, SES, Age, Unique_Seen, Unique_Heard, Words_Learned.
2. **Word list file** (`YY-mm-dd-HH-MM-SS word_list.csv`): Columns: Word, Name, SES, Age.

**`outputSummaryLocation()` / `outputWordsLocation()`** -- Return the output file paths.

## WordsLearnt.java {#wordslearntjava}

**Location:** `src/env/WordsLearnt.java`
**Workspace:** `sync`

Tracks which words each agent learned, used for the word list CSV output.

### Data structure

`HashMap<String, ArrayList<AgentDetails>> wordAoA` -- Maps words to lists of `AgentDetails` records (name, SES, age, word). Uses a private `record AgentDetails`.

### Operations

**`addLearnedWords(String agentName, String ses, Object[] wordAoa)`** -- Called by child agents during finalisation. Parses the AgentSpeak term array (format: `aoa(Word, Age)`), extracting word and age via string splitting on `[(),"]`. Deduplicates by checking if the agent already has an entry for each word.

### Static methods

**`getAll()`** -- Returns all entries as `ArrayList<WordLearntRow>`, used by `DataLogger.writeFiles()`.

---

# 9. Organisational Layer (Moise) {#organisational-layer}

## learning_environ.xml {#learning_environxml}

**Location:** `src/org/learning_environ.xml`

Defines the organisational specification using Moise's XML schema.

### Structural specification

**Roles:** `adult`, `child`, `offspring`, `parent`, `student`, `teacher`

**Group hierarchy:**

```
learning_environment (root)
+-- classroom
|   +-- teacher (min: 1, max: 1)
|   +-- student (min: 1, max: unbounded)
+-- village
    +-- adult (min: 1)
    +-- child (min: 1)
    +-- indv_home (sub-group, min: 1)
        +-- parent (min: 1, max: 2)
        +-- offspring (min: 1, max: 1)
```

**Links:**

- Teacher has `authority` and bi-directional `communication` with students (intra-group).

**Formation constraints:**

- `offspring` and `student` roles are compatible (bi-directional), allowing a single agent to hold both roles simultaneously.

### Functional specification

**Scheme `classroom_reading_books`:**

- Root goal: `tell_class_to_read_a_book` (sequential plan)
  - Sub-goal 1: `find_my_students`
  - Sub-goal 2: `read_books`
- Mission `tellStudentsToRead`: encompasses both sub-goals.

**Scheme `speak_to_child`:**

- Root goal: `speak_an_utterance_to_child` (sequential plan)
  - Sub-goal: `speak_utterance`
- Mission `speakUtterances`: encompasses the sub-goal.

### Normative specification

| Norm | Type | Role | Mission |
|---|---|---|---|
| `parentNorm` | obligation | parent | speakUtterances |
| `teacherNorm` | obligation | teacher | tellStudentsToRead |

---

# 10. JaCaMo Project File {#jacamo-project-file}

**File:** `child_vocabulary_acquisition.jcm`

Defines the static MAS configuration:

**Statically-defined agent:**

- `classroom_teacher` -- Source: `teacher.asl`, focuses on `classroom.library` and `sync.synchroniser`, plays `teacher` role in `class` group.

**Workspaces and artefacts:**

- `sync`: `Synchroniser`, `DataLogger`, `WordsLearnt`
- `classroom`: `Library`
- `home_environ`: `Utterances`

**Organisation:**

- `learning_environ` with group `class` (type: `classroom`) responsible for scheme `readingBooksClassroom` (type: `classroom_reading_books`), and group `village`.

**Platform configuration:**

```
platform: local(asych_shared, 64, 5, 5, 10)
```

This configures Jason's threading model:

- `asych_shared` -- Asynchronous shared thread pool
- `64` -- Number of threads in the pool
- `5, 5, 10` -- Reasoning cycle parameters (sense, deliberate, act repetitions per scheduling slot)

The large thread pool prevents deadlocks during startup with many agents, even when the host CPU has fewer cores.

---

# 11. Simulation Lifecycle {#simulation-lifecycle}

## Startup Sequence {#startup-sequence}

1. `VocabLauncher.main()` initialises JaCaMo runtime services.
2. `create()` sets up platforms, environments, organisations, and agents.
3. `createAgs()` loads configuration, initialises artefacts, and creates dynamic agents.
4. `start()` launches all agents.
5. Parent agents create home groups and signal `groupCreated`.
6. Once all groups are created, the synchroniser transitions to "Ready".
7. Teacher executes `find_my_students` and signals `schoolReady`.
8. Synchroniser transitions to "StartSchool", beginning the first day.

## Daily Cycle {#daily-cycle}

1. **Synchroniser** status: `StartSchool`
2. **Teacher** signals `startSchool` (status becomes `SchoolStarted`), selects a book, and either reads aloud or instructs students.
3. **Children** process words (seen or heard), attempt to learn each one.
4. **Teacher** receives completion confirmations, signals `finishSchool`.
5. **Synchroniser** transitions through `SchoolFinished` to `StartHome`.
6. **Parents** select and deliver utterances to their children (20 batches each).
7. **Children** process utterances, confirm completion.
8. **Parents** signal `finishedHome`.
9. **Synchroniser** (once all parents have reported, via counter and `tryLock()`) calls `endOfDay()`.

## Yearly Progression {#yearly-progression}

After 365 daily cycles:

1. Synchroniser signals `newYear`.
2. Child agents report annual statistics to `DataLogger`.
3. Child agents update their age belief.
4. Synchroniser increments the year counter and resets the day counter.
5. If more years remain, the cycle repeats from `StartSchool`.
6. If all years are complete, the synchroniser signals `finalise`.

## Shutdown Sequence {#shutdown-sequence}

1. Child agents receive the `finalise` signal.
2. Each child reports its learned words to `WordsLearnt` and signals `childAgentFinalised`.
3. Once all children have reported (via guard `allChildrenFinished`):
   - `DataLogger.writeFiles()` writes both CSV files.
   - `RuntimeServicesFactory.get().stopMAS()` initiates JaCaMo shutdown.
   - Output file locations are printed to the console.

---

# 12. Word Learning Algorithm {#word-learning-algorithm}

Each child agent maintains per-word exposure counts. A word is considered "learnt" when any of the following conditions is met:

| Condition | Threshold |
|---|---|
| Seen (read) only | > 20 times |
| Heard (speech) only | > 20 times |
| Both seen and heard | > 12 times each |

The learning check is performed in `try_learn_word(Word)` in `child.asl`:

```
if ((Seen > 12 & Heard > 12) | Seen > 20 | Heard > 20) {
    // Mark word as learnt at current age
}
```

Once learnt, a word's `AgeLearned` field is set to the child's current age and is never re-evaluated.

---

# 13. Concurrency and Synchronisation {#concurrency-and-synchronisation}

The simulation involves significant concurrency challenges due to many agents operating simultaneously. Key mechanisms:

**Atomic plans** (`@[atomic]`): Used extensively in child and teacher agents to prevent interleaving of plan execution. Critical for maintaining belief-base consistency, especially during word processing and state transitions.

**CArtAgO guards** (`@GUARD`): The `Synchroniser` uses the `allChildrenFinished` guard to block the `childAgentFinalised` operation (via `await`) until all children have reported. Agents calling the guarded operation are suspended by CArtAgO until the guard evaluates to `true`.

**ReentrantLock with `tryLock()`**: Used in two contexts:

1. `Synchroniser.finishedHome()` -- Each parent increments a counter; when all parents have reported, `tryLock()` ensures exactly one thread calls `endOfDay()`. Other threads see the counter is met but fail to acquire the lock and skip the call.
2. `Synchroniser.childAgentFinalised()` -- After the `allChildrenFinished` guard releases all waiting agents, `tryLock()` ensures only one thread executes the shutdown sequence (writing output files and stopping the MAS).
3. `Library` -- Ensures thread-safe lazy loading of book contents.

**HashMapBB (custom belief base)**: A custom `BeliefBase` implementation (`src/env/HashMapBB.java`) that provides O(1) indexed lookups for registered belief patterns. Each agent has its own `HashMapBB` instance. Indexed beliefs (e.g., `word(key,_)`) are stored in `HashMap<String, Literal>` keyed by a designated argument, while non-indexed beliefs use namespace-aware linked lists. The `getAsDOM()` method (used by the Mind Inspector) includes retry logic for `ConcurrentModificationException`.

**Belief-base `.wait()` calls**: Jason's `.wait(condition)` suspends the agent's intention until the specified belief condition becomes true. Used for inter-agent coordination (e.g., waiting for group formation, waiting for idle state).

**Batched utterance delivery**: Parent agents send utterances in 20 batches rather than one large message. The child uses a counter (`finished(Counter, Parent)`) to track received batches and only signals completion after all 20 are processed. This prevents overwhelming the agent's messaging system.

---

# 14. Data Flow {#data-flow}

```
Books (.txt files)                    Utterances (.csv files)
       |                                       |
       v                                       v
  Library artefact                      Utterances artefact
       |                                       |
       v                                       v
  Teacher agent                         Parent agents
       |                                       |
       |                                       |
       v                                       v
  Child agents                       Child agents
       |                                       |
       +----------+----------+---------+-------+
                  |          |         |
                  v          v         v
        word_seen_checker  word_heard_checker  try_learn_word
           (child.asl)       (child.asl)        (child.asl)
                  |          |         |
                  v          v         v
          HashMapBB belief base (per-agent, indexed by word)
          words::word(Word,[Seen,Heard,AgeLearned])
          words::unique_seen / words::unique_heard
                             |
              +--------------+--------------+
              |                             |
              v                             v
        DataLogger                    WordsLearnt
   (addAnnualStats)            (addLearnedWords)
              |                             |
              v                             v
     summary.csv                    word_list.csv
```

---

# 15. Logging Configuration {#logging-configuration}

**File:** `logging.properties`

- Default log level: `WARN`
- Console handler: `java.util.logging.ConsoleHandler` with `jason.runtime.MASConsoleLogFormatter`
- File handler: Disabled by default; can be enabled by uncommenting the `FileHandler` configuration (outputs to `log/mas-*.log`).

---

# 16. Known Limitations and Unimplemented Features {#known-limitations}

1. **Siblings** -- The `max_siblings` configuration is parsed but `addSiblingAgents()` is a no-op.
2. **Single teacher** -- The system supports exactly one teacher agent. Multiple classrooms are not supported.
3. **Static word learning thresholds** -- The learning criteria (>20 or >12+12) are hard-coded in `child.asl`.
4. **Static fields in artefacts** -- All artefacts use `static` fields, meaning only one instance of each artefact type can exist in the system.
5. **No incremental output** -- Results are only written at simulation end. A crash mid-simulation loses all data.
6. **Memory usage** -- Each child agent's word data is stored in its `HashMapBB` belief base (indexed by word), so large vocabularies with many agents can consume significant heap space.
