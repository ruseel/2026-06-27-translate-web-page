#!/usr/bin/env bash
set -u
export PATH="$HOME/.local/bin:$HOME/bin:$PATH"
LEDGER="${1:-translate}"
DONE="out/logs/paulgraham-translate.done"
FAIL="out/logs/paulgraham-translate.fail"
RETRY="out/logs/paulgraham-translate.retry"
LOG="out/logs/paulgraham-translate-failed-$(date +%Y%m%d-%H%M%S).log"
mkdir -p out/logs
touch "$DONE" "$FAIL"

# Retry only URLs that failed before and are not already marked done.
comm -23 <(sort -u "$FAIL") <(sort -u "$DONE") > "$RETRY"

total=$(wc -l < "$RETRY" | tr -d ' ')
echo "Starting Paul Graham failed-only retry: $total URLs ledger=$LEDGER" | tee -a "$LOG"

i=0
while IFS= read -r url; do
  [ -z "$url" ] && continue
  i=$((i+1))
  if grep -Fxq "$url" "$DONE"; then
    echo "[$i/$total] SKIP done $url" | tee -a "$LOG"
    continue
  fi
  echo "[$i/$total] START $url" | tee -a "$LOG"
  if bb page "$url" "$LEDGER" >>"$LOG" 2>&1; then
    echo "$url" >> "$DONE"
    echo "[$i/$total] OK $url" | tee -a "$LOG"
  else
    echo "$url" >> "$FAIL"
    echo "[$i/$total] FAIL $url" | tee -a "$LOG"
  fi
done < "$RETRY"

echo "Finished Paul Graham failed-only retry. done=$(sort -u "$DONE" | wc -l) remaining_failed=$(comm -23 <(sort -u "$FAIL") <(sort -u "$DONE") | wc -l) log=$LOG" | tee -a "$LOG"
