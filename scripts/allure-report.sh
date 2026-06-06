#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
aggregate_dir="$repo_root/target/allure-results-aggregate"
report_dir="$repo_root/target/site/allure-report-aggregate"
open_report=false
run_verify=false

for arg in "$@"; do
  case "$arg" in
    --open)
      open_report=true
      ;;
    --verify)
      run_verify=true
      ;;
    *)
      echo "Unknown option: $arg" >&2
      echo "Usage: $0 [--verify] [--open]" >&2
      exit 2
      ;;
  esac
done

cd "$repo_root"

if [[ "$run_verify" == true ]]; then
  ./mvnw -fae verify
fi

if [[ ! -x "$repo_root/.allure/bin/allure" ]]; then
  ./mvnw -N -q allure:install
fi

rm -rf "$aggregate_dir" "$report_dir"
mkdir -p "$aggregate_dir"

results_count=0
while IFS= read -r -d '' results_dir; do
  results_count=$((results_count + 1))
  module_name="$results_dir"
  module_name="${module_name#./}"
  module_name="${module_name%/target/allure-results}"
  module_name="${module_name//\//-}"
  while IFS= read -r -d '' result_file; do
    file_name="$(basename "$result_file")"
    cp "$result_file" "$aggregate_dir/${module_name}__${file_name}"
  done < <(
    find "$results_dir" -type f \
      ! -name 'executor.json' \
      ! -name 'categories.json' \
      ! -name 'environment.properties' \
      ! -path '*/history/*' \
      -print0
  )
done < <(find . -path '*/target/allure-results' -type d ! -path './target/*' -print0 | sort -z)

if [[ "$results_count" -eq 0 ]]; then
  echo "No module allure results found. Run tests first, or pass --verify." >&2
  exit 1
fi

generate_args=(
  generate
  --output "$report_dir"
)

if [[ "$open_report" == true ]]; then
  generate_args+=(--open)
fi

generate_args+=("$aggregate_dir")

"$repo_root/.allure/bin/allure" "${generate_args[@]}"

echo "Aggregate Allure report generated at:"
echo "  $report_dir/index.html"
