-- Passphrase-protected private key backup, so a user can sign in on a new device with only their
-- password and key passphrase. The browser locks the key with the passphrase before upload; the
-- server then wraps it again with Vault Transit (envelope encryption) and stores that here.
-- Neither layer gives the server a usable private key.
ALTER TABLE users ADD COLUMN key_backup VARCHAR(40000);
ALTER TABLE users ADD COLUMN key_backup_updated_at TIMESTAMP WITH TIME ZONE;
