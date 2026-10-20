#!/bin/bash
# Finder entry point: launch Android with automatic development updates.
cd "$(dirname "$0")" || exit 1
exec /bin/bash "./scripts/打开Echo.command" "$@"
