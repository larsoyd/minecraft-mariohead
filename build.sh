#!/usr/bin/env bash
[ -n "${BASH_VERSION:-}" ] || exec bash "$0" "$@"
set -euo pipefail

root=$(cd "$(dirname "$0")" && pwd)
cd "$root"
printf '[build] building and testing Mario Head\n'
timeout --foreground 1200 ./gradlew --console=plain --max-workers=4 build </dev/null
version=$(sed -n 's/^version=//p' gradle.properties)
jar="$root/build/libs/mariohead-$version.jar"
[ -s "$jar" ] || { printf '[build] missing jar: %s\n' "$jar" >&2; exit 1; }
printf '[build] ready: %s\n' "$jar"
