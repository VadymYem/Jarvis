#!/usr/bin/env bash
set -euo pipefail

repository=${GITHUB_REPOSITORY:?}
run_list=$(mktemp)
trap 'rm -f "$run_list"' EXIT

gh api --paginate "repos/$repository/actions/runs?per_page=100" \
  --jq '.workflow_runs[] | [.id, .status, .event] | @tsv' | sort -nr > "$run_list"

failed=0
blocked=0
while IFS=$'\t' read -r run_id status event; do
  if [[ "$status" == queued && "$event" == dynamic ]]; then
    echo "GitHub retains orphaned dynamic run $run_id in queued state" >&2
    blocked=$((blocked + 1))
    continue
  fi
  if [[ "$status" != completed ]]; then
    gh api -X POST "repos/$repository/actions/runs/$run_id/cancel" >/dev/null || \
      gh api -X POST "repos/$repository/actions/runs/$run_id/force-cancel" >/dev/null || true
    for attempt in {1..10}; do
      status=$(gh api "repos/$repository/actions/runs/$run_id" --jq .status)
      [[ "$status" == completed ]] && break
      sleep 3
    done
  fi

  if [[ "$status" != completed ]]; then
    echo "GitHub has not completed run $run_id (status: $status)" >&2
    blocked=$((blocked + 1))
    continue
  fi
  if ! gh api -X DELETE "repos/$repository/actions/runs/$run_id" >/dev/null; then
    echo "Unable to delete run $run_id (status: $status)" >&2
    failed=1
  fi
done < <(tail -n +3 "$run_list")

echo "Retained newest runs: $(head -n 2 "$run_list" | cut -f1 | tr '\n' ' ')"
echo "GitHub-blocked runs: $blocked"
exit "$failed"
