#!/usr/bin/env bash
# Deletes ALL users and everything tied to their keys from the LOCAL DEVELOPMENT stack:
# users (with public keys and key backups), conversations, messages and attachment files.
# The schema and Flyway history stay, so the app starts normally afterwards.
#
# It only talks to the Docker Compose containers of this repository (the `db` service and the
# attachments volume); it has no way to reach any other database. On purpose this is NOT a Flyway
# migration, so it can never run against a real deployment.
#
# Usage:  scripts/reset-dev-data.sh          (asks for confirmation)
#         scripts/reset-dev-data.sh --yes    (no prompt)
set -euo pipefail

cd "$(dirname "$0")/.."
[ -f docker-compose.yml ] || { echo "Run this from the cipherchat repository." >&2; exit 1; }
[ -f .env ] && set -a && . ./.env && set +a
DB_NAME=${POSTGRES_DB:-cipherchat}
DB_USER=${POSTGRES_USER:-cipherchat}

if [ "${CIPHERCHAT_ENV:-dev}" != "dev" ]; then
  echo "CIPHERCHAT_ENV=$CIPHERCHAT_ENV: refusing. This script is for local development only." >&2
  exit 1
fi
if ! docker compose ps --status running --services 2>/dev/null | grep -qx db; then
  echo "The local database container is not running. Start it with: docker compose up -d db" >&2
  exit 1
fi

counts() {
  docker compose exec -T db psql -U "$DB_USER" -d "$DB_NAME" -At -F ' ' -c \
    "select (select count(*) from users), (select count(*) from conversations),
            (select count(*) from messages), (select count(*) from attachments);" </dev/null
}
read -r USERS CONVERSATIONS MESSAGES ATTACHMENTS < <(counts)
echo "Local dev database '$DB_NAME' (Docker Compose service 'db') currently has:"
echo "  $USERS users, $CONVERSATIONS conversations, $MESSAGES messages, $ATTACHMENTS attachments"

if [ "${1:-}" != "--yes" ]; then
  read -r -p "Delete all of it, plus the stored attachment files? Type 'reset' to continue: " answer
  [ "$answer" = "reset" ] || { echo "Cancelled."; exit 1; }
fi

docker compose exec -T db psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -q <<'SQL'
BEGIN;
-- Order follows the foreign keys; RESTART IDENTITY so new accounts start from id 1 again.
TRUNCATE messages, attachments, conversations, users RESTART IDENTITY;
COMMIT;
SQL

# Encrypted attachment files: the Docker volume (backend container) and the folder used by ./mvnw spring-boot:run.
docker compose run --rm --no-deps -T --entrypoint sh backend -c 'rm -f /data/attachments/*.pgp' >/dev/null 2>&1 \
  || echo "Note: could not clear the attachments volume (build the backend image first: docker compose build backend)."
rm -f backend/data/attachments/*.pgp 2>/dev/null || true

read -r USERS CONVERSATIONS MESSAGES ATTACHMENTS < <(counts)
echo "Done: $USERS users, $CONVERSATIONS conversations, $MESSAGES messages, $ATTACHMENTS attachments."
cat <<'NOTE'

Now clear the browser's saved keys, or the old accounts' device keys stay in IndexedDB:
  Chrome/Edge: open http://localhost:3000, DevTools (F12) > Application > Storage > "Clear site data"
  Firefox:     open http://localhost:3000, padlock icon > "Clear cookies and site data..."
Do it in every browser/profile you used with CipherChat, then create new accounts.
NOTE
