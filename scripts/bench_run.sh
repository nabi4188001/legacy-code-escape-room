#!/usr/bin/env bash
# Runs one model through the chosen rooms, several times each. Used by the bench workflow.
# Environment: LLM_API_KEY, LLM_MODEL, LLM_BASE_URL, LLM_SYSTEM_AS_USER, ROOMS, RUNS, MAX_ATTEMPTS
set -u
cd "$(dirname "$0")/.."

if [ -z "${LLM_API_KEY:-}" ]; then
  echo "::warning::No API key for ${LLM_MODEL}, skipping it."
  exit 0
fi

rooms_input="${ROOMS:-all}"
if [ "$rooms_input" = "all" ]; then
  rooms=$(ls rooms)
else
  rooms=$(echo "$rooms_input" | tr ',' ' ')
fi

for room in $rooms; do
  for i in $(seq 1 "${RUNS:-3}"); do
    echo "=== ${LLM_MODEL} / ${room} / run ${i}"
    java -jar escape-agent/target/escape-agent.jar --room "$room" --max-attempts "${MAX_ATTEMPTS:-5}" || true
    sleep 5
  done
done
