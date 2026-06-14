# Child Vocabulary Acquisition Simulation

A multi-agent simulation of how children acquire vocabulary over time, modelling the impact of socio-economic status (SES), child learning profiles, parent language profiles, and home reading environments on word learning. Built with [JaCaMo](https://jacamo-lang.github.io/jacamo/), a framework combining Jason (BDI agents), CArtAgO (environment artefacts), and Moise (organisational structures).

The model is based on [Green, Keogh, Sun & O'Brien (2024), *Behavior Research Methods*](https://link.springer.com/article/10.3758/s13428-023-02198-y), which simulated cumulative word-type exposure in Python. This agent-based version reproduces that exposure model and extends it with a word-*learning* layer and configurable agent profiles.

## Table of Contents

- [Overview](#overview)
- [How It Works](#how-it-works)
- [Prerequisites](#prerequisites)
- [Preparing the Data](#preparing-the-data)
- [Running the Simulation](#running-the-simulation)
- [Configuration](#configuration)
  - [Agent Settings](#agent-settings)
  - [Socio-Economic Status Categories](#socio-economic-status-categories)
  - [Data Locations](#data-locations)
  - [Child Profiles](#child-profiles)
  - [Parent Profiles and Utterance Pools](#parent-profiles-and-utterance-pools)
  - [Home Profiles](#home-profiles)
- [Scenarios: Changing the Environment](#scenarios-changing-the-environment)
  - [Ready-Made Scenarios](#ready-made-scenarios)
  - [Building Your Own Scenario](#building-your-own-scenario)
- [Output](#output)
- [Batch Runs and Analysis](#batch-runs-and-analysis)
- [Inspecting a Running Simulation](#inspecting-a-running-simulation)
- [Sample Console Output](#sample-console-output)
- [Troubleshooting](#troubleshooting)
- [Platforms](#platforms)
- [Licence](#licence)

## Overview

This simulation investigates how children's word exposure (through being read to and hearing speech) translates into vocabulary growth, and how that growth differs across socio-economic backgrounds, learner types, and home environments.

Child agents learn words by accumulating exposure. With the default learning rule, a word is considered "learnt" when a child has:

- **heard** it more than 20 times, **or**
- **seen** (read) it more than 20 times, **or**
- **both** seen and heard it more than 12 times each.

The simulation progresses through simulated years (each comprising 365 days). Each day consists of a **school** phase (where one teacher reads to, or instructs, **all** children — the classroom experience is identical for every child, so it acts as the controlled variable) and a **home** phase (where parents speak utterances to their children, with the volume of speech determined by SES, and optionally read books aloud at home).

Two distinct metrics are tracked and must not be conflated:

- **Unique_Encountered** — cumulative unique word *types* the child has been exposed to at least once (the exposure metric of the paper's Table 6).
- **Words_Learned** — words that crossed the learning threshold.

## How It Works

1. **School phase** — The teacher agent selects one book from the library and shares it with the whole class.
   - Children under 5: the teacher reads aloud to them (words count as *heard*).
   - Children aged 5–7: 50% chance of reading aloud or independent reading.
   - Children over 7: independent reading (words count as *seen*).
2. **Home phase** — Each parent agent delivers utterances to their child, drawn randomly from the parent's assigned utterance pool. The total daily word count is the household's SES quantity, scaled by the parent profile's factor. If the home profile sets `books_per_day > 0`, the parent then reads that many randomly chosen books aloud.
3. **Attentiveness** — every heard/seen word passes through the child profile's per-channel attentiveness gate (probability of attending to that word) before it is counted.
4. **Day end** — the day only closes once the synchroniser artifact has confirmed every child processed every batch its parent sent (a counted handshake; this is what makes the simulation hang-proof).
5. **Year end** — each child's statistics are recorded.
6. **Simulation end** — results are written to timestamped CSV files and the system shuts itself down.

## Prerequisites

- **Java 17** or later (developed against Java 21)
- **Gradle** (or use the included `gradlew` / `gradlew.bat` wrapper — no separate install needed)
- **Python 3** — only needed for the data-preparation and analysis scripts
- **Graphviz** *(optional)* — for graphical views of the Moise organisational structure via the web inspector. See [Graphviz Downloads](https://graphviz.org/download/).

### User-Supplied Data

Due to licensing restrictions, the book texts and utterance data are **not included** in public distributions. The research corpus is available in the project's [OSF repository](https://osf.io/z7tpe/); additional corpora can be obtained from [CHILDES / TalkBank](https://talkbank.org/childes/).

| Data Type | Format | Configuration Key |
|---|---|---|
| **Book texts** | One `.txt` file per book, UTF-8 encoded | `[environment.locations] books` |
| **Utterances** | CSV files. Utterance files must contain `utterances` in the filename and include `speaker_code` and `gloss` columns. Participant metadata files must contain `participants` in the filename and include `role` and `id` columns. | `[environment.locations] utterances` |

## Preparing the Data

If you have raw transcripts rather than ready-made CSV files, `prepare_data.py` converts them:

```bash
# Extract the corpus (from OSF / DATA.tar) into resources/, then:
python3 prepare_data.py
```

It walks `resources/utterances/`, accepts both plain-text transcripts (one utterance per line) and CHA-format files (`*MOT:` lines), cleans punctuation, and writes `resources/utterances/processed_utterances.csv` in the format the simulation loads. Re-run it whenever the transcript files change.

To create an **additional utterance pool** (e.g. for an ESL parent profile), place its CSV file(s) in their own directory — for example `resources/utterances_esl/esl_utterances.csv` — and declare the pool in the configuration (see [Parent Profiles and Utterance Pools](#parent-profiles-and-utterance-pools)).

## Running the Simulation

**Linux / macOS:**
```bash
chmod +x gradlew    # first time only
./gradlew                                      # uses simulation.conf
./gradlew run -Pconf=scenarios/baseline.conf   # uses a specific scenario file
```

**Windows:**
```cmd
gradlew.bat
gradlew.bat run -Pconf=scenarios\baseline.conf
```

**Standalone JAR:**
```bash
./gradlew shadowJar
java -jar build/libs/jacamo-child_vocabulary_acquisition-1.0-all.jar
```

**VSCode:** Open the project folder with the "Gradle for Java" extension installed, then run the default task.

A one-year run with three children takes roughly 8–10 minutes. For a fast mechanical check after changing code or configuration, run the smoke test (about a minute):

```bash
./gradlew run -Pconf=scenarios/smoke_test.conf
```

## Configuration

All simulation settings are controlled through a single TOML file — `simulation.conf` by default, or any file passed with `-Pconf=<path>`. Every setting is optional; omitted values use sensible defaults, and **a configuration with no profile sections behaves exactly like the original (pre-profile) model**. See the [TOML specification](https://toml.io) for syntax details.

> **Important:** keys must appear inside their section headers (`[agents]`, `[environment.locations]`). Top-level keys are silently ignored.

### Agent Settings

```toml
[agents]
child = 3                      # Number of child agents (ignored when [[child_profile]]
                               # sections are present - their counts then decide)
max_siblings = 0               # Siblings per child, 0-2 (default: 0, not yet implemented)
percent_single_parents = 100.0 # Percentage of single-parent households, 0.0-100.0
age_start = 3                  # Starting age, 0-5 (default: 3)
age_finish = 3                 # Finishing age, 0-10; clamped to at least age_start.
                               # Set equal to age_start to simulate exactly one year.
days_per_year = 365            # Days per simulated year (default 365).
                               # Lower it ONLY for smoke tests - not for results.
```

Values outside permitted ranges are clamped to the nearest valid value.

### Socio-Economic Status Categories

Define one or more SES categories. Households are assigned to categories round-robin.

```toml
[[ses]]
name = "Welfare"
qty = 8767          # Average words spoken to the child per day (Hart & Risley)

[[ses]]
name = "Working"
qty = 17514

[[ses]]
name = "Professional"
qty = 30142
```

If no valid `[[ses]]` entries are defined, the three defaults shown above are used. Make the number of children a multiple of the number of SES categories so each category is equally represented.

### Data Locations

```toml
[environment.locations]
utterances = "./resources/utterances"
books = "./resources/books"
```

Both relative and absolute paths are supported.

> **Warning:** The simulation will stall (not crash) if the specified directories do not exist or contain no matching files. Ensure the paths are correct before running.

### Child Profiles

Each `[[child_profile]]` defines a *type of child* and how many of them to create. Children are created in the order the profiles appear (`child1..childN`). All parameters are optional; the defaults reproduce the original model exactly.

```toml
[[child_profile]]
name = "typical"            # label, appears in the output CSV
count = 3                   # how many children of this type
attentiveness = 1.0         # shorthand: sets both channels below

[[child_profile]]
name = "inattentive"
count = 3
attentiveness_heard = 0.5   # probability of attending to any single heard word
attentiveness_seen = 0.5    # probability of attending to any single seen word

[[child_profile]]
name = "autistic_demo"
count = 3
attentiveness_heard = 0.4   # reduced attention to spoken/social input
attentiveness_seen = 1.0    # visual channel unaffected
threshold_heard = 30        # spoken words need more repetitions to be learnt
threshold_seen = 20         # (threshold_* default to 20/20/12 - the baseline rule)
threshold_both = 12
```

| Parameter | Meaning | Default |
|---|---|---|
| `name` | Profile label (required) | — |
| `count` | Number of children with this profile | 1 |
| `attentiveness` | Sets both channels at once | 1.0 |
| `attentiveness_heard` / `attentiveness_seen` | Per-channel probability (0–1) of attending to a word | 1.0 |
| `threshold_seen` / `threshold_heard` / `threshold_both` | Learning rule: learnt when seen > S, or heard > H, or both > B | 20 / 20 / 12 |

> **Research note:** parameter values for clinical-flavoured profiles (e.g. autism spectrum) are a research decision, not a programming one. Calibrate them from the literature (TalkBank's ASDBank corpora may help) before drawing conclusions; the values in `scenarios/autism_demo.conf` are placeholders that demonstrate the mechanism.

### Parent Profiles and Utterance Pools

Parent profiles control *what* a parent says and *how much*. Each profile can draw its speech from a named **utterance pool** — a separate corpus directory — which is how, for example, English-as-a-second-language input is modelled.

```toml
[[utterance_pool]]
name = "esl_demo"
path = "./resources/utterances_esl"   # directory of *utterances*.csv files

[[parent_profile]]
name = "standard"
pool = "default"            # "default" = the main [environment.locations] corpus

[[parent_profile]]
name = "esl_parent"
pool = "esl_demo"
daily_words_factor = 0.9    # optional scaling of the SES daily word budget (0-2)
```

Parent profiles are assigned to households round-robin, in file order. An unknown pool name falls back to the default pool with a console warning.

### Home Profiles

Home profiles control the home *reading* environment — how many books the parent reads aloud to the child each day (0 = the baseline, no reading at home). Home books are read aloud, so their words count as *heard*.

```toml
[[home_profile]]
name = "no_books"
books_per_day = 0

[[home_profile]]
name = "one_book"
books_per_day = 1

[[home_profile]]
name = "three_books"
books_per_day = 3           # 0-10; per parent, per day
```

Home profiles are assigned to households round-robin, in file order.

## Scenarios: Changing the Environment

A **scenario is just a configuration file**. Keep one file per experimental condition in `scenarios/` and select it at run time — nothing in the code changes between scenarios:

```bash
./gradlew run -Pconf=scenarios/mixed_children.conf
```

### Ready-Made Scenarios

| File | What it models |
|---|---|
| `scenarios/baseline.conf` | The paper's setting: one typical child per SES group, one year, school reading only. |
| `scenarios/mixed_children.conf` | Typical vs inattentive children (attends to 50% of words) in every SES group. |
| `scenarios/autism_demo.conf` | An autism-spectrum profile demonstration: reduced spoken-channel attention, higher heard threshold. **Placeholder parameters — calibrate before use.** |
| `scenarios/esl_home_books.conf` | ESL parents (restricted-vocabulary utterance pool) crossed with home environments of 0, 1 and 3 books per day. |
| `scenarios/smoke_test.conf` | Every feature at once in a 5-day run, for verifying the machinery (~1 minute). Not for results. |

### Building Your Own Scenario

1. **Copy** `scenarios/baseline.conf` to a new file and change *only* the condition under study — one variable per scenario keeps results interpretable.
2. **Choose the population.** Add `[[child_profile]]` sections; their `count`s decide the total number of children. Keep totals a multiple of the SES count.
3. **Choose the input.** Add `[[parent_profile]]` (+ `[[utterance_pool]]` if using a different corpus) and `[[home_profile]]` sections. Profiles distribute round-robin across households; the output CSV labels every child with all of its profile names, so analysis never depends on remembering the assignment.
4. **Keep the classroom constant.** Don't vary school-side behaviour between scenarios — it is the shared baseline experience that makes home/child effects attributable.
5. **Smoke-test it:** add `days_per_year = 5` temporarily, run, and check one summary row appears per child with the labels you expect. Then remove `days_per_year` and run for real.
6. **Run it enough times.** One run is one sample; use batch runs (below) and compare distributions of **both** `Unique_Encountered` and `Words_Learned` — an intervention can move the two metrics differently.

Worked example — *"Does daily home reading close the gap for inattentive Welfare children?"*:

```toml
[agents]
age_start = 3
age_finish = 3

[[ses]]
name = "Welfare"
qty = 8767

[[child_profile]]
name = "inattentive"
count = 2
attentiveness = 0.5

[[home_profile]]
name = "no_books"
books_per_day = 0

[[home_profile]]
name = "two_books"
books_per_day = 2
```

Two inattentive Welfare children, one in a non-reading home, one read to twice daily — same classroom, same corpus. The difference between their rows is the effect of home reading.

## Output

Two timestamped CSV files are produced when the simulation completes, saved alongside the Gradle wrapper:

### Summary (`YY-mm-dd-HH-MM-SS summary.csv`)

Per-agent, per-year statistics:

| Column | Description |
|---|---|
| Name | Agent identifier |
| Profile | Child profile name (e.g. `typical`, `inattentive`) |
| ParentProfile | Parent profile name (e.g. `standard`, `esl_parent`) |
| HomeProfile | Home profile name (e.g. `no_books`, `two_books`) |
| SES | Socio-economic status category |
| Age | Agent's age in that year |
| Unique_Seen | Cumulative count of unique word types seen (read) |
| Unique_Heard | Cumulative count of unique word types heard |
| Unique_Encountered | Cumulative unique word types exposed to at least once (the paper's Table 6 exposure metric) |
| Words_Learned | Cumulative count of words meeting the learning threshold |

> **Note:** `Unique_Seen` is 0 for age-3 runs by design — the teacher reads aloud to under-5s, and children do not read for themselves until age 5.

### Word List (`YY-mm-dd-HH-MM-SS word_list.csv`)

Every word learnt by every agent, with the age it was learnt — i.e. per-word age-of-acquisition data:

| Column | Description |
|---|---|
| Word | The word that was learnt |
| Name | Agent identifier |
| SES | Socio-economic status category |
| Age | Age at which the word was learnt |

## Batch Runs and Analysis

A single run is one random sample. For comparable statistics:

```bash
./run_baseline.sh        # 30 runs, results collected into sim_runs/
python3 analyse_baseline.py
```

When comparing scenarios, run each scenario's config the same number of times and compare distributions per profile label.

## Inspecting a Running Simulation

While the simulation is running, three web interfaces are available:

| Interface | URL | Purpose |
|---|---|---|
| Jason Mind Inspector | [http://localhost:3272](http://localhost:3272) | View agent beliefs, goals, and plans |
| Moise Web View | [http://localhost:3271](http://localhost:3271) | View organisational structure |
| CArtAgO Web View | [http://localhost:3273](http://localhost:3273) | View environment artefacts |

Graphviz must be installed for full graphical rendering in the Moise view. If another simulation (or a stuck one) is already holding a port, the inspector binds the next port up — check the console banner for the actual URL.

## Sample Console Output

```
$ ./gradlew run -Pconf=scenarios/mixed_children.conf

> Task :run
Runtime Services (RTS) is running at 127.0.0.1:63829
Agent mind inspector is running at http://127.0.0.1:3272
Looking for config file in: /path/to/project
Found config file: /path/to/project/scenarios/mixed_children.conf
Attempting to parse it.
Child profiles define 6 children in total; overriding agents.child=10.
Configuration loaded:
[agents]
   child = 6
   ...
Loading utterance pool 'default': 935723 entries from 2 files in 336ms.
Simulation ready to start.
Finished day 1 of year 1.
...
Finished day 365 of year 1.
[DataLogger] addAnnualStats called for child1 profile=typical SES=Welfare encountered=32924 learned=4810
...
Summary data file successfully written.
Word count data file successfully written.
Shutting down environment...
Shutdown complete.
Summary of results saved to: 26-06-13-02-25-38 summary.csv
Word count results saved to: 26-06-13-02-25-38 word_list.csv
```

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Settings appear ignored (wrong child count / ages) | Config keys outside their TOML sections — they must be under `[agents]`, `[environment.locations]`, etc. Check the "Configuration loaded" console echo. |
| `WARNING: utterance pool '...' is unknown or empty` | The `[[utterance_pool]]` path is wrong or its directory has no `*utterances*.csv` file. The run continues on the default pool. |
| Run stalls before day 1 | Data directories missing/empty, or `processed_utterances.csv` was never generated — run `python3 prepare_data.py`. |
| Run stalls mid-day | Inspect the agents at http://localhost:3272 (look for a child stuck "Busy - Listening" or a parent stuck in `awaitChildHeardAll`) and report the state. |
| Want a fast check, not results | `days_per_year = 5` in the config, or use `scenarios/smoke_test.conf`. |

## Platforms

Developed and tested on Linux and macOS. Windows is supported via `gradlew.bat` and the Gradle build system.

## Licence

This code is provided "as-is" with no warranties or guarantees of any sort, as far as legally permitted.
