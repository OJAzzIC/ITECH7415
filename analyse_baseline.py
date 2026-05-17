import os, glob, csv
from collections import defaultdict
SIM_RUNS_DIR = "sim_runs"
PAPER_TARGETS = {"Welfare": 24523, "Working": 33671, "Professional": 44086}
files = sorted(glob.glob(os.path.join(SIM_RUNS_DIR, "*summary*.csv")))
if not files:
    print("No CSV files found in sim_runs/. Run run_baseline.sh first.")
    exit()
print(f"Found {len(files)} run files.")
data = defaultdict(lambda: defaultdict(list))
for filepath in files:
    with open(filepath, newline='', encoding='utf-8') as f:
        for row in csv.DictReader(f):
            ses = row.get("SES","Unknown")
            age = int(row.get("Age",0))
            val = int(row.get("Unique_Encountered", row.get("Unique_Heard",0)))
            data[ses][age].append(val)
max_age = max(a for sd in data.values() for a in sd.keys())
print(f"\nResults at final age ({max_age}):\n")
print(f"{'SES':<15} {'Your Result':>14} {'Paper Target':>14} {'Diff %':>8}")
print("-"*55)
for ses, target in PAPER_TARGETS.items():
    key = next((k for k in data if k.lower()==ses.lower()), None)
    if not key:
        print(f"  {ses:<15} NOT FOUND"); continue
    vals = data[key].get(max_age,[])
    if not vals:
        print(f"  {ses:<15} No data at age {max_age}"); continue
    avg = sum(vals)/len(vals)
    pct = ((avg-target)/target)*100
    status = "✓ MATCH" if abs(pct)<20 else "✗ CHECK"
    print(f"  {ses:<15} {avg:>14,.0f} {target:>14,} {pct:>+7.1f}%  {status}")
print("\nDone. Share these results with Kathleen.")
