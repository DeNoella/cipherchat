---
title: "Learn PGP from the beginning, Part V: Limitations, proof, and interview prep"
description: The honest limits of CipherChat, hands-on proof with the running app, and interview answers you can memorise.
---

# Learn PGP from the beginning, Part V: Limitations, proof, and interview prep

<SeriesNav course="/cybersecurity/pgp-from-the-beginning/" />

I assume nothing, except that you've read Parts I to IV. This last part does three things. It's honest about what CipherChat does **not** protect against. It shows you how to **prove** the encryption yourself, with your own eyes. And it prepares you to **explain** it all in an interview.

## Resources

- [RFC 9580, the OpenPGP standard](https://www.rfc-editor.org/rfc/rfc9580): section 15 lists OpenPGP's security considerations.
- [OpenPGP.js documentation](https://docs.openpgpjs.org/)
- [BouncyCastle Java](https://www.bouncycastle.org/documentation/)
- [Spring Boot reference](https://docs.spring.io/spring-boot/)
- [The Signal protocol (Double Ratchet)](https://signal.org/docs/specifications/doubleratchet/): how forward secrecy is done in modern messengers.
- [CipherChat's own improvement list](https://github.com/angelabs-png/cipherchat/blob/0033149090aa01b53d9ccd7ccadaf8929bfe4974/DEPLOYMENT_AND_SUGGESTIONS.md#part-2-features-that-would-impress-a-cybersecurity-company)

## In this article we will cover

- **Limitations and threats.** What can still go wrong, and what would improve it.
- **See it with your own eyes.** Five hands-on checks with the running app, with the exact commands and output.
- **Interview prep.** A 30-second pitch, a 2-minute PGP explanation, 21 questions with answers, and a glossary.

[[toc]]

## Limitations and threats

Every security design has limits. Knowing yours is what makes you credible.

### Lost passphrase = lost messages on new devices

**The problem:** the server keeps a backup of your private key, but it's locked with your passphrase, and nobody can reset that. Forget the passphrase **and** lose every device that's already set up (or clear their site data), and every message sent to you is unreadable forever.

**Where you see it:** right after registering, the Profile page warns: *"Remember your key passphrase. You will not need it to sign in on this browser, but you will need it once on any new device or browser. Nobody, including us, can reset it."*

**What CipherChat already does:** devices you've set up keep working without the passphrase, and you can export an extra backup file from **Profile**.

### The server holds a locked backup

**The problem:** to make new devices easy, the server stores your passphrase-locked backup. Someone who gets that backup can try passphrases offline, as fast as their hardware allows.

**What CipherChat already does:** the backup is wrapped again by **HashiCorp Vault Transit** before it's stored, so a stolen database dump alone is useless. Getting it also needs your password (or access to Vault). Passphrases must be 12+ characters, and the passphrase lock uses OpenPGP's slowest standard setting.

**What could improve it:** **Argon2id** for the passphrase lock (OpenPGP.js supports it; it's held back only because GnuPG 2.4 can't read it yet), and a passphrase strength meter.

### No forward secrecy

**The problem:** every message sent to you is locked for the same long-term key. If an attacker ever gets your private key (from an unlocked device, or from your backup plus your passphrase), they can decrypt **all** your past messages, from any stolen database copy.

**What could improve it:** per-message keys, like the Signal protocol's Double Ratchet or MLS. A simpler step: rotate encryption subkeys regularly.

### Trusting the right public key

**The problem:** the first time you chat with someone, you trust the public key the server gives you. A malicious server could hand you its own key instead.

**What CipherChat already does:** it pins the first fingerprint it sees (trust on first use), warns loudly if a key changes, and blocks sending until you confirm. You can compare fingerprints out-of-band and click **mark verified**.

**What could improve it:** fingerprint QR codes to scan in person, and a public key transparency log.

### A compromised device, or the server sending bad code

**The problem:** once unlocked, your private key is in the browser's memory, and the browser can unlock it again from its device key without asking you. Malware on your computer, or a script injected into the page, could use it. Anyone with your unlocked computer and your password can sign in. And in any web app, the server sends the JavaScript that does the encryption. A compromised server could send a modified version.

**What CipherChat already does:** a strict, nonce-based Content-Security-Policy blocks injected scripts. Messages are rendered as text, never HTML. The device key is non-extractable, so the stored key can't be copied off the computer, and **Sign out and forget this device** deletes it on shared computers.

**What could improve it:** signed, reproducible frontend builds (so users can check the code), hardware-backed keys (WebAuthn), and Trusted Types.

### Metadata is visible

**The problem:** as covered in Part III, the server knows who talks to whom, when, and how much.

**What could improve it:** padding messages to fixed sizes, and "sealed sender" style delivery.

### Other honest notes

- **Logins last 12 hours** and can't be revoked early: there's no "log out other sessions". Short-lived tokens with refresh tokens would fix it.
- **Single server instance:** the login rate limiter and WebSocket broker are in memory. Several servers would need Redis and an external broker.

### Check yourself

1. An attacker steals a full database backup. What can they read?

::: details Answer
Metadata (usernames, who talks to whom, when, sizes), public keys, fingerprints and BCrypt password hashes. **Not** message content or attachments: those are ciphertext. The key backups are there only as Vault ciphertext (`vault:v1:...`), useless without Vault, and still passphrase-locked underneath.
:::

2. Why is "no forward secrecy" a real risk?

::: details Answer
Because one long-term key protects every message. If it's ever stolen with its passphrase, all past messages from any stored copy can be decrypted. Forward secrecy uses short-lived keys so old messages stay safe.
:::

## See it with your own eyes

Don't take my word for it. Here are five checks you can run yourself. Every output below is real: I ran these exact steps on CipherChat with two users, **alice** and **bob**.

### Before you start

Start the whole app with Docker. From the `cipherchat` folder:

```bash
cp .env.example .env      # first time only; replace each "replace-with-..." value with a random one
docker compose up -d --build
```

Let's break it down:

- **`cp .env.example .env`**: creates the settings file. It holds the database password and the dev Vault logins; there's no JWT secret in it, because Vault generates that.
- **`docker compose up -d --build`**: builds and starts the containers: `db` (PostgreSQL), `vault` (HashiCorp Vault in dev mode), `vault-init` (a one-off job that puts the server's secrets into Vault), `backend` (Spring Boot on port 8080) and `frontend` (Next.js on port 3000). `-d` runs them in the background.

> **Warning:** if you're already running the frontend yourself (`npm run dev`), stop it first. Otherwise port 3000 is taken, and Docker's frontend container can't start.

Then open <http://localhost:3000> twice: once in a normal window (register as **alice**) and once in a private window (register as **bob**). Two windows keep the two users' keys apart.

### 1. Only ciphertext leaves the browser (DevTools)

1. In alice's window, press <kbd>F12</kbd> to open DevTools, and click the **Network** tab.
2. Open a chat with bob, type `hello bob, this is secret` and click **Send**.
3. In the Network list, click the request named **messages** (`POST /api/messages`), then open the **Payload** tab.

**What you should see:** only `recipientUsername` and `ciphertext`. Your text isn't there. This is the exact body I captured from alice's browser, shortened:

```json
{
  "recipientUsername": "bob",
  "ciphertext": "-----BEGIN PGP MESSAGE-----\n\nwV4DD/XUvd2rdXUSAQdA/HGrB0RIQJ1ShP3XaASxvkIYa6NmBgKWEz8i/+JL\ntg0wGmoBQW6iSypMILuwHw0ymBv1km8AqgQN8yDVaA5fVRkavw9BZZNV3Mki\n3PRl+DbxwV4DaEoeSIR7eH4SAQdAW…"
}
```

Let's break it down:

- **`recipientUsername`**: the only readable field. The server needs it to deliver the message. That's metadata.
- **`ciphertext`**: 728 characters of armored PGP for a 25-character message.
- **`wV4D` appears twice**: each one starts a locked copy of the session key. One is for bob's key, one is for alice's (Part II).

<div class="screenshot-placeholder">
<strong>Screenshot to add:</strong> DevTools → Network → <code>messages</code> → Payload, showing the ciphertext.<br>
Save it as <code>blog/docs/public/screenshots/devtools-network.png</code> and replace this box with<br>
<code>![DevTools showing only ciphertext in the request](/screenshots/devtools-network.png)</code>
</div>

### 2. The database holds only `-----BEGIN PGP MESSAGE-----`

Open a PostgreSQL prompt inside the `db` container and list the messages:

```bash
docker compose exec db psql -U cipherchat -d cipherchat -c "SELECT id, sender_id, left(replace(ciphertext, E'\n', ' '), 45) AS ciphertext, length(ciphertext) AS chars FROM messages ORDER BY id;"
```

Let's break it down:

- **`docker compose exec db`**: runs a command inside the database container.
- **`psql -U cipherchat -d cipherchat`**: the PostgreSQL client, as user `cipherchat`, on database `cipherchat`.
- **`left(replace(...), 45)`**: shows only the first 45 characters of each ciphertext, on one line.

**What I got:**

```text
 id | sender_id |                  ciphertext                   | chars
----+-----------+-----------------------------------------------+-------
  1 |         1 | -----BEGIN PGP MESSAGE-----  wV4DD/XUvd2rdXUS |   728
  2 |         1 | -----BEGIN PGP MESSAGE-----  wV4DD/XUvd2rdXUS |   854
  3 |         2 | -----BEGIN PGP MESSAGE-----  wV4DaEoeSIR7eH4S |   712
(3 rows)
```

Three messages: `hello bob, this is secret`, `see attached` (with the file) and bob's reply `got it, thanks!`. None of those words are anywhere in the database. Now look at the users:

```bash
docker compose exec db psql -U cipherchat -d cipherchat -c "SELECT id, username, left(password_hash, 7) AS hash_start, left(public_key, 36) AS public_key_start, key_fingerprint FROM users ORDER BY id;"
```

```text
 id | username | hash_start |           public_key_start           |             key_fingerprint
----+----------+------------+--------------------------------------+------------------------------------------
  1 | alice    | $2a$12$    | -----BEGIN PGP PUBLIC KEY BLOCK----- | A8BE937A0EA7F2037A1532C73997ADE234F03A50
  2 | bob      | $2a$12$    | -----BEGIN PGP PUBLIC KEY BLOCK----- | CAFA3682F6E98D7BDDBBC0B725861F12BF19D2BC
  3 | carol    | $2a$12$    |                                      |
(3 rows)
```

Let's break it down:

- **`$2a$12$`**: every password is a BCrypt hash with cost 12. No passwords are stored.
- **`PUBLIC KEY BLOCK`**: only public keys. There's no private key column at all.
- **carol**: an account with no key yet. Messages to her are refused, and the chat shows *"carol has not set up encryption yet, so messages cannot be encrypted for them."*

![The chat screen for carol showing "carol has not set up encryption yet, so messages cannot be encrypted for them."](/screenshots/no-key.png)

> **Tip:** Using the manual setup instead of Docker Compose? The database container is called `cipherchat-db`, so use `docker exec -it cipherchat-db psql -U cipherchat -d cipherchat` and paste the `SELECT` at the prompt.

### 3. The stored attachment is unreadable

alice attached `plan.txt`, a 43-byte text file that says `Meeting notes: the launch moves to Friday.`. List the attachments folder inside the backend container:

```bash
docker compose exec backend ls -l /data/attachments
```

```text
total 4
-rw-r--r-- 1 app app 491 Sep 29 11:03 f910ee44-9a3f-4be8-abb6-3e09f25303cf.pgp
```

Now look inside it:

```bash
docker compose exec backend sh -c 'od -c /data/attachments/*.pgp | head -n 6'
```

```text
0000000 301   ^ 003 017 365 324 275 335 253   u   u 022 001  \a   @  \n
0000020 025   _   <   n   [   S  \0 342 355   T   B   ~   } 256 253 266
0000040  \t 231 336 364   #   o 214 366 234   ^   m 304 033 220 016   0
0000060 264 327   z   N  \f 307 344  \0 201 332 356 356   , 210   A 363
0000100 034 315   ? 304 212 244   v 307 331   : 252   N 357 226 330 232
0000120   &   a  \v   M 316 241 027   ] 237 271 377  \a   I 034   {  \0
```

Let's break it down:

- **The file name** is a random UUID plus `.pgp`. The real name `plan.txt` isn't on disk or in the database.
- **491 bytes** instead of 43: the encrypted data, two locked session-key copies and the signature.
- **`od -c`** prints each byte. It's binary noise: no `Meeting`, no `Friday`. The first byte, `301`, is the start of a PGP session-key packet, just like `wV4D` in the armored messages.

And yet bob's browser downloaded it, decrypted it and saved `plan.txt` with the original 43 bytes, identical to what alice sent.

### 4. Compare fingerprints between two users

1. In **alice's** window, open **Profile**. Read **Your key fingerprint**.
2. In **bob's** window, open the chat with alice and click **Key fingerprint**.

**What you should see:** the same 40 characters in both. In my run, both showed `A8BE 937A 0EA7 F203 7A15 32C7 3997 ADE2 34F0 3A50`:

![alice's Profile page showing her fingerprint A8BE 937A 0EA7 F203 ... and "Backup downloaded"](/screenshots/profile-backup.png)

![bob's chat with alice, with the fingerprint panel open showing the same A8BE 937A 0EA7 F203 ...](/screenshots/bob-chat.png)

They also match the `key_fingerprint` column from check 2 (`A8BE937A...`). The browser, the server and the other user all agree. In real life, you'd compare these over a call, then click **Fingerprints match — mark verified**.

### 5. Tamper test: a changed message is caught

Pretend you're an attacker with database access. Change **one character** near the end of alice's first message, inside the encrypted data:

```bash
docker compose exec db psql -U cipherchat -d cipherchat -c "UPDATE messages SET ciphertext = overlay(ciphertext placing CASE WHEN substr(ciphertext, length(ciphertext) - 150, 1) = 'A' THEN 'B' ELSE 'A' END from length(ciphertext) - 150 for 1) WHERE id = (SELECT min(id) FROM messages);"
```

Let's break it down:

- **`WHERE id = (SELECT min(id) FROM messages)`**: only the first message.
- **`length(ciphertext) - 150`**: a position near the end, inside the encrypted message body.
- **`overlay(... placing 'A' or 'B' ...)`**: replaces that one character with a different letter.
- **Output:** `UPDATE 1`. The server accepted it, because this bypassed the API entirely.

Now reload bob's window (no passphrase needed). **What you should see:** the first message in red: *"This message could not be decrypted with your key."* The other messages are untouched, still **Verified**.

![bob's chat. The first message shows in red "This message could not be decrypted with your key."; the others show Verified](/screenshots/tampered.png)

OpenPGP's integrity check noticed the change ("Modification detected") and refused to decrypt. The attacker can't change a word without being caught, and can't read anything either.

### Bonus: the key backup is locked twice

Look at what the database stores for alice's key backup:

```bash
docker compose exec db psql -U cipherchat -d cipherchat -c "SELECT username, left(key_backup, 30) FROM users;"
```

**What you should see:** only Vault ciphertext, never `-----BEGIN PGP PRIVATE KEY BLOCK-----`:

```text
 username |              left
----------+--------------------------------
 alice    | vault:v1:yoOtg6OITf4ADISYg+qh5
 bob      | vault:v1:mMt+YqY5VeYj4H/P7Mk9y
```

That's the outer lock (Vault Transit). Now sign in as alice in a **third** window (a new browser profile, so it has no key yet). After the password, the app asks for her key passphrase **once**: the server removed only the Vault lock and sent back the backup still locked with her passphrase, which only her browser can open. Sign out, sign in again in that window, and the passphrase isn't asked any more.

### Bonus: what a swapped key looks like

What if the server handed out a different key for bob? I simulated it by giving bob a brand-new key pair and uploading it through the real API (`PUT /api/keys/me`) with a small script. When alice reopened the chat:

![alice's chat with a warning: "bob's key has changed", the previous and new fingerprints, and sending disabled. bob's old reply now shows "Signature invalid"](/screenshots/key-changed.png)

- **"bob's key has changed"**, with the old and new fingerprints side by side.
- **Sending is blocked**: the box says *"Verify the new key to continue"*.
- bob's old reply now shows **Signature invalid**, because it was signed with his old key, and CipherChat only trusts his current one.

That's trust on first use (Part II) doing its job.

### Check yourself

1. In check 2, why can't the server simply `SELECT` the message text?

::: details Answer
There is no text column. The `messages` table only has `ciphertext`, and the server has no private key to decrypt it.
:::

2. In the tamper test, the database happily accepted the change (`UPDATE 1`). Why is that still safe?

::: details Answer
Because integrity is checked in the receiver's browser. OpenPGP detects the modification and refuses to decrypt, so bob sees an error instead of a forged message.
:::

## Interview prep

### Explain CipherChat in 30 seconds

Read it out loud until you can say it without looking.

> "CipherChat is an end-to-end encrypted chat app. When you register, your browser generates an OpenPGP key pair with OpenPGP.js. The private key stays on your device, locked by a key the browser won't let anyone read; the server only gets the public key and a backup locked with your passphrase, so you type that passphrase once at sign-up and again only on a new device. Every message and file is encrypted and signed in the browser, so the Spring Boot server only ever stores ciphertext. The server uses BouncyCastle to reject anything that isn't real PGP ciphertext for both people, and pushes new messages over WebSocket. The receiver's browser decrypts them and shows a Verified badge when the signature checks out."

### Explain PGP in 2 minutes

> "PGP, standardised as OpenPGP in RFC 9580, protects data with two kinds of cryptography working together.
>
> First, **key pairs**. Everyone has a public key they can share, and a private key they keep secret. Anything locked with your public key can only be unlocked with your private key.
>
> But public-key encryption is slow, so PGP uses **hybrid encryption**. It generates a random session key, encrypts the message with it using fast symmetric encryption, and then locks just that session key with each recipient's public key. In CipherChat, it's locked for the recipient and the sender, so both can read it.
>
> Second, **signatures**. The sender hashes the message and signs the hash with their private key. The receiver checks it with the sender's public key. If it matches, you know who sent it and that it wasn't changed. That's CipherChat's Verified badge. The encryption also has its own integrity check, so a modified ciphertext won't decrypt at all.
>
> Third, **trust**. How do you know a public key really belongs to someone? You compare its **fingerprint**, a hash of the key, through another channel. CipherChat remembers the first fingerprint it sees and warns you if it ever changes.
>
> Finally, the private key itself is protected: any copy that leaves your device is encrypted with a **passphrase**, so a stolen key file is useless on its own."

### 21 likely interview questions

::: details 1. Why client-side encryption?
So the server never sees plaintext or private keys. Even if the server is hacked, or the people running it are curious, they only get ciphertext and metadata. In CipherChat, `crypto.ts` is the only place encryption happens, and it runs in the browser.
:::

::: details 2. Why not just use HTTPS?
HTTPS only protects data between the browser and the server. The server decrypts it and sees everything. End-to-end encryption protects the message from the sender's device all the way to the receiver's, including from the server. CipherChat uses both: HTTPS for transport, PGP for content.
:::

::: details 3. What if a user forgets their passphrase?
Devices that are already set up keep working, because they unlock the key with their device key. But the backup can't be opened on a new device, and the server can't reset it, because it never had the passphrase. With no set-up device left, old messages are lost and they'd need a new key pair. That's a deliberate trade-off: if the server could recover it, the server could also read it.
:::

::: details 4. Why ECC (Curve25519) over RSA?
Much smaller keys for the same strength (256-bit ECC is roughly RSA-3072), and much faster key generation, which matters because CipherChat generates keys in the browser. It's also the modern default in OpenPGP. The server still accepts RSA of 2048 bits or more.
:::

::: details 5. What does BouncyCastle do if the browser does the encryption?
It inspects, it doesn't decrypt. It validates uploaded public keys (not a private key, not revoked, not expired, not weak, properly self-signed, has an encryption subkey), computes fingerprints, and reads which keys each message and file is encrypted to, to enforce "addressed to both participants".
:::

::: details 6. Can the server read messages?
No. It stores only armored PGP ciphertext and has no private keys. It can see metadata: who talks to whom, when, and message sizes.
:::

::: details 7. Why encrypt each message to the sender too?
So the sender can read their own sent messages later, on any device with their key. The server enforces it in `PublicKeyService.requireAddressedToBoth`.
:::

::: details 8. How do you stop the server swapping someone's public key?
Fingerprints. The browser computes the fingerprint itself, pins the first one it sees (trust on first use), and blocks sending with a warning if it changes. Users can compare fingerprints in person and mark a contact verified.
:::

::: details 9. What's the difference between the password and the passphrase?
The password authenticates you to the server on every sign-in; the server stores a BCrypt hash. The passphrase locks the backup of your private key, is asked only at sign-up and on a new device, and is never sent anywhere. The register form requires them to be different.
:::

::: details 10. Where is the private key stored?
On each device, in IndexedDB, encrypted with AES-GCM under a **non-extractable WebCrypto device key**, so sign-in needs no passphrase. When in use it's in memory only. The only copy on the server is a backup locked with the passphrase and wrapped again by Vault Transit. Users can also export that locked backup as a file.
:::

::: details 11. What happens if someone tampers with a message in the database?
OpenPGP's integrity check fails, so decryption throws "Modification detected", and the chat shows "This message could not be decrypted with your key." If a message is instead signed by the wrong key, it shows "Signature invalid".
:::

::: details 12. How do users authenticate?
Username and password, checked with BCrypt, then a JWT signed with HS256 and valid for 12 hours. It's sent as a Bearer token on every REST request and in the STOMP CONNECT frame for WebSocket.
:::

::: details 13. How do you protect the login from brute force?
`LoginAttemptGuard`: at most 5 failed logins per username per minute, and 20 auth requests per IP per minute, returning `429` with `Retry-After`. Unknown users get the same error and the same timing (a dummy BCrypt check), so usernames can't be discovered.
:::

::: details 14. How does real-time delivery work?
Messages are sent over REST, validated and saved. After the transaction commits, `MessageRelay` pushes the ciphertext over STOMP to `/user/queue/messages` for the recipient and the sender. Clients can't send over WebSocket at all.
:::

::: details 15. How are attachments protected?
Encrypted and signed in the browser in binary PGP format, uploaded as an opaque blob, checked server-side as real ciphertext, stored as `<uuid>.pgp`. The file name and type are inside the encrypted message. Only participants can download it; others get 404.
:::

::: details 16. What's forward secrecy, and does CipherChat have it?
It means stealing today's key can't decrypt yesterday's messages. CipherChat doesn't have it: one long-term key protects all messages. The Signal protocol's Double Ratchet or MLS would add it.
:::

::: details 17. What's the biggest remaining risk?
The web delivery model: the server sends the JavaScript that does the encryption, so a compromised server could send malicious code. Plus a compromised device. CipherChat mitigates script injection with a strict CSP, but signed reproducible builds would be the real fix.
:::

::: details 18. Why return 404 instead of 403 for other people's conversations?
A 403 would confirm the conversation or attachment exists. A 404 reveals nothing, so IDs can't be probed.
:::

::: details 19. How did you test the security properties?
58 backend tests, all passing, run against a real Vault in Docker (Testcontainers). For example: plaintext is rejected, a private key upload is rejected, an unprotected key backup is rejected, the backend's Vault policy can't read anything but its own secret, a message not addressed to the recipient is rejected, outsiders get 404, login errors don't reveal whether a user exists, rate limits work, security headers are present, and WebSocket connects are refused without a token. The PGP test data was generated by OpenPGP.js, the same library as the browser.
:::

::: details 20. Why not store the private keys in HashiCorp Vault?
Because Vault belongs to whoever runs the server. A key the server can unlock is a key the server could use, so it wouldn't be end-to-end any more. CipherChat uses Vault for the **server's** secrets (the JWT secret and database login, loaded by Spring Cloud Vault) and for a second lock (Transit) on the passphrase-locked backups. Removing that lock still leaves the passphrase lock, which only the user can open.
:::

::: details 21. What would you add next?
Forward secrecy, Argon2 for the key backup, fingerprint QR codes, 2FA or passkeys, short-lived tokens with refresh, and Redis for rate limiting across instances. The full list is in `DEPLOYMENT_AND_SUGGESTIONS.md`.
:::

### Glossary

| Term | In one line |
| --- | --- |
| **Encryption** | Turning data into unreadable ciphertext that only the right key can turn back. |
| **Plaintext** | The readable original, like `hello bob`. |
| **Ciphertext** | The scrambled result of encryption. |
| **Symmetric encryption** | One key locks and unlocks. |
| **Asymmetric encryption** | A key pair: public to lock, private to unlock. |
| **Public key** | The half you share; others use it to encrypt to you and check your signatures. |
| **Private key** | The half only you have; it decrypts and signs. |
| **Key pair** | A public key and its matching private key. |
| **Session key** | A random one-time symmetric key that encrypts one message. |
| **Hybrid encryption** | Session key for the data, public keys to lock the session key. |
| **Hash** | A short, fixed-size fingerprint of data; any change gives a different hash. |
| **Digital signature** | A hash signed with a private key, proving who sent the data and that it's unchanged. |
| **Fingerprint** | A hash of a public key, used to check you have the right key. |
| **Trust on first use (TOFU)** | Remember the first key you see for someone and warn if it changes. |
| **Passphrase** | The secret that locks the backup of your private key; asked at sign-up and on a new device only. |
| **Device key** | A non-extractable WebCrypto AES-GCM key in the browser that locks the private key on that device. |
| **Non-extractable key** | A browser key that code can use but never read or export. |
| **HashiCorp Vault** | A server for secrets; CipherChat keeps its own secrets there and uses it to wrap key backups. |
| **KV v2** | Vault's versioned key-value store for secrets. |
| **Transit** | Vault's "encryption as a service": it encrypts data with keys that never leave Vault. |
| **Envelope encryption** | Locking data with one key and keeping that key somewhere else (here: in Vault). |
| **AppRole** | A Vault login for applications: a role ID plus a secret ID. |
| **PGP** | Pretty Good Privacy, the 1991 program for encrypting and signing. |
| **OpenPGP** | The open standard for PGP; the current version is RFC 9580. |
| **RFC 9580** | The 2024 OpenPGP specification. |
| **ASCII armor** | PGP binary data written as text between `-----BEGIN PGP ...-----` lines. |
| **Curve25519 / Ed25519 / X25519** | Modern elliptic curves: Ed25519 for signatures, X25519 for encryption. |
| **RSA** | An older public-key algorithm needing much larger keys. |
| **End-to-end encryption (E2EE)** | Only the sender and receiver can read the content, not the server. |
| **Metadata** | Data about the messages: who, when, how big. |
| **Forward secrecy** | Stealing a key today can't decrypt past messages. |
| **OpenPGP.js** | The JavaScript OpenPGP library CipherChat uses in the browser. |
| **BouncyCastle** | The Java crypto library the server uses to inspect PGP data. |
| **Spring Boot** | The Java framework the CipherChat server is built with. |
| **Bean** | An object created and managed by Spring. |
| **Dependency injection** | Spring passing each class the objects it needs. |
| **Controller** | Handles HTTP requests for a set of URLs. |
| **Service** | Holds the business rules. |
| **Repository** | Reads and writes the database. |
| **Entity** | A Java class mapped to a database table. |
| **DTO** | A small object describing exactly what the API sends or receives. |
| **Flyway** | Runs versioned SQL files to build the database. |
| **JWT** | A signed token proving who you are, sent with each request. |
| **BCrypt** | A slow, salted password-hashing algorithm. |
| **CORS** | Browser rules for which websites may call an API. |
| **CSP** | Content-Security-Policy: browser rules for which scripts may run. |
| **WebSocket** | A connection that stays open so the server can push data. |
| **STOMP** | A simple messaging protocol on top of WebSocket, with destinations like `/user/queue/messages`. |
| **Rate limiting** | Limiting how many attempts are allowed per time window. |
| **IndexedDB** | A database built into the browser, used to store the device key and the locked private key. |

## Summary

- **CipherChat's limits:** a lost passphrase means lost messages on new devices, the server holds a (locked) backup, there's no forward secrecy, first contact trusts the server's key, a compromised device or served code is dangerous, and metadata is visible. Each has a known fix.
- **You can prove it:** the network, the database and the disk only ever contain PGP ciphertext, fingerprints match between users, and tampering is detected.
- **You can explain it:** 30 seconds for the app, 2 minutes for PGP, and 21 answers backed by real code.

That's the end of the series. Go back to [the course overview](./) any time, and good luck in your interview.
