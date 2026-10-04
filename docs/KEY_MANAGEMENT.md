# Key management in CipherChat

This note explains how CipherChat protects private keys and secrets, why it is built this way,
and what it costs. It is short on purpose: read it before changing anything in
`frontend/src/lib/device-key.ts`, `backend/.../service/KeyBackupService.java` or `vault/`.

**The rule that never changes:** CipherChat is end-to-end encrypted. The server never sees
plaintext messages, plaintext attachments, or a usable (unencrypted) private key.

---

## 1. HashiCorp Vault in plain words

[Vault](https://developer.hashicorp.com/vault/docs/what-is-vault) is a server whose only job is
to keep secrets and do cryptography for other programs. Applications log in to Vault (with a
token, AppRole, Kubernetes, cloud IAM...), and a **policy** decides exactly which paths each one
may touch. Everything is audited. Vault stores its data encrypted and starts **sealed**: it cannot
read its own storage until it is unsealed with key shares or a cloud KMS.

Vault is organised in **secrets engines**. Two matter here:

| Engine | What it does | Good for |
| --- | --- | --- |
| **KV v2** (key/value, version 2) | Stores small secrets and keeps a version history, so you can roll back. You put a value in and read the same value out. | Passwords, API keys, the JWT signing secret, database credentials. |
| **Transit** ("encryption as a service") | Holds encryption keys that **never leave Vault**. You send data, it returns ciphertext like `vault:v1:...`; you send ciphertext back to decrypt. Keys can be rotated; old versions still decrypt. Nothing is stored. | Encrypting data your app keeps in its own database, without the app ever holding the key. |

[**Spring Cloud Vault**](https://docs.spring.io/spring-cloud-vault/reference/) connects Spring
Boot to Vault. With `spring.config.import: vault://` it logs in at start-up and loads KV secrets as
normal Spring properties (`app.jwt.secret`, `spring.datasource.password`), so they never have to be
in `application.yml` or environment files. It also gives you a `VaultTemplate` for calling Transit.

## 2. Why private keys do NOT go into Vault

It is tempting to "store the private key safely in Vault". In an end-to-end encrypted app that
would break the whole promise:

- Vault runs on **our** infrastructure and the backend can read whatever its policy allows. A
  private key in Vault is a private key the operator (or anyone who compromises the backend) can use.
  That is server-side encryption, not end-to-end.
- A court order, an insider, or a stolen backend token would then be enough to read messages.

So Vault protects **the server's own secrets** instead:

- the **JWT signing secret** and the **database credentials** (KV v2), so they are not lying in
  config files, and can be rotated in one place;
- a **second lock on the key backups** (Transit), so a stolen database dump alone is useless.

The user's private key stays a **user** secret, unlocked only on the user's device.

## 3. Protecting the key in the browser

- **WebCrypto** (`crypto.subtle`) can create an AES-GCM key with `extractable: false`. JavaScript
  can ask the browser to encrypt and decrypt with it, but can never read the key bytes, not even
  through `exportKey`.
- **IndexedDB** can store that `CryptoKey` object as it is (the browser keeps the raw key material
  internally). It survives reloads and restarts and is scoped to the site's origin.
- CipherChat encrypts ("wraps") the OpenPGP private key with that device key and stores only the
  wrapped bytes. Copying the IndexedDB files to another machine does not give a usable key without
  the browser's own protected storage, and the key never has to be typed again on this device.
- Limit: this protects against copying data at rest and against "export the key" scripts. It does
  **not** stop malicious JavaScript running inside the page (XSS) from *using* the key while the app
  is open. The strict Content Security Policy is still the main defence there.

## 4. How real end-to-end apps do it

- **Signal**: private keys are created on the phone and never leave it. A new phone gets keys by a
  direct device-to-device transfer, or starts fresh. Optional encrypted backups and "Secure Value
  Recovery" use a PIN that is stretched and protected inside secure hardware (SGX enclaves), so
  Signal's servers cannot read them.
- **ProtonMail**: the private key is encrypted in the browser with a key derived from the user's
  password and stored on Proton's servers, so you can log in anywhere. Proton never receives the
  password in a usable form (it uses SRP). A password reset without a recovery method means old
  mail cannot be decrypted.
- **WhatsApp / iMessage**: keys live on the device; encrypted cloud backups are opt-in and use a
  user key or HSM-protected key vaults.

The common pattern: **keys are made and used on the device; anything stored on a server is
encrypted with something only the user knows.** CipherChat follows the ProtonMail-style model.

## 5. The design CipherChat implements

```
Sign-up (once)                                    Login, same browser       Login, new browser
------------------------------------------------  ------------------------  ------------------------------
browser: make OpenPGP key pair                    password -> JWT           password -> JWT
browser: backup = private key locked with         browser: device key in    GET /api/keys/me/backup
         passphrase (OpenPGP S2K, AES-256)          IndexedDB unwraps the   browser: ask passphrase ONCE,
browser: device key = AES-GCM, non-extractable,     private key. No           unlock backup, make a new
         wraps the private key into IndexedDB       passphrase.               device key, wrap, store
POST /api/auth/register {publicKey, keyBackup}
server: check backup is encrypted + matches the
        public key, then Transit-encrypt it
        (derived key, context = user id) -> DB
```

- **Passphrase**: chosen once at sign-up. Asked again only on a new device/browser or after the
  browser's site data was cleared (account recovery). Never sent to the server.
- **On the device**: the private key is wrapped by a non-extractable AES-GCM key in IndexedDB. The
  unwrapped key exists only in memory while the app is open.
- **On the server**: the backup is the passphrase-locked key, wrapped again with Vault Transit
  (envelope encryption). The backend can remove the Transit layer, but what is left is still the
  passphrase-locked key: never a usable private key. The backend refuses any backup that is not
  passphrase-protected or does not match the account's public key.
- **Logout** keeps the device key, so the next login is passphrase-free. "Sign out and forget this
  device" deletes it.
- **Secrets**: the backend logs in to Vault with AppRole and reads the JWT secret and database
  credentials from KV v2. Its policy allows only that KV path and encrypt/decrypt on one Transit key.

### Trade-offs

| We gain | We pay |
| --- | --- |
| No passphrase on every login: much better everyday use. | Anyone who can use the unlocked browser profile can open the app as that user (same as staying logged in to email). "Forget this device" exists for shared computers. |
| Log in on a new device with only password + passphrase; no backup file to carry around. | The server now holds the passphrase-locked backup. Someone who steals **both** the database and Vault access (or the user's password) can try passphrases offline. Mitigations: 12+ character passphrase, the maximum OpenPGP S2K work factor, Transit so a database dump alone is not enough. |
| Transit: a stolen database dump or backup tape is useless without Vault. Keys can be rotated with `vault write -f transit/keys/.../rotate` and data re-wrapped with `rewrap`. | One more service to run, seal/unseal, back up and monitor. If Vault is down, new-device logins and new sign-ups fail (existing devices keep working). |
| Secrets in one audited place instead of `.env` files. | Dev mode Vault is in memory only; the dev stack works around that (see `vault/init.sh`), production needs a real Vault cluster. |
| OpenPGP S2K keeps the backup compatible with GnuPG (`gpg --import`). | S2K is weaker than Argon2 against GPU guessing. OpenPGP.js supports Argon2 (RFC 9580) but GnuPG 2.4 cannot read it yet; switch once it can. |
| Losing the passphrase never loses the account on devices already set up. | Losing the passphrase **and** every set-up device means old messages are gone for good. That is the price of the server never being able to read them. |
