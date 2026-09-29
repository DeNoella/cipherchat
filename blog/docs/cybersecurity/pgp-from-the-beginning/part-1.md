---
title: "Learn PGP from the beginning, Part I: Why encryption, and the two kinds of keys"
description: Why encryption exists, symmetric vs asymmetric keys, shown through creating an account in CipherChat.
---

# Learn PGP from the beginning, Part I: Why encryption, and the two kinds of keys

<SeriesNav course="/cybersecurity/pgp-from-the-beginning/" />

I assume nothing. You don't need to know anything about encryption or programming to read this. If you can create an account on a website, you can follow along.

Throughout this series, I'll use **CipherChat**, an end-to-end encrypted chat app I built. Every idea is shown on a real screen, and proved with the real code.

## Resources

- [OpenPGP.js documentation](https://docs.openpgpjs.org/): the library CipherChat uses in the browser.
- [RFC 9580, the OpenPGP standard](https://www.rfc-editor.org/rfc/rfc9580): the official rules of PGP.
- [BouncyCastle Java](https://www.bouncycastle.org/documentation/): the library the CipherChat server uses to check PGP data.
- [Spring Boot reference](https://docs.spring.io/spring-boot/): the framework behind the CipherChat server.
- [CipherChat source code](https://github.com/angelabs-png/cipherchat/tree/0033149090aa01b53d9ccd7ccadaf8929bfe4974): pinned to the exact commit this course describes.

## In this article we will cover

- **Why encryption exists.** What goes wrong when you send a secret over the internet.
- **Symmetric encryption.** One key locks and unlocks. Simple, but sharing that key is the hard part.
- **Asymmetric encryption.** A key pair: one public, one private. This is the idea that makes PGP possible.

[[toc]]

## Why encryption exists

### The idea in one sentence

Anything you send over the internet passes through machines you don't control, so encryption turns it into scrambled text that only the right person can turn back.

### Where you see it in CipherChat

**Journey step 6: sending a message.** You type a message and click **Send**. For a moment the button says **Encrypting…**. That's your browser scrambling the text *before* it leaves your computer.

Your message goes through your Wi-Fi, your internet provider and the CipherChat server. Then the server stores it in a database. Any of those could be spied on, hacked or simply curious. Without encryption, every one of them could read your message.

> **Note:** HTTPS (the padlock in your browser) protects the message *on the way* to the server. But the server itself still sees it. End-to-end encryption means even the server only sees scrambled text.

### A simple comparison

Sending a message without encryption is like sending a postcard. Everyone who handles it along the way can read it.

### Proof

In the chat screen, the message is encrypted first, and only the encrypted result is sent to the server ([`chats/[username]/page.tsx` lines 136–138](https://github.com/angelabs-png/cipherchat/blob/0033149090aa01b53d9ccd7ccadaf8929bfe4974/frontend/src/app/%28app%29/chats/%5Busername%5D/page.tsx#L136-L138)):

```tsx:line-numbers=136
      const envelope: MessageEnvelope = { v: 1, text: body, ...(attachment ? { attachment } : {}) };
      const ciphertext = await encryptEnvelope(envelope, peer.publicKey, myPublicKey, privateKey);
      const saved = await api.sendMessage(peer.username, ciphertext, attachment?.id);
```

Let's break it down:

- **`envelope`**: the message you typed (`text: body`), wrapped in a small object. If you attached a file, its name and size go in here too.
- **`encryptEnvelope(...)`**: scrambles the envelope in the browser. The result is called **ciphertext**.
- **`api.sendMessage(..., ciphertext, ...)`**: sends only the ciphertext to the server. The readable `body` is never sent.

The server double-checks this. If someone sends plain text, it refuses ([`OpenPgpInspector.java` lines 159–161](https://github.com/angelabs-png/cipherchat/blob/0033149090aa01b53d9ccd7ccadaf8929bfe4974/backend/src/main/java/com/cipherchat/service/OpenPgpInspector.java#L159-L161)):

```java:line-numbers=159
    public Set<Long> encryptedMessageRecipients(String armored) {
        if (armored == null || !armored.stripLeading().startsWith("-----BEGIN PGP MESSAGE-----")) {
            throw new InvalidPgpDataException("Ciphertext must be an ASCII-armored OpenPGP message");
```

Let's break it down:

- **`armored`**: the text the browser sent as the message.
- **`startsWith("-----BEGIN PGP MESSAGE-----")`**: every PGP-encrypted message starts with this header line.
- **`throw new InvalidPgpDataException(...)`**: if it doesn't start that way, the server rejects it with a `400 Bad Request`. Plain text like "hello bob" can never be stored.

### Check yourself

1. HTTPS already encrypts traffic. Why does CipherChat encrypt messages *again* in the browser?

::: details Answer
HTTPS only protects the trip between your browser and the server. The server decrypts HTTPS and would see the message. CipherChat encrypts in the browser so the server only ever sees ciphertext.
:::

2. What happens if a buggy client sends `"hello bob"` as a message?

::: details Answer
The server rejects it with `400 Bad Request` and the error "Ciphertext must be an ASCII-armored OpenPGP message", because it doesn't start with `-----BEGIN PGP MESSAGE-----`. The test `rejectsPlaintext` in `MessageControllerTest` checks exactly this.
:::

## Symmetric encryption: one key locks and unlocks

### The idea in one sentence

Symmetric encryption uses **one secret key** to both lock (encrypt) and unlock (decrypt) data.

### Where you see it in CipherChat

**Journey step 2: choosing a passphrase.** When you create an account, you choose a **Key passphrase**. The hint under it says:

> *"Locks your private key on this device. The server never sees it and cannot reset it."*

That's symmetric encryption. The same passphrase locks your private key when it's created (step 2), and unlocks it every time you sign in (step 4). One secret, both directions.

![The CipherChat "Create your account" screen with Username, Password, Key passphrase and Confirm passphrase fields](/screenshots/register.png)

### A simple comparison

A house key. The same key locks the door and opens it. Anyone who copies your key gets in.

### Why sharing the key is hard

Symmetric keys work well when **only you** need the key, like your passphrase. But imagine chatting with another CipherChat user using one shared key. You'd first have to send them the key. Send it how? Over the same internet you don't trust. Anyone who sees the key can read every message.

This is called the **key distribution problem**. Asymmetric encryption solves it.

### Proof

The passphrase is passed in when the key pair is generated, and needed again to unlock it ([`crypto.ts` lines 24–43](https://github.com/angelabs-png/cipherchat/blob/0033149090aa01b53d9ccd7ccadaf8929bfe4974/frontend/src/lib/crypto.ts#L24-L43)). Here are the key parts:

```ts:line-numbers=36
export async function unlockPrivateKey(encryptedPrivateKey: string, passphrase: string) {
  const privateKey = await openpgp.readPrivateKey({ armoredKey: encryptedPrivateKey });
  try {
    return await openpgp.decryptKey({ privateKey, passphrase });
  } catch {
    throw new WrongPassphraseError();
  }
}
```

Let's break it down:

- **`encryptedPrivateKey`**: your private key, stored *locked* in the browser.
- **`openpgp.decryptKey({ privateKey, passphrase })`**: uses the passphrase to unlock the private key. The same passphrase locked it during registration.
- **`throw new WrongPassphraseError()`**: a wrong passphrase can't unlock it. The screen then shows *"Wrong passphrase. Your key could not be unlocked."*

![The "Unlock your key" screen showing the error "Wrong passphrase. Your key could not be unlocked."](/screenshots/unlock-error.png)

> **Note:** Under the hood, OpenPGP turns your passphrase into a symmetric key and uses it to encrypt the private key. That's why the server can't reset it: the server never had it.

### Check yourself

1. In CipherChat, what does the key passphrase lock?

::: details Answer
Your private key, stored in your browser. The same passphrase unlocks it when you sign in.
:::

2. Why not just give every pair of users one shared secret key to chat with?

::: details Answer
Because you'd have to send that key over the internet first, and anyone who intercepts it can read everything. That's the key distribution problem.
:::

## Asymmetric encryption: public key and private key

### The idea in one sentence

Asymmetric encryption uses a **pair** of keys: a **public key** that anyone can use to lock a message for you, and a **private key** that only you have, to unlock it.

### Where you see it in CipherChat

**Journey step 1 and 2: creating an account and generating a key pair.** You fill in the form and click **Create account**. The button briefly says **Generating key…**, then **Creating account…**.

In that moment, your browser creates your key pair. The screen tells you exactly what happens next:

> *"Your encryption key is created on this device. Only its public half is sent to the server."*

**Journey step 3: uploading the public key.** The public key is sent along with your registration. The private key stays in your browser, locked with your passphrase.

Notice you also chose a **Password**. It's a different secret, with a different job: the password signs you in to the server, while the passphrase locks your key. The form even refuses a passphrase that's the same as your password.

### A simple comparison

Think of an open padlock. You can hand out copies of your open padlock to anyone (that's the public key). Anyone can snap one shut on a box addressed to you. But only your key (the private key) opens it.

### Proof

On the register screen, the key pair is generated first, then only the public key is sent ([`register/page.tsx` lines 44–58](https://github.com/angelabs-png/cipherchat/blob/0033149090aa01b53d9ccd7ccadaf8929bfe4974/frontend/src/app/%28auth%29/register/page.tsx#L44-L58)):

```tsx:line-numbers=44
      setStep("generating");
      const keys = await generateKeyPair(username.toLowerCase(), passphrase);

      setStep("registering");
      const auth = await api.register(username, password, keys.publicKey);
      if (auth.fingerprint !== keys.fingerprint) {
        throw new Error("The server reported a different fingerprint for your key. Aborting.");
      }
      await keystore.put({
        username: auth.username,
        publicKey: keys.publicKey,
        encryptedPrivateKey: keys.encryptedPrivateKey,
        fingerprint: keys.fingerprint,
        createdAt: new Date().toISOString(),
      });
```

Let's break it down:

- **`setStep("generating")`**: switches the button text to "Generating key…".
- **`generateKeyPair(username, passphrase)`**: creates the key pair in the browser. The private half comes back already locked with the passphrase.
- **`api.register(username, password, keys.publicKey)`**: sends the username, password and **only the public key**. The private key isn't in this call.
- **`auth.fingerprint !== keys.fingerprint`**: a safety check. The server calculates the key's fingerprint too. If it doesn't match, something is wrong and registration stops. (More on fingerprints in Part II.)
- **`keystore.put({... encryptedPrivateKey ...})`**: saves the locked private key in the browser's own storage (IndexedDB). It never goes to the server.

Here is the key pair being created ([`crypto.ts` lines 24–33](https://github.com/angelabs-png/cipherchat/blob/0033149090aa01b53d9ccd7ccadaf8929bfe4974/frontend/src/lib/crypto.ts#L24-L33)):

```ts:line-numbers=24
export async function generateKeyPair(username: string, passphrase: string): Promise<GeneratedKeys> {
  const { publicKey, privateKey } = await openpgp.generateKey({
    type: "ecc",
    curve: "curve25519Legacy", // Ed25519 signing + X25519 (ECDH) encryption subkey
    userIDs: [{ name: username }],
    passphrase,
    format: "armored",
  });
  return { publicKey, encryptedPrivateKey: privateKey, fingerprint: await fingerprintOf(publicKey) };
}
```

Let's break it down:

- **`openpgp.generateKey`**: OpenPGP.js creates a new key pair, right here in the browser.
- **`type: "ecc"`, `curve: "curve25519Legacy"`**: the kind of maths used. It's modern elliptic-curve cryptography (Part II explains why it's used instead of RSA).
- **`userIDs: [{ name: username }]`**: labels the key with your username.
- **`passphrase`**: locks the private key immediately. It's never stored unlocked.
- **`format: "armored"`**: produces text starting with `-----BEGIN PGP ...-----`, easy to send as JSON.

And the server refuses a private key, just in case someone tries ([`OpenPgpInspector.java` lines 65–68](https://github.com/angelabs-png/cipherchat/blob/0033149090aa01b53d9ccd7ccadaf8929bfe4974/backend/src/main/java/com/cipherchat/service/OpenPgpInspector.java#L65-L68)):

```java:line-numbers=65
        if (armored == null || !armored.contains(PUBLIC_KEY_HEADER)) {
            if (armored != null && armored.contains("PRIVATE KEY BLOCK")) {
                throw new InvalidPgpDataException("That is a PRIVATE key. Never upload it; upload your public key only");
            }
```

Let's break it down:

- **`PUBLIC_KEY_HEADER`**: the text `-----BEGIN PGP PUBLIC KEY BLOCK-----`.
- **`contains("PRIVATE KEY BLOCK")`**: if the upload looks like a private key instead, the server refuses it with a clear warning and doesn't store it.

### Check yourself

1. During registration, which key goes to the server, and which stays in the browser?

::: details Answer
The **public key** goes to the server, inside the `api.register(...)` call. The **private key** stays in the browser's IndexedDB, locked with your passphrase.
:::

2. What's the difference between your password and your key passphrase in CipherChat?

::: details Answer
The **password** signs you in to the server (the server stores a BCrypt hash of it). The **passphrase** locks your private key in the browser, and the server never sees it. The register form rejects a passphrase that equals the password.
:::

## Summary

- **Encryption** turns a message into ciphertext, so the machines it passes through can't read it. CipherChat encrypts in the browser, and the server rejects anything that isn't PGP ciphertext.
- **Symmetric encryption** uses one key to lock and unlock. CipherChat uses it for your passphrase, which locks your private key. Sharing one key between people is the hard part.
- **Asymmetric encryption** uses a key pair. Your browser creates it when you register, sends only the public key, and keeps the private key locked on your device.

In Part II, we'll see how PGP combines both kinds of keys to lock a real message, and how the "Verified" badge proves who sent it.

**Next:** [Part II: How PGP actually protects a message](./part-2)
