#!/bin/bash
# Demo-morning URL refresh for the "URL exact-match" beat.
#
# URLhaus listings rotate, so the exact-match step cannot be scripted in advance. Run this
# on the morning of the demo to find a currently-online URL on a shared host.
#
# It ONLY queries the URLhaus API. It never opens, fetches, resolves or launches anything —
# the same rule the app itself follows (PRD v5.3 §2, Principle 3).
#
# The auth key is read from secrets.properties at run time. That file is gitignored and no
# key is embedded here.
#
# Usage:  ./tools/demo/refresh-control.sh [host]      (host defaults to github.com)
#
# Exit 0 = an online entry exists, use the URL it prints for the CONFIRMED step.
# Exit 1 = no online entry this morning. DROP the exact-match step and present the
#          host-level case alone. Do not assert a result you have not just verified.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT" || { echo "cannot locate repository root" >&2; exit 2; }

HOST="${1:-github.com}"

if [ ! -f secrets.properties ]; then
    echo "secrets.properties not found in $REPO_ROOT" >&2
    echo "It is gitignored by design. Recreate it with your URLhaus auth key:" >&2
    echo "    URLHAUS_AUTH_KEY=<key>" >&2
    exit 2
fi

KEY=$(grep -i URLHAUS secrets.properties | head -1 | cut -d= -f2- | tr -d '[:space:]')
if [ -z "$KEY" ]; then
    echo "No URLhaus key found in secrets.properties" >&2
    exit 2
fi

RESPONSE=$(curl -s --fail-with-body -X POST "https://urlhaus-api.abuse.ch/v1/host/" \
    -H "Auth-Key: $KEY" -H "accept: application/json" \
    -d "host=$HOST") || { echo "URLhaus request failed" >&2; exit 2; }

printf '%s' "$RESPONSE" | python -c "
import sys, json
try:
    d = json.load(sys.stdin)
except ValueError:
    print('URLhaus returned a non-JSON response'); raise SystemExit(2)

status = d.get('query_status')
if status != 'ok':
    print(f'URLhaus query_status={status} -> no usable listing.')
    print('DROP the exact-match step; present the host-level case alone.')
    raise SystemExit(1)

urls = d.get('urls') or []
online = [u for u in urls if u.get('url_status') == 'online']
print(f'$HOST  url_count={d.get(\"url_count\")}  returned={len(urls)}  ONLINE={len(online)}')

if not online:
    print()
    print('NO ONLINE ENTRIES -> DROP the URL exact-match CONFIRMED step.')
    print('Present the host-level result alone. Do not claim CONFIRMED.')
    raise SystemExit(1)

print()
print('USE THIS for the exact-match step (paste into the app - do NOT open it in a browser):')
print('   ', online[0]['url'])
if len(online) > 1:
    print()
    print('backups:')
    for u in online[1:4]:
        print('   ', u['url'])
"
