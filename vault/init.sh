#!/bin/sh
# Configures Vault for CipherChat. Safe to run again (idempotent).
#
#   KV v2     secret/cipherchat           JWT signing secret + database credentials for the backend
#   Transit   cipherchat-key-backup       envelope encryption of the users' passphrase-locked key backups
#   Policy    cipherchat-backend          read that one KV path, encrypt/decrypt with that one key, nothing else
#   AppRole   cipherchat-backend          how the backend logs in (role ID + secret ID)
#
# Docker Compose runs it against a DEV MODE Vault (in memory, unsealed, root token). That is for
# local development only. In production, run Vault as a real cluster and apply the same policy,
# engines and role with your own automation (see DEPLOYMENT_AND_SUGGESTIONS.md).
#
# Needs: VAULT_ADDR, VAULT_TOKEN (an admin token), DB_USERNAME, DB_PASSWORD, VAULT_ROLE_ID, VAULT_SECRET_ID.
# Optional: DEV_PERSIST_DIR (dev only): keeps the JWT secret and a backup of the Transit key there,
#           so restarting the in-memory dev Vault does not make existing key backups unreadable.
set -eu

: "${VAULT_ADDR:?}" "${VAULT_TOKEN:?}" "${DB_USERNAME:?}" "${DB_PASSWORD?}" "${VAULT_ROLE_ID:?}" "${VAULT_SECRET_ID:?}"
KV_PATH=secret/cipherchat
TRANSIT_KEY=cipherchat-key-backup
ROLE=cipherchat-backend
PERSIST=${DEV_PERSIST_DIR:-}

echo "Waiting for Vault at $VAULT_ADDR"
until vault status >/dev/null 2>&1; do sleep 1; done

# --- KV v2: the backend's own secrets -------------------------------------------------------
vault secrets list | grep -q '^secret/' || vault secrets enable -path=secret -version=2 kv

jwt_secret() {
  # Reuse the current secret (dev: from the persist dir, otherwise from Vault) so restarts do not log everyone out.
  if [ -n "$PERSIST" ] && [ -s "$PERSIST/jwt-secret" ]; then cat "$PERSIST/jwt-secret"; return; fi
  vault kv get -field=app.jwt.secret "$KV_PATH" 2>/dev/null && return
  head -c 48 /dev/urandom | base64 | tr -d '\n'
}
JWT_SECRET=$(jwt_secret)
if [ -n "$PERSIST" ]; then printf '%s' "$JWT_SECRET" > "$PERSIST/jwt-secret"; fi

vault kv put "$KV_PATH" \
  app.jwt.secret="$JWT_SECRET" \
  spring.datasource.username="$DB_USERNAME" \
  spring.datasource.password="$DB_PASSWORD" >/dev/null
echo "KV: wrote $KV_PATH"

# --- Transit: second lock on key backups ----------------------------------------------------
vault secrets list | grep -q '^transit/' || vault secrets enable transit
if ! vault read "transit/keys/$TRANSIT_KEY" >/dev/null 2>&1; then
  if [ -n "$PERSIST" ] && [ -s "$PERSIST/transit-key.backup" ]; then
    vault write "transit/restore/$TRANSIT_KEY" backup=@"$PERSIST/transit-key.backup" >/dev/null
    echo "Transit: restored $TRANSIT_KEY from the dev backup"
  elif [ -n "$PERSIST" ]; then
    # DEV ONLY: exportable + plaintext backup so the in-memory dev server can be restored. Never in production.
    vault write "transit/keys/$TRANSIT_KEY" type=aes256-gcm96 derived=true \
      exportable=true allow_plaintext_backup=true >/dev/null
    echo "Transit: created $TRANSIT_KEY (dev, backed up to $PERSIST)"
  else
    # derived=true: every encryption needs a context (the user ID), so a backup cannot be moved to another user.
    vault write "transit/keys/$TRANSIT_KEY" type=aes256-gcm96 derived=true >/dev/null
    echo "Transit: created $TRANSIT_KEY"
  fi
fi
if [ -n "$PERSIST" ]; then
  vault read -field=backup "transit/backup/$TRANSIT_KEY" > "$PERSIST/transit-key.backup"
fi

# --- Policy: least privilege for the backend ------------------------------------------------
vault policy write "$ROLE" - >/dev/null <<POLICY
# Read the backend's own secrets (KV v2 keeps data under data/).
path "secret/data/cipherchat" {
  capabilities = ["read"]
}
# Wrap and unwrap key backups. No read, rotate, export or delete on the key itself.
path "transit/encrypt/$TRANSIT_KEY" {
  capabilities = ["update"]
}
path "transit/decrypt/$TRANSIT_KEY" {
  capabilities = ["update"]
}
POLICY
echo "Policy: $ROLE"

# --- AppRole: how the backend logs in -------------------------------------------------------
vault auth list | grep -q '^approle/' || vault auth enable approle >/dev/null
vault write "auth/approle/role/$ROLE" token_policies="$ROLE" token_ttl=1h token_max_ttl=4h \
  secret_id_ttl=0 >/dev/null
vault write "auth/approle/role/$ROLE/role-id" role_id="$VAULT_ROLE_ID" >/dev/null
if ! vault write -field=token auth/approle/login role_id="$VAULT_ROLE_ID" secret_id="$VAULT_SECRET_ID" >/dev/null 2>&1; then
  vault write "auth/approle/role/$ROLE/custom-secret-id" secret_id="$VAULT_SECRET_ID" >/dev/null
fi
vault write -field=token auth/approle/login role_id="$VAULT_ROLE_ID" secret_id="$VAULT_SECRET_ID" >/dev/null
echo "AppRole: $ROLE can log in"
echo "Vault is ready for CipherChat"
