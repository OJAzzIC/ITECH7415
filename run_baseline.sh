#!/bin/bash
RUNS=30
OUTPUT_DIR="sim_runs"
mkdir -p "$OUTPUT_DIR"
echo "Starting $RUNS simulation runs..."
for i in $(seq 1 $RUNS); do
    echo "=== Run $i of $RUNS ==="
    ./gradlew -q --console=plain
    SUMMARY=$(ls *summary*.csv 2>/dev/null | head -1)
    if [ -n "$SUMMARY" ]; then
        mv "$SUMMARY" "$OUTPUT_DIR/run_${i}_summary.csv"
        echo "Saved: $OUTPUT_DIR/run_${i}_summary.csv"
    fi
    WORDLIST=$(ls *word_list*.csv 2>/dev/null | head -1)
    if [ -n "$WORDLIST" ]; then
        mv "$WORDLIST" "$OUTPUT_DIR/run_${i}_word_list.csv"
    fi
done
echo "All $RUNS runs complete. Now run: python3 analyse_baseline.py"
