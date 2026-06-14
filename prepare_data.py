import os
import csv
import string

# Set directories for the extracted research data
input_dir = './resources/utterances'
output_file = './resources/utterances/processed_utterances.csv'

if not os.path.exists(input_dir):
    print(f"Error: {input_dir} not found. Ensure DATA.tar was extracted to resources/")
    exit()

# Characters stripped from the edges of each token.  Word-internal
# apostrophes/hyphens (e.g. "don't", "merry-go-round") are preserved.
STRIP_CHARS = string.punctuation + string.whitespace


def clean_utterance(line):
    """Lowercase, strip per-token punctuation; return '' if nothing remains."""
    tokens = []
    for token in line.lower().split():
        token = token.strip(STRIP_CHARS)
        if token:
            tokens.append(token)
    return ' '.join(tokens)


print("Starting data conversion...")
count = 0
skipped_files = 0

with open(output_file, 'w', newline='', encoding='utf-8') as f_out:
    writer = csv.writer(f_out)
    writer.writerow(['speaker_code', 'gloss'])  # Headers for Utterances.java

    for root, dirs, files in os.walk(input_dir):
        for filename in sorted(files):
            if not (filename.endswith('.txt') or filename.endswith('.cha')):
                continue
            wrote_any = False
            with open(os.path.join(root, filename), 'r', encoding='utf-8',
                      errors='ignore') as f_in:
                for line in f_in:
                    line = line.strip()
                    # CHA-format transcripts mark the mother's speech with a
                    # '*MOT:' prefix; the plain-text transcripts in this corpus
                    # are one utterance per line (with a 'text.' header line
                    # and '.'-only filler lines, both of which clean to '').
                    if line.startswith('*MOT:'):
                        line = line[5:]
                    elif line.startswith(('*', '%', '@')):
                        continue  # other CHA speakers/annotations
                    if line == 'text.':
                        continue
                    text = clean_utterance(line)
                    if text:
                        # Replicating Green et al. (2024) baseline: all input
                        # is treated as maternal speech ('mot').
                        writer.writerow(['mot', text])
                        count += 1
                        wrote_any = True
            if not wrote_any:
                skipped_files += 1

print(f"Success! Processed {count} utterances into {output_file}")
if skipped_files:
    print(f"Note: {skipped_files} transcript files contained no usable text "
          "(e.g. '.'-only placeholder files) and contributed nothing.")
