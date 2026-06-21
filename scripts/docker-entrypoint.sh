#!/bin/sh
set -eu

DB_FILE="${DB_PATH:-/app/server/data/chemical_ai.db}"

mkdir -p "$(dirname "$DB_FILE")"

if [ "${FORCE_BOOTSTRAP:-0}" = "1" ] || [ ! -f "$DB_FILE" ]; then
  npm run bootstrap
fi

exec npm start
