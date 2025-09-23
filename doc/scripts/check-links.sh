#!/usr/bin/env bash
set -euo pipefail
root_dir="$(cd "$(dirname "$0")/../.." && pwd)"

# simple internal link check: grep relative markdown links and verify targets exist
cd "$root_dir"

errors=0
while IFS= read -r -d '' file; do
  # extract markdown links like `(path.md)` not starting with http, https, mailto
  while read -r link; do
    # strip anchors
    target="${link%%#*}"
    # ignore empty or external
    if [[ "$target" =~ ^https?://|^mailto: ]]; then
      continue
    fi
    # resolve relative to file dir
    dir="$(dirname "$file")"
    abs="$(realpath -m "$dir/$target")"
    if [[ ! -e "$abs" ]]; then
      echo "Broken link: $file -> $target" >&2
      errors=$((errors+1))
    fi
  done < <(grep -oE "\]\([^):]+\)" "$file" | sed -E 's/^\]\(([^)]+)\)$/\1/' )

done < <(find doc tinystore-domain-* -type f -name '*.md' -print0)

if [[ $errors -gt 0 ]]; then
  echo "Found $errors broken links" >&2
  exit 1
else
  echo "All checked links are valid"
fi
