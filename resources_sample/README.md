# Sample data (synthetic — safe to publish)

Everything in this folder is **computer-generated placeholder data** produced
by [`generate_sample_data.py`](../generate_sample_data.py). It contains no
sentence from any real corpus and may be shared freely.

It exists because the real corpus used for research **cannot be
redistributed**:

- **Utterances** derive from **CHILDES / TalkBank** (Brian MacWhinney).
  The data is citable and usable for research, but not re-hostable publicly.
  Obtain it from <https://childes.talkbank.org/> under TalkBank's terms.
- **Books** are transcripts of in-copyright picture books (see the CPB-LEX
  paper, Green, Keogh, Sun & O'Brien 2024, DOI 10.3758/s13428-023-02198-y —
  the published lexicon is at <https://osf.io/z7tpe/>, but the underlying
  full texts are not redistributable).

The synthetic data mirrors the real corpus's *statistical shape* (Zipfian
vocabulary, ~4-word mean utterance length, ~500-word books) so the simulation
runs end-to-end, but **results from it are demonstrations of the machinery
only** — they do not reproduce the paper's Table 6 targets.

## File formats (match your own data to these)

| Path | Format |
|---|---|
| `utterances/*utterances*.csv` | CSV with headers `speaker_code,gloss` — one utterance per row, e.g. `mot,look at the dog`. Every CSV in the folder whose name contains `utterances` is loaded. |
| `utterances/*participants*.csv` | CSV with headers `role,id` mapping speaker roles to codes, e.g. `Mother,MOT`. |
| `utterances_esl/…` | Same utterance-CSV format; an additional named pool wired up via `[[utterance_pool]]` in the config. |
| `books/*.txt` | Plain UTF-8 text, one file per book (whole text on one line is fine). Every `.txt` in the folder is loaded. |

## Using it

```sh
# quick 5-day feature sweep (~1 min)
./gradlew run -Pconf=scenarios/sample_smoke.conf

# full one-year, 3-children demo run (~10 min)
./gradlew run -Pconf=scenarios/sample_year.conf

# regenerate this folder (seeded, reproducible)
python3 generate_sample_data.py
```

To run with a real corpus instead, place it in `resources/` (which is
gitignored and never committed) using the same formats, and use the default
`simulation.conf`. `prepare_data.py` converts raw one-utterance-per-line
`.txt` files and CHAT `*MOT:` lines into the processed utterances CSV.
