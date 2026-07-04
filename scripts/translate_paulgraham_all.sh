#!/usr/bin/env bash
set -u
export PATH="$HOME/.local/bin:$HOME/bin:$PATH"
LEDGER="${1:-translate}"
URLS="out/paulgraham_essay_urls.txt"
LOG="out/logs/paulgraham-translate-$(date +%Y%m%d-%H%M%S).log"
DONE="out/logs/paulgraham-translate.done"
FAIL="out/logs/paulgraham-translate.fail"
touch "$DONE" "$FAIL"
total=$(wc -l < "$URLS" | tr -d ' ')
echo "Starting Paul Graham batch: $total URLs ledger=$LEDGER" | tee -a "$LOG"
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
done < "$URLS"
echo "Finished Paul Graham batch. done=$(wc -l < "$DONE") fail=$(wc -l < "$FAIL") log=$LOG" | tee -a "$LOG"
