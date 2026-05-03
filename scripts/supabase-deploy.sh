#!/usr/bin/env bash
# Pushes everything in supabase/ to the linked remote project.
# Usage: ./scripts/supabase-deploy.sh
set -euo pipefail

cd "$(dirname "$0")/.."

if ! command -v supabase >/dev/null 2>&1; then
  echo "supabase CLI not found. Install with: brew install supabase/tap/supabase" >&2
  exit 1
fi

if [ ! -f supabase/config.toml ]; then
  echo "supabase/config.toml not found. Run the one-time bootstrap first (see README)." >&2
  exit 1
fi

# Optional: load SUPABASE_DB_PASSWORD (and any other env) from .env so we don't get prompted.
if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

echo "→ Pushing database migrations..."
supabase db push

if [ -d supabase/functions ] && compgen -G "supabase/functions/*/" > /dev/null; then
  echo "→ Deploying edge functions..."
  supabase functions deploy
else
  echo "→ No edge functions found, skipping."
fi

if [ "${SUPABASE_PUSH_CONFIG:-0}" = "1" ]; then
  echo "→ Pushing project config (auth, storage, api)..."
  supabase config push
else
  echo "→ Skipping config push (set SUPABASE_PUSH_CONFIG=1 to enable)."
fi

echo "✓ Supabase backend updated."
