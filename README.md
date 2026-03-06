# Child Vocabulary Acquisition Simulation

A multi-agent simulation of how children acquire vocabulary over time, modelling the impact of socio-economic status (SES) on word learning. Built with [JaCaMo](https://jacamo-lang.github.io/jacamo/), a framework combining Jason (BDI agents), CArtAgO (environment artefacts), and Moise (organisational structures).

## Table of Contents

- [Overview](#overview)
- [How It Works](#how-it-works)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
  - [Agent Settings](#agent-settings)
  - [Socio-Economic Status Categories](#socio-economic-status-categories)
  - [Data Locations](#data-locations)
- [Running the Simulation](#running-the-simulation)
- [Inspecting a Running Simulation](#inspecting-a-running-simulation)
- [Output](#output)
- [Sample Console Output](#sample-console-output)
- [Platforms](#platforms)
- [Licence](#licence)

## Overview

This simulation investigates how children's word exposure (through being read to and hearing speech) translates into vocabulary growth, and how that growth differs across socio-economic backgrounds.

Child agents learn words by accumulating exposure. A word is considered "learnt" when a child has:

- **heard** it more than 20 times, **or**
- **seen** (read) it more than 20 times, **or**
- **both** seen and heard it more than 12 times each.

The simulation progresses through simulated years (each comprising 5 days). Each day consists of a **school** phase (where a teacher reads to or instructs children to read books) and a **home** phase (where parents speak utterances to their children, with the volume of speech determined by SES).

## How It Works

1. **School phase** -- A teacher agent selects a book from the library.
   - Children under 5: the teacher reads aloud to them.
   - Children aged 5--7: 50% chance of reading aloud or independent reading.
   - Children over 7: independent reading.
2. **Home phase** -- Parent agents deliver utterances to their child, drawn randomly from a corpus. The total daily word count is determined by the household's SES category.
3. **Year end** -- Each child's statistics (unique words seen, heard, and learnt) are recorded.
4. **Simulation end** -- Results are written to timestamped CSV files.

## Prerequisites

- **Java 11** or later
- **Gradle** (or use the included `gradlew` / `gradlew.bat` wrapper)
- **Graphviz** *(optional, recommended)* -- required for graphical views of the Moise organisational structure via the web inspector. See [Graphviz Downloads](https://graphviz.org/download/).

### User-Supplied Data

Due to licensing restrictions, the book texts and utterance data are **not included**. You must supply your own:

| Data Type | Format | Configuration Key |
|---|---|---|
| **Book texts** | One `.txt` file per book, UTF-8 encoded | `[environment.locations] books` |
| **Utterances** | CSV files following [CHILDES](https://talkbank.org/childes/) conventions. Utterance files must contain `utterances` in the filename and include `speaker_code` and `gloss` columns. Participant metadata files must contain `participants` in the filename and include `role` and `id` columns. | `[environment.locations] utterances` |

## Configuration

All simulation settings are controlled through a single file: **`simulation.conf`** (TOML format). Every setting is optional; omitted values use sensible defaults. See [TOML specification](https://toml.io) for syntax details.

### Agent Settings

```toml
[agents]
child = 10                    # Number of child agents (default: 10)
max_siblings = 0              # Siblings per child, 0-2 (default: 0, not yet implemented)
percent_single_parents = 100.0 # Percentage of single-parent households, 0.0-100.0
age_start = 3                 # Starting age, 0-5 (default: 3)
age_finish = 8                # Finishing age, 5-10 (default: 8)
```

Values outside permitted ranges are clamped to the nearest valid value.

> **Tip:** It is recommended to run multiple simulations with hundreds of agents rather than a single run with thousands, to avoid excessive memory usage.

### Socio-Economic Status Categories

Define one or more SES categories. Children and their households are distributed as evenly as possible across all listed categories.

```toml
[[ses]]
name = "Welfare"
qty = 8767          # Average daily words spoken to child

[[ses]]
name = "Working"
qty = 17514

[[ses]]
name = "Professional"
qty = 30142
```

If no valid `[[ses]]` entries are defined, the three defaults shown above are used. It is recommended to make the number of child agents a multiple of the number of SES categories.

### Data Locations

```toml
[environment.locations]
utterances = "./resources/utterances"
books = "./resources/books"
```

Both relative and absolute paths are supported. Case sensitivity depends on the operating system.

> **Warning:** The simulation will stall (not crash) if the specified directories do not exist. Ensure the paths are correct before running.

## Running the Simulation

**Linux / macOS:**
```bash
chmod +x gradlew    # first time only
./gradlew
```

**Windows:**
```cmd
gradlew.bat
```

**Standalone JAR:**
```bash
./gradlew shadowJar
java -jar build/libs/jacamo-child_vocabulary_acquisition-1.0-all.jar
```

**VSCode:** Open the project folder with the "Gradle for Java" extension installed, then run the default task.

## Inspecting a Running Simulation

While the simulation is running, three web interfaces are available:

| Interface | URL | Purpose |
|---|---|---|
| Jason Mind Inspector | [http://localhost:3272](http://localhost:3272) | View agent beliefs, goals, and plans |
| Moise Web View | [http://localhost:3271](http://localhost:3271) | View organisational structure |
| CArtAgO Web View | [http://localhost:3273](http://localhost:3273) | View environment artefacts |

Graphviz must be installed for full graphical rendering in the Moise view.

## Output

Two timestamped CSV files are produced when the simulation completes, saved alongside the Gradle wrapper:

### Summary (`YY-mm-dd-HH-MM-SS summary.csv`)

Per-agent, per-year statistics:

| Column | Description |
|---|---|
| Name | Agent identifier |
| SES | Socio-economic status category |
| Age | Agent's age in that year |
| Unique_Seen | Cumulative count of unique words seen (read) |
| Unique_Heard | Cumulative count of unique words heard |
| Words_Learned | Cumulative count of words meeting the learning threshold |

### Word List (`YY-mm-dd-HH-MM-SS word_list.csv`)

Every word learnt by every agent:

| Column | Description |
|---|---|
| Word | The word that was learnt |
| Name | Agent identifier |
| SES | Socio-economic status category |
| Age | Age at which the word was learnt |

## Sample Console Output

```
$ ./gradlew

> Task :run
Runtime Services (RTS) is running at 127.0.0.1:63829
Agent mind inspector is running at http://127.0.0.1:3272
CArtAgO Http Server running on http://127.0.0.1:3273
Looking for config file in: /path/to/child_vocabulary_acquisition
Found config file: /path/to/child_vocabulary_acquisition/simulation.conf
Attempting to parse it.
Configuration loaded:
[agents]
   child = 1
   max_siblings = 0
   percent_single_parents = 100.0
   age_start = 3
   age_finish = 5
...
Finished day 1 of year 1.
...
Finished day 5 of year 3.
Summary data file successfully written.
Word count data file successfully written.
Shutting down environment...
Shutdown complete.
Summary of results saved to: 26-01-29-13-10-11 summary.csv
Word count results saved to: 26-01-29-13-10-11 word_list.csv
```

## Platforms

Developed and tested on Linux and macOS. Windows is supported via `gradlew.bat` and the Gradle build system.

## Licence

This code is provided "as-is" with no warranties or guarantees of any sort, as far as legally permitted.
