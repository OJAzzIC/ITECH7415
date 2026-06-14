# Project Notes — JaCaMo Child Vocabulary Acquisition Simulation

*Last updated: 13 June 2026*

---

## 1. What was broken, and how it was fixed

### Problem A — the config file was silently ignored
`simulation.conf` had all its settings at the top level, but the parser
(`ConfigFile.java`) only reads them from TOML sections (`[agents]`,
`[environment.locations]`). So every run quietly fell back to defaults:
10 children and ages 3–8 (2,190 days) instead of the intended 3 children
and 365 days.
**Fix:** rewrote `simulation.conf` with proper sections, and relaxed the
validator so `age_start = age_finish = 3` is legal and means exactly one
simulated year.

### Problem B — the run hung (historically at ~75%, later at day 365)
The day-end handshake between parent and child assumed the child receives
**exactly 20** speech batches every day and counted up to that constant.
If batches and the end-of-day signal arrived out of step, the child stayed
"Busy - Listening" forever, the day never closed, and the whole simulation
froze.
**Fix:** replaced agent-side counting with a handshake inside the
Synchroniser artifact (plain Java counters): the child reports each batch
*after* processing it, and the parent blocks until the artifact confirms
every batch it sent was processed. This is immune to message timing.
A second bug let a "ghost" day 366 run during shutdown, duplicating result
rows — fixed with a hard stop flag after day 365 and a run-exactly-once
guard on each child's finalisation.

### Problem C — the results CSV was never written / could be corrupted
On the final year the "record your annual stats" signal and the "write the
CSV and shut down" signal fired simultaneously, so the file could be
written before (or without) the data.
**Fix:** on the final year only `finalise` fires, and each child records
its statistics *before* reporting itself finished, so the CSV is always
written after all rows exist. The CSV now includes a `Unique_Encountered`
column (exposure) alongside `Words_Learned` (threshold learning) — two
deliberately separate metrics.

### Problem D — the utterance dataset was empty
`prepare_data.py` crashed on its first line of output (a `writerow()` call
with no arguments) and expected a transcript format (`*MOT:` prefixes) the
corpus doesn't use. The simulation had been running on 19 toy test
utterances.
**Fix:** rewrote the script for the real format (one utterance per line);
it now produces **935,703 utterances** in `processed_utterances.csv`.
Note: 466 of the 3,834 transcript files contain only dots (placeholders)
and contribute nothing.

---

## 2. How the tool runs

```
./gradlew run        (from the ModellingLearning directory)
```

Pipeline for one run:

1. `VocabLauncher` reads `simulation.conf`, loads the utterance corpus and
   the book library, and creates the agents: 3 children (one per SES
   group), 3 single parents, 1 teacher.
2. Each simulated **day** has two phases, coordinated by the Synchroniser
   artifact:
   - **School:** the teacher picks one random book; at age 3 the teacher
     reads it aloud, so the children *hear* its words.
   - **Home:** each parent speaks the SES-specific daily word budget to
     their child (Welfare 8,767 / Working 17,514 / Professional 30,142
     words — Hart & Risley's daily figures), sampled randomly from the
     CHILDES-derived corpus in up to 20 batches.
3. Each child tracks, per word type: times seen, times heard, and whether
   it crossed the **learning threshold** (seen >20, heard >20, or both
   >12 — unchanged). Separately it counts each *new* word type it is
   exposed to at all (`unique_encountered`).
4. After day 365 the run stops, every child logs one summary row, and two
   CSVs are written. A full run takes roughly 8–10 minutes.

While running, you can watch agents live at `http://localhost:3272`
(JaCaMo mind inspector).

---

## 3. What you get from a run

Two timestamped CSVs in the project directory:

- **`<timestamp> summary.csv`** — one row per child:
  `Name, SES, Age, Unique_Seen, Unique_Heard, Unique_Encountered,
  Words_Learned`. `Unique_Encountered` is the paper's Table 6 exposure
  metric; `Words_Learned` is the threshold-based learned count. They must
  never be conflated. (`Unique_Seen` is 0 at age 3 — children don't read
  themselves until age 5 in this model; book words arrive as "heard".)
- **`<timestamp> word_list.csv`** — every learned word with the child, SES
  and age of acquisition. This is data the paper's approach cannot
  produce at all (see §5).

Verified result from today's clean run (CDS + 1 book/day, 365 days):

| SES | Unique_Encountered | Words_Learned |
|---|---|---|
| Welfare | 32,924 | 4,810 |
| Working | 36,451 | 6,608 |
| Professional | 38,211 | 8,439 |

`run_baseline.sh` runs the simulation 30 times and
`analyse_baseline.py` aggregates, for confidence intervals.

---

## 4. How this compares to the paper's Table 6

Table 6 (Green, Keogh, Sun & O'Brien 2024) — cumulative unique **types
encountered** at 365 days, with one book per day:

| SES | Paper (CDS + 1 book) | Our run | Difference |
|---|---|---|---|
| Welfare | 27,966 | 32,924 | +18% |
| Working | 36,883 | 36,451 | **−1% (excellent match)** |
| Professional | 46,567 | 38,211 | −18% |

The SES gradient is reproduced (Welfare < Working < Professional) and the
middle group matches almost exactly, but our gradient is **compressed**.
The reason is a methodological difference, not a bug: the paper samples
**whole transcripts** (≈5 / 10 / 17 per day), preserving each recording's
natural type–token structure, while our tool currently samples **random
individual utterances from one shared pool** until the daily word budget
is met. Random sampling across thousands of different speakers inflates
type diversity at small budgets (lifting Welfare) and saturates the pool
at large budgets (capping Professional). Fixing this is improvement #1
below.

Words_Learned has no Table 6 counterpart — the paper measures exposure
only. That column is this tool's value-add.

---

## 5. Why the JaCaMo agent model is better than the paper's script-based simulation

The paper's simulation is corpus arithmetic: sample text, count cumulative
unique types. It answers one question — "how many word types does a child
*encounter*?" — and stops there. The JaCaMo model:

1. **Models learning, not just exposure.** Each child applies a
   psychologically motivated threshold rule combining seen and heard
   frequencies, so we can ask which words are *acquired* and **when**
   (age-of-acquisition per word, in `word_list.csv`).
2. **Separates input channels.** Spoken input at home and book input at
   school are distinct streams with distinct effects (heard vs seen),
   which is exactly what makes the both->12 combined threshold meaningful.
3. **Has real social structure.** Children, parents, teachers, households
   and a classroom are explicit agents and organisations (BDI + Moise).
   Interventions map directly onto entities: add a second parent, change
   teacher behaviour, change class composition — no model rewrite.
4. **Supports heterogeneity.** Per-agent parameters like `attentiveness`
   (an inattentive-child variant already exists) allow within-group
   variation; the paper's approach has one curve per SES, full stop.
5. **Is interactive and inspectable.** A running simulation can be opened
   in the mind inspector and every agent's beliefs examined — invaluable
   for both debugging and explaining the model.
6. **Extends without re-derivation.** New scenarios (below) are config or
   small plan changes; in a counting script each new mechanism is a new
   program.

Honest trade-off to acknowledge: the paper's method is faster and, for the
pure exposure count, more directly corpus-faithful. The right framing is
that our tool *contains* their exposure model and adds an acquisition
layer and a social-simulation layer on top.

---

## 6. How to improve the tool

In rough priority order:

1. **Transcript-level sampling.** Sample 5/10/17 whole transcripts per day
   per SES (as the paper does) instead of random utterances by word
   budget. This should close the ±18% Table 6 gap. Requires keying the
   utterance store by transcript file.
2. **Per-SES corpora.** The Hall corpus files already encode SES in their
   names (`WhiteWork`, `BlackPro`, ...) — sample Welfare children from
   welfare-recorded transcripts, etc.
3. **Multiple children per SES + variance.** `child = 30` and the 30-run
   script give means and confidence intervals instead of single noisy
   points.
4. **Validate the learning layer.** Compare `Words_Learned` and the
   age-of-acquisition output against published norms (Wordbank/CDI,
   AoA norms) — this is the publishable claim the paper can't make.
5. **Book-dose sweeps.** Table 6 has columns for 1–5 books/day; add a
   `books_per_day` config key and reproduce the whole table, not just one
   column.
6. **Performance/robustness.** Headless mode (skip the web inspectors),
   a CLI flag for the config file path, and a watchdog that dumps agent
   states automatically if a day takes too long.
7. **Data hygiene.** Remove or regenerate the 466 placeholder transcript
   files; strip punctuation in the book texts the same way utterances are
   cleaned so "dog." and "dog" count as one type.

---

## 7. Adapting to client scenarios

The architecture maps client questions onto three levers — **config**,
**agent plans**, **corpora**:

| Client scenario | What to change |
|---|---|
| "What if low-SES families get a book-gifting program?" | Give the Welfare home a daily book-reading plan in `parent.asl` (reuse `read_book_aloud_to_child`); compare runs. |
| "Effect of two-parent vs single-parent households?" | `percent_single_parents` in `simulation.conf` — already implemented; parents split the word budget. |
| "More/less preschool exposure?" | Vary teacher books per day, or give a child a probability of skipping school in `child.asl`. |
| "Children with attention difficulties?" | `attentiveness(0.5)` variant (`inattentive_child.asl`) already exists — mix attentive and inattentive children in one classroom. |
| "A different language or dialect community?" | Swap the corpora in `[environment.locations]`; `prepare_data.py` regenerates the utterance CSV from any one-utterance-per-line text. |
| "Longer horizons (ages 3–8)?" | `age_finish = 8` — multi-year runs already log one row per child per year, giving growth curves. |
| "Policy comparison with uncertainty" | Wrap any of the above in `run_baseline.sh` (30 runs) and report distributions, not single numbers. |

The general recipe for any new client requirement: (1) express it as a
change to an agent's daily behaviour or an environment resource, (2) make
it a `simulation.conf` switch so scenarios are reproducible, (3) run the
baseline and the scenario 30× each, (4) compare both columns — exposure
*and* words learned — because interventions can move the two differently.
