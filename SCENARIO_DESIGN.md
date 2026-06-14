# Scenario Design — Child / Parent / Home Profiles

> **STATUS: IMPLEMENTED (13 June 2026).** The design below has been built —
> see README.md ("Configuration" and "Scenarios" sections) for the user-facing
> documentation and `scenarios/` for ready-made configuration files. This
> document remains as the design rationale.

*How to extend the simulation for the client's requirements: different child
types (inattentive, autistic), different parent types (ESL), different home
environments (books / multiple books / no books), with a common classroom
experience for all children.*

The guiding principle: **every profile is a set of named parameters in
`simulation.conf`**, agents read their parameters as initial beliefs, and one
scenario = one config file. No scenario should ever require editing agent
code once the parameter hooks exist.

---

## 0. What already exists (don't rebuild it)

| Requirement | Existing hook |
|---|---|
| Attentiveness | `attentiveness(1.0)` belief in `child.asl`; `maybe_process_heard/seen` already roll a random number against it per word. `inattentive_child.asl` (factor 0.5) already exists but is never instantiated by the launcher. |
| Common classroom | Already true: one teacher, one class, one shared book per day for every child (`teacher.asl` sends the same title to all students). Keep this fixed across scenarios — it is the controlled variable that lets home/child effects show up cleanly. |
| Home book reading | `read_book_aloud_to_child(Title,Child)` already exists in `inc/adult_common.asl`, which parents include — it just isn't called from any parent plan yet. |
| Per-child SES word budgets | `[[ses]]` config + round-robin assignment in `VocabLauncher.addParentAgents()`. |

---

## 1. Child profiles (inattentive, autistic, ...)

### Config

```toml
[[child_profile]]
name = "typical"
count = 1
attentiveness_heard = 1.0
attentiveness_seen  = 1.0
threshold_seen  = 20      # learning rule parameters; defaults = current rule
threshold_heard = 20
threshold_both  = 12

[[child_profile]]
name = "inattentive"
count = 1
attentiveness_heard = 0.5
attentiveness_seen  = 0.5

[[child_profile]]
name = "autistic"
count = 1
attentiveness_heard = 0.4   # reduced attention to social/spoken input
attentiveness_seen  = 1.0   # relatively stronger visual/print channel
threshold_heard = 30        # more repetitions of spoken input needed
```

### Code changes

1. **`ConfigFile.java`** — parse `[[child_profile]]` the same way `[[ses]]` is
   parsed (name + numeric fields, with the current behaviour as defaults).
2. **`VocabLauncher.addChildAgents()`** — instead of one template child ×
   `agents.child`, loop over profiles and inject the parameters as initial
   beliefs:
   ```java
   ag.addInitBel(ASSyntax.parseLiteral("attentiveness_heard(" + p.heard + ")"));
   ag.addInitBel(ASSyntax.parseLiteral("attentiveness_seen("  + p.seen  + ")"));
   ag.addInitBel(ASSyntax.parseLiteral("thresholds(" + p.tSeen + "," + p.tHeard + "," + p.tBoth + ")"));
   ag.addInitBel(ASSyntax.parseLiteral("profile(\"" + p.name + "\")"));
   ```
3. **`child.asl`** — split the single `attentiveness(F)` gate into the two
   channel gates, and make `try_learn_word` read its numbers from the
   `thresholds(S,H,B)` belief instead of literals:
   ```
   +!maybe_process_heard(Word) : attentiveness_heard(F) <- .random(R); if (R < F) { ... }.
   +!maybe_process_seen(Word)  : attentiveness_seen(F)  <- .random(R); if (R < F) { ... }.

   +!try_learn_word(Word)<-
       ?thresholds(TS,TH,TB);
       ?words::word(Word,[Seen,Heard,AgeLearned]);
       if(AgeLearned==0){
           if((Seen>TB & Heard>TB) | Seen>TS | Heard>TS){ ... };
       }.
   ```
   With `thresholds(20,20,12)` as the default belief, the baseline behaviour
   is bit-for-bit unchanged — the validated rule stays the default.
4. **`DataLogger` / CSV** — add a `Profile` column so results group by
   profile, not just SES (one-line changes in `WordCountRow`,
   `addAnnualStats`, and the `record_annual_stats` plan; same pattern as the
   `Unique_Encountered` column we just added).

### Important note on the "autistic" profile

Attentiveness/threshold numbers for an autism profile are a **research
decision, not a programming one**. Implement the knobs, but get the values
(and which knobs matter — e.g. reduced response to child-directed speech,
relative strength of visual input, restricted interests) from the research
team / literature. TalkBank hosts **ASDBank** (autism interaction corpora),
which can both inform parameters and supply input data. The model should be
presented as "a child agent with reduced social-channel attention", not as a
clinical claim.

---

## 2. Parent profiles (English as a second language, ...)

Two mechanisms, both useful; implement (a) first.

### (a) Named utterance pools — "different types of conversations" (the email's explicit ask)

Today all parents sample one shared pool. Make pools named and assignable:

```toml
[[utterance_pool]]
name = "english_l1"
path = "./resources/utterances"            # current corpus

[[utterance_pool]]
name = "esl"
path = "./resources/utterances_esl"        # e.g. CHILDES bilingual corpora

[[parent_profile]]
name = "esl_parent"
pool = "esl"
daily_words_factor = 0.8    # optionally scale the SES word budget
```

Code:
1. **`Utterances.java`** — load `HashMap<String, HashMap<...>> pools` keyed by
   pool name (the loading code already exists; wrap it per directory), and
   add the pool name to the operation:
   `getBulkUtterances(poolName, numWordsRequired, out, out)`.
2. **`parent.asl`** — parent reads its `pool(P)` initial belief and passes it:
   `getBulkUtterances(P, DesiredWords, Utterances, NumWordsReceived);`
3. **`VocabLauncher.addParentAgents()`** — assign profiles to parents the same
   round-robin way SES is assigned (or explicitly: profile per household in
   the config).

Data: the OSF project (osf.io/z7tpe) is the primary corpus; for ESL input use
CHILDES bilingual corpora from talkbank.org/childes (e.g. Spanish–English,
Mandarin–English sets), run through the existing `prepare_data.py` (it
accepts any one-utterance-per-line text or `*MOT:`-prefixed CHA files).

### (b) Lexical-diversity cap (cheap ESL approximation)

If a separate corpus isn't ready, approximate restricted L2 vocabulary by
sampling from only the N most frequent types of the main pool
(`vocab_cap = 3000` in the profile; filter applied once at load time).
State clearly in any write-up that this is an approximation.

---

## 3. Home environments (no books / one book / multiple books)

### Config

```toml
[[home_profile]]
name = "no_books"
books_per_day = 0

[[home_profile]]
name = "one_book"
books_per_day = 1

[[home_profile]]
name = "many_books"
books_per_day = 3        # Table 6 goes up to 5 — this enables that sweep
```

### Code changes

1. **`VocabLauncher.addParentAgents()`** — give parents
   `home::books_per_day(N)` as an initial belief, and **add
   `agParent.addFocus("classroom.library", null)`** — parents currently can't
   reach the book library artifact (only children and the teacher focus it).
2. **`parent.asl`** — extend `+!speak_to_child` (after the utterance loop,
   *before* the handshake):
   ```
   ?home::books_per_day(NBooks);
   for(.range(I,1,NBooks)){
       get_bookTitleRandomly(Title);
       !read_book_aloud_to_child(Title,Child);   // already in adult_common.asl
   };
   ```
3. **Close the handshake correctly** (this is the one subtle part — we just
   spent a debugging session learning that day-end accounting must be
   airtight): count each home book as one more "batch".
   - Child side: `+listen_to_speech` already exists for teacher reading; give
     parent-sourced books their own trigger (e.g. parent sends
     `listen_to_home_book(Text)`), whose plan processes the words **as
     heard** and ends with `sync::utteranceBatchProcessed;` — exactly like an
     utterance batch.
   - Parent side: `sync::awaitChildHeardAll(Child, BatchesSent + NBooks);`
   No Synchroniser changes needed — the artifact just counts.
4. Decide with the client whether a *home* book read by a parent counts as
   heard only (read aloud — recommended, matches the age-3 school model) or
   heard + seen (shared book reading where the child sees the print).
   Make it a profile flag if both are wanted: `home_book_mode = "heard"`.

---

## 4. Common classroom experience

Nothing to build — assert it and keep it:
- One `classroom_teacher`, one class, all children adopt the `student` role,
  the teacher picks **one** title per day and reads it to **all** students.
- Keep `books_per_day_school` implicitly 1 (optionally expose it in
  `[agents]` later for Table 6's +N-books sweep, but it must stay identical
  for every child within a run).
- Because school input is identical across children, any between-child
  differences in a run are attributable to child profile + parent profile +
  home profile. That is the experimental design in one sentence.

---

## 5. Putting a scenario together

A scenario is one conf file, e.g. `scenarios/esl_no_books_inattentive.conf`:

```toml
[agents]
child = 6                 # 2 per SES; pair each typical child with a variant
age_start = 3
age_finish = 3
...
[[child_profile]]  name="typical"      count=3 ...
[[child_profile]]  name="inattentive"  count=3 ...
[[parent_profile]] name="esl_parent"   pool="esl" ...
[[home_profile]]   name="no_books"     books_per_day=0
```

Workflow per client question:
1. Copy the baseline conf, change only the profile under study.
2. Run 30× (`run_baseline.sh`, parameterise it to take a conf path).
3. Compare **both** output columns per profile — `Unique_Encountered`
   (exposure) and `Words_Learned` (acquisition) — plus age-of-acquisition
   curves from `word_list.csv`. Interventions can move the two metrics
   differently, which is exactly the insight the client is paying for.

Small supporting changes worth doing at the same time:
- CLI flag for the conf path (`gradle run -Pconf=scenarios/x.conf`) so
  scenarios don't overwrite `simulation.conf`.
- Add `Profile` (child), `ParentProfile`, `HomeProfile` columns to the
  summary CSV so one file is self-describing.

---

## 6. Suggested build order (fits Sprint 1)

1. `[[child_profile]]` parsing + launcher loop + two-channel attentiveness +
   parameterised thresholds (defaults = current rule). *~1–2 days; this is
   the email's headline deliverable.*
2. `Profile` columns in the CSV + conf-path CLI flag. *~0.5 day.*
3. Named utterance pools + per-parent pool assignment. *~1–2 days, includes
   preparing an ESL corpus from CHILDES via `prepare_data.py`.*
4. Home book reading with handshake-safe accounting. *~1 day, test the
   day-end handshake hard (we know its failure mode).* 
5. 30-run scenario harness + a comparison notebook/script extending
   `analyse_baseline.py`. *~1 day.*

Data sources, for reference: OSF project (osf.io/z7tpe) for the existing
utterance + book corpus; talkbank.org/childes for additional CHILDES corpora
(bilingual sets for ESL); TalkBank ASDBank for autism-related interaction
data. Also: the email expects work committed to the shared GitHub repo
(github.com/OJAzzIC/ModellingLearning) — this working copy is currently not
a git repository, so clone/`git init` and reconcile before starting Sprint 1.
