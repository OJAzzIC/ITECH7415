#!/usr/bin/env python3
"""Generate a fully synthetic sample corpus in resources_sample/.

The real corpus (resources/) is licensed research data that cannot be
redistributed: the utterances derive from CHILDES/TalkBank (Brian
MacWhinney) and the books are in-copyright picture-book transcripts.
This script produces stand-in data with the same *file formats* and a
similar *statistical shape* (Zipfian vocabulary, ~4.4-word utterances,
~500-word books) so the simulation runs out of the box, while containing
zero sentences from the real corpus.

Usage:  python3 generate_sample_data.py [--seed 42] [--utterances 100000]
                                        [--books 200] [--out resources_sample]

Run the result with:  ./gradlew run -Pconf=scenarios/sample_smoke.conf
"""

import argparse
import csv
import math
import os
import random
import re

DICT_PATH = "/usr/share/dict/words"

# High-frequency child-directed-speech core: function words plus the
# handful of content words that dominate real CDS. These occupy the head
# of the Zipf distribution.
CORE_WORDS = [
    "you", "the", "it", "that", "a", "what", "is", "to", "and", "do",
    "look", "this", "on", "in", "there", "we", "your", "can", "no",
    "yes", "he", "she", "they", "one", "go", "see", "put", "want",
    "get", "like", "little", "big", "up", "down", "here", "come",
    "good", "now", "me", "my", "not", "have", "so", "oh", "okay",
    "let's", "where", "who", "why", "how", "are", "was", "did",
    "don't", "it's", "that's", "what's", "with", "for", "of", "at",
    "all", "some", "more", "again", "very", "too", "just", "then",
    "over", "under", "out", "off", "back", "away", "right", "wow",
]

# Sentence frames grouped by word count. Slots: {N}=noun-ish, {V}=verb-ish,
# {A}=adjective-ish — filled from the synthetic content vocabulary.
TEMPLATES = {
    1: ["wow", "look", "yes", "no", "okay", "careful", "goodnight", "hello"],
    2: ["look {N}", "good {N}", "come here", "that's {A}", "a {N}",
        "what's that", "oh dear", "your {N}"],
    3: ["see the {N}", "where's the {N}", "that's a {N}", "it's a {N}",
        "you like {N}", "find the {N}", "the {A} {N}", "he can {V}"],
    4: ["look at the {N}", "what a {A} {N}", "can you {V} it",
        "the {N} is {A}", "do you see it", "put the {N} down",
        "where did it go", "it's a {A} {N}"],
    5: ["do you see the {N}", "the {N} likes to {V}", "can you find the {N}",
        "what does the {N} say", "let's {V} the {A} {N}",
        "there is a {A} {N}", "the {A} {N} is {V}ing"],
    6: ["the {N} is on the {N}", "do you want the {A} {N}",
        "can you {V} the {N} again", "look at the {A} {N} there",
        "we can {V} it later okay", "the {N} and the {N} {V}ed"],
    7: ["the {A} {N} is under the {N}", "do you want to {V} the {N}",
        "what is the {A} {N} doing there", "let's put the {N} on the {N}",
        "the {N} went to see the {N}"],
    8: ["the {A} {N} is looking at the {N}", "can you help me {V} the {A} {N}",
        "we are going to {V} the {N} now", "the {N} put the {N} on the {N}"],
    9: ["do you think the {A} {N} can {V} the {N}",
        "the {N} and the {N} went to the {A} {N}",
        "let's see if the {A} {N} wants to {V}"],
    10: ["the {A} {N} and the {A} {N} are {V}ing over there",
         "do you remember when we saw the {A} {N} at the {N}"],
}
SLOT_RE = re.compile(r"\{[NVA]\}")


def load_content_vocab(size, rng):
    """Sample real (public-domain word-list) words for the content vocabulary,
    or fall back to pronounceable pseudo-words if no dictionary exists."""
    words = []
    if os.path.exists(DICT_PATH):
        with open(DICT_PATH, errors="ignore") as fh:
            words = [w.strip() for w in fh
                     if w[0].islower() and w.strip().isalpha()
                     and 3 <= len(w.strip()) <= 9]
        rng.shuffle(words)
        words = words[:size]
    if len(words) < size:  # fallback / top-up: syllable pseudo-words
        onsets = list("bdfgklmnprstwz") + ["ch", "sh", "bl", "gr", "st"]
        vowels = ["a", "e", "i", "o", "u", "oo", "ee", "ay"]
        codas = ["", "n", "t", "k", "p", "m", "sh", "ck", "ng"]
        seen = set(words)
        while len(words) < size:
            w = "".join(rng.choice(p) for p in (onsets, vowels, codas, onsets, vowels))
            if w not in seen:
                seen.add(w)
                words.append(w)
    return words


def zipf_weights(n, exponent=1.05, shift=2.7):
    return [1.0 / (rank + shift) ** exponent for rank in range(1, n + 1)]


class VocabSampler:
    """Zipf-weighted word sampler over core + content vocabulary."""

    def __init__(self, vocab, rng):
        self.vocab = vocab
        self.weights = zipf_weights(len(vocab))
        self.rng = rng

    def words(self, k):
        return self.rng.choices(self.vocab, weights=self.weights, k=k)


def fill(template, sampler):
    n_slots = len(SLOT_RE.findall(template))
    if not n_slots:
        return template
    picks = iter(sampler.words(n_slots))
    return SLOT_RE.sub(lambda m: next(picks), template)


def sample_length(rng, mean=4.4, cap=15):
    # Geometric-ish length distribution matching real CDS (mean ~4.4, median 4).
    length = 1 + math.floor(rng.expovariate(1.0 / (mean - 1)))
    return min(length, cap)


def make_utterance(rng, sampler):
    length = sample_length(rng)
    if length > 10:  # long utterances: join two frames with "and"
        first = rng.choice(TEMPLATES[rng.randint(4, 6)])
        rest = length - len(first.split()) - 1
        second = rng.choice(TEMPLATES[max(1, min(rest, 10))])
        return fill(first, sampler) + " and " + fill(second, sampler)
    group = TEMPLATES[max(1, min(length, 10))]
    return fill(rng.choice(group), sampler)


def make_book(rng, sampler, content_vocab):
    """A synthetic picture book: a small per-book topic vocabulary reused
    heavily, mirroring how real picture books repeat their own words."""
    topic = rng.sample(content_vocab[:6000], k=40)
    topic_sampler = VocabSampler(topic + CORE_WORDS[:30], rng)
    target = max(120, min(int(rng.lognormvariate(math.log(480), 0.55)), 1500))
    parts, count = [], 0
    while count < target:
        sentence = fill(rng.choice(TEMPLATES[rng.randint(3, 10)]), topic_sampler)
        parts.append(sentence)
        count += len(sentence.split())
    return " ".join(parts)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--utterances", type=int, default=100_000)
    ap.add_argument("--books", type=int, default=200)
    ap.add_argument("--vocab", type=int, default=28_000)
    ap.add_argument("--out", default="resources_sample")
    args = ap.parse_args()

    rng = random.Random(args.seed)
    content = load_content_vocab(args.vocab, rng)
    sampler = VocabSampler(CORE_WORDS + content, rng)

    utt_dir = os.path.join(args.out, "utterances")
    esl_dir = os.path.join(args.out, "utterances_esl")
    book_dir = os.path.join(args.out, "books")
    for d in (utt_dir, esl_dir, book_dir):
        os.makedirs(d, exist_ok=True)

    # Main utterance pool (same format as resources/utterances/*.csv).
    tokens, types = 0, set()
    with open(os.path.join(utt_dir, "sample_utterances.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["speaker_code", "gloss"])
        for _ in range(args.utterances):
            utt = make_utterance(rng, sampler)
            w.writerow(["mot", utt])
            words = utt.split()
            tokens += len(words)
            types.update(words)
    with open(os.path.join(utt_dir, "sample_participants.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["role", "id"])
        w.writerow(["Mother", "MOT"])

    # Small reduced-vocabulary pool standing in for an ESL corpus.
    esl_sampler = VocabSampler(CORE_WORDS + content[:2000], rng)
    with open(os.path.join(esl_dir, "sample_esl_utterances.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["speaker_code", "gloss"])
        for _ in range(args.utterances // 10):
            w.writerow(["mot", make_utterance(rng, esl_sampler)])

    book_tokens = 0
    for i in range(args.books):
        text = make_book(rng, sampler, content)
        book_tokens += len(text.split())
        with open(os.path.join(book_dir, f"sample_book_{i + 1:04d}.txt"), "w") as fh:
            fh.write(text)

    print(f"Wrote {args.utterances:,} utterances "
          f"({tokens:,} tokens, {len(types):,} unique types, "
          f"mean {tokens / args.utterances:.2f} words/utterance)")
    print(f"Wrote {args.books} books ({book_tokens:,} tokens, "
          f"mean {book_tokens / args.books:.0f} words/book)")
    print(f"Output in {args.out}/ — run with "
          f"./gradlew run -Pconf=scenarios/sample_smoke.conf")


if __name__ == "__main__":
    main()
