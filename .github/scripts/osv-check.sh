#!/usr/bin/env bash
# Checks the resolved Maven dependencies of the launcher against the OSV vulnerability database (https://osv.dev).
# Usage: osv-check.sh <output of "gradlew dependencies --configuration runtimeClasspath">
# Exits with 1 if any dependency has a known vulnerability. Only package names and versions are sent.
set -euo pipefail
input="$1"
# "+--- group:artifact:1.0 -> 1.1 (*)" -> "group:artifact:1.1"
coords=$(grep -E '^[| +\-]*[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[0-9]' "$input" \
	| sed -E 's/^[| +\-]*//; s/ \([*c]\)$//; s/^([^:]+:[^:]+):[^ ]+ -> ([^ ]+)$/\1:\2/; s/ .*$//' \
	| sort -u)
if [ -z "$coords" ]; then
	echo "No dependencies found in $input" >&2
	exit 1
fi
queries=""
for c in $coords; do
	name="${c%:*}"; version="${c##*:}"
	queries="$queries{\"package\":{\"ecosystem\":\"Maven\",\"name\":\"$name\"},\"version\":\"$version\"},"
done
response=$(curl -sf -X POST https://api.osv.dev/v1/querybatch -d "{\"queries\":[${queries%,}]}")
count=$(echo "$coords" | wc -l | tr -d ' ')
if echo "$response" | grep -q '"vulns"'; then
	echo "Known vulnerabilities found (checked $count packages):"
	i=0
	for c in $coords; do
		result=$(echo "$response" | sed -E 's/^\{"results":\[//; s/\]\}$//' | awk -v RS='\},\{' -v n=$i 'NR==n+1')
		if echo "$result" | grep -q '"vulns"'; then
			echo "  $c: $(echo "$result" | grep -oE '"id":"[^"]+"' | cut -d'"' -f4 | tr '\n' ' ')"
		fi
		i=$((i+1))
	done
	exit 1
fi
echo "No known vulnerabilities in $count packages."
