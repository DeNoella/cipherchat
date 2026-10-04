"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState, type FormEvent } from "react";
import { useApp } from "@/components/providers";
import { Button, Card, Field, Notice, Spinner } from "@/components/ui";
import { ApiError, type AuthResponse } from "@/lib/api";
import {
  generateKeyPair,
  readPrivateKeyFingerprint,
  unlockPrivateKey,
  WrongPassphraseError,
  type UnlockedKey,
} from "@/lib/crypto";
import { MissingDeviceKeyError, protectOnDevice, unlockFromDevice } from "@/lib/device-key";
import { session, useHydrated } from "@/lib/session";

/**
 * "new-device": the account has a key, but this browser does not have it yet. The passphrase
 * unlocks the backup once, then the key is wrapped with a new device key for passphrase-free logins.
 */
type Stage =
  | { kind: "credentials" }
  | { kind: "new-device"; auth: AuthResponse }
  | { kind: "create-key"; auth: AuthResponse };

function describe(err: unknown): string {
  if (err instanceof WrongPassphraseError || err instanceof ApiError) return err.message;
  return err instanceof Error ? err.message : "Something went wrong";
}

function LoginForm() {
  const { api } = useApp();
  const router = useRouter();
  const params = useSearchParams();
  const hydrated = useHydrated();
  const [stage, setStage] = useState<Stage>({ kind: "credentials" });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const deviceMissing = params.get("device") === "missing";

  useEffect(() => {
    // Sent here because this browser no longer has the key: drop the token that came without it.
    if (deviceMissing) session.signOut();
  }, [deviceMissing]);

  function done(auth: AuthResponse, key: UnlockedKey, publicKey: string, fingerprint: string) {
    session.signIn(auth.token, auth.username, key, publicKey, fingerprint);
    const next = params.get("next");
    router.replace(next?.startsWith("/") && !next.startsWith("//") ? next : "/chats");
  }

  async function onCredentials(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const username = String(form.get("username") ?? "");
    const password = String(form.get("password") ?? "");
    setError(null);
    setBusy(true);
    try {
      const auth = await api.login(username, password);
      if (!auth.hasPublicKey) {
        setStage({ kind: "create-key", auth });
        return;
      }
      // Same device: the device key unlocks the private key. No passphrase.
      const local = await unlockFromDevice(auth.username, auth.fingerprint);
      if (local) {
        done(auth, local.privateKey, local.publicKey, local.fingerprint);
      } else {
        setStage({ kind: "new-device", auth });
      }
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 429
          ? "Too many attempts. Wait a minute and try again."
          : describe(err),
      );
    } finally {
      setBusy(false);
    }
  }

  /** Downloads the backup (or reads an exported file), unlocks it with the passphrase, keeps it on this device. */
  async function onNewDevice(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (stage.kind !== "new-device") return;
    const form = new FormData(event.currentTarget);
    const passphrase = String(form.get("passphrase") ?? "");
    const file = form.get("backup");
    const { auth } = stage;
    setError(null);
    setBusy(true);
    try {
      // The api client uses the session token, so hold the JWT (without a key) while we fetch.
      session.authenticate(auth.token, auth.username);
      let keyBackup: string;
      if (auth.hasKeyBackup) {
        keyBackup = (await api.getKeyBackup()).keyBackup;
      } else {
        if (!(file instanceof File) || file.size === 0) throw new Error("Choose your key backup file (.asc)");
        if (file.size > 64 * 1024) throw new Error("That file is too large to be a key backup");
        keyBackup = await file.text();
      }
      const fingerprint = await readPrivateKeyFingerprint(keyBackup).catch(() => {
        throw new Error("That is not an OpenPGP private key backup");
      });
      if (fingerprint !== auth.fingerprint) {
        throw new Error("This backup belongs to a different key than the one on your account");
      }
      const privateKey = await unlockPrivateKey(keyBackup, passphrase);
      const publicKey = privateKey.toPublic().armor();
      if (!auth.hasKeyBackup) await api.uploadKeyBackup(keyBackup); // next new device needs no file
      await protectOnDevice(auth.username, { privateKey, publicKey, fingerprint });
      done(auth, privateKey, publicKey, fingerprint);
    } catch (err) {
      session.signOut();
      setError(
        err instanceof ApiError && err.status === 404
          ? "No key backup is stored for your account. Choose the backup file you exported from your profile."
          : describe(err),
      );
    } finally {
      setBusy(false);
    }
  }

  async function onCreateKey(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (stage.kind !== "create-key") return;
    const form = new FormData(event.currentTarget);
    const passphrase = String(form.get("passphrase") ?? "");
    const confirm = String(form.get("confirm") ?? "");
    setError(null);
    if (passphrase.length < 12) return setError("Key passphrase: at least 12 characters");
    if (confirm !== passphrase) return setError("Passphrases do not match");
    setBusy(true);
    try {
      const { auth } = stage;
      const keys = await generateKeyPair(auth.username, passphrase);
      session.authenticate(auth.token, auth.username);
      await api.uploadKey(keys.publicKey);
      await api.uploadKeyBackup(keys.keyBackup);
      await protectOnDevice(auth.username, keys);
      session.signIn(auth.token, auth.username, keys.privateKey, keys.publicKey, keys.fingerprint);
      router.replace("/profile?welcome=1");
    } catch (err) {
      session.signOut();
      setError(describe(err));
    } finally {
      setBusy(false);
    }
  }

  if (!hydrated) return <Spinner />;

  if (stage.kind === "new-device") {
    const needsFile = !stage.auth.hasKeyBackup;
    return (
      <Card>
        <h1 className="text-xl font-medium">Set up this browser</h1>
        <p className="mt-2 text-sm leading-relaxed text-muted">
          This browser does not have your private key yet. Enter your key passphrase once to unlock your encrypted
          backup here. After that, signing in on this browser needs only your password.
        </p>
        <form onSubmit={onNewDevice} className="mt-6 space-y-4">
          {needsFile && (
            <div className="space-y-1.5">
              <Notice tone="warn">
                No key backup is stored for your account. Choose the backup file you exported from your profile.
              </Notice>
              <input
                type="file"
                name="backup"
                accept=".asc,.txt,application/pgp-keys"
                className="block w-full text-sm text-muted file:mr-3 file:rounded-md file:border file:border-line-strong file:bg-transparent file:px-3 file:py-1.5 file:text-sm file:text-ink"
              />
            </div>
          )}
          <Field
            label="Key passphrase"
            name="passphrase"
            type="password"
            autoComplete="off"
            required
            autoFocus
            hint="The passphrase you chose when you created your account. It never leaves this browser."
          />
          {error && <Notice tone="danger">{error}</Notice>}
          <Button type="submit" className="w-full" disabled={busy}>
            {busy ? "Unlocking…" : "Unlock and continue"}
          </Button>
        </form>
        <button onClick={() => setStage({ kind: "credentials" })} className="mt-6 text-sm text-muted hover:text-ink">
          Back
        </button>
      </Card>
    );
  }

  if (stage.kind === "create-key") {
    return (
      <Card>
        <h1 className="text-xl font-medium">Create an encryption key</h1>
        <p className="mt-2 text-sm leading-relaxed text-muted">
          Your account has no key yet. We will generate one on this device. Choose a key passphrase: it locks the backup
          of your key and is asked again only when you sign in on a new device or browser.
        </p>
        <form onSubmit={onCreateKey} className="mt-6 space-y-4">
          <Field label="Key passphrase" name="passphrase" type="password" autoComplete="off" required autoFocus />
          <Field label="Confirm passphrase" name="confirm" type="password" autoComplete="off" required />
          {error && <Notice tone="danger">{error}</Notice>}
          <Button type="submit" className="w-full" disabled={busy}>
            {busy ? "Generating…" : "Generate key"}
          </Button>
        </form>
      </Card>
    );
  }

  return (
    <Card>
      <h1 className="text-xl font-medium">Sign in</h1>
      {params.get("expired") && (
        <div className="mt-4"><Notice>Your session expired. Please sign in again.</Notice></div>
      )}
      {deviceMissing && (
        <div className="mt-4"><Notice tone="warn">{new MissingDeviceKeyError().message}</Notice></div>
      )}
      <form onSubmit={onCredentials} className="mt-6 space-y-4">
        <Field label="Username" name="username" autoComplete="username" required autoFocus />
        <Field label="Password" name="password" type="password" autoComplete="current-password" required />
        {error && <Notice tone="danger">{error}</Notice>}
        <Button type="submit" className="w-full" disabled={busy}>
          {busy ? "Signing in…" : "Sign in"}
        </Button>
      </form>
      <p className="mt-6 text-sm text-muted">
        New here?{" "}
        <Link href="/register" className="text-ink underline decoration-line-strong underline-offset-4 hover:decoration-ink">
          Create an account
        </Link>
      </p>
    </Card>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={<Spinner />}>
      <LoginForm />
    </Suspense>
  );
}
