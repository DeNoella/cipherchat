"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { useApp } from "@/components/providers";
import { Button, Card, Field, Notice } from "@/components/ui";
import { ApiError } from "@/lib/api";
import { generateKeyPair, unlockPrivateKey } from "@/lib/crypto";
import { keystore } from "@/lib/keystore";
import { session } from "@/lib/session";

type Errors = Partial<Record<"username" | "password" | "passphrase" | "confirm" | "form", string>>;

function validate(username: string, password: string, passphrase: string, confirm: string): Errors {
  const errors: Errors = {};
  if (!/^[A-Za-z0-9_]{3,32}$/.test(username)) errors.username = "3–32 characters: letters, digits or underscore";
  if (password.length < 10) errors.password = "At least 10 characters";
  if (passphrase.length < 12) errors.passphrase = "At least 12 characters";
  else if (passphrase === password) errors.passphrase = "Must be different from your password";
  if (confirm !== passphrase) errors.confirm = "Passphrases do not match";
  return errors;
}

export default function RegisterPage() {
  const { api } = useApp();
  const router = useRouter();
  const [errors, setErrors] = useState<Errors>({});
  const [step, setStep] = useState<"idle" | "generating" | "registering">("idle");

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const username = String(form.get("username") ?? "").trim();
    const password = String(form.get("password") ?? "");
    const passphrase = String(form.get("passphrase") ?? "");
    const confirm = String(form.get("confirm") ?? "");

    const found = validate(username, password, passphrase, confirm);
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    try {
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
      const privateKey = await unlockPrivateKey(keys.encryptedPrivateKey, passphrase);
      session.signIn(auth.token, auth.username, privateKey, keys.publicKey, keys.fingerprint);
      router.replace("/profile?welcome=1");
    } catch (err) {
      setStep("idle");
      if (err instanceof ApiError && err.status === 409) {
        setErrors({ username: err.message });
      } else if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        setErrors(err.fieldErrors);
      } else {
        setErrors({ form: err instanceof Error ? err.message : "Registration failed" });
      }
    }
  }

  const busy = step !== "idle";

  return (
    <Card>
      <h1 className="text-xl font-medium">Create your account</h1>
      <p className="mt-2 text-sm leading-relaxed text-muted">
        Your encryption key is created on this device. Only its public half is sent to the server.
      </p>

      <form onSubmit={onSubmit} className="mt-6 space-y-4" noValidate>
        <Field label="Username" name="username" autoComplete="username" required error={errors.username} />
        <Field
          label="Password"
          name="password"
          type="password"
          autoComplete="new-password"
          required
          hint="Signs you in to the server."
          error={errors.password}
        />
        <div className="border-t border-line pt-4" />
        <Field
          label="Key passphrase"
          name="passphrase"
          type="password"
          autoComplete="off"
          required
          hint="Locks your private key on this device. The server never sees it and cannot reset it."
          error={errors.passphrase}
        />
        <Field
          label="Confirm passphrase"
          name="confirm"
          type="password"
          autoComplete="off"
          required
          error={errors.confirm}
        />

        {errors.form && <Notice tone="danger">{errors.form}</Notice>}

        <Button type="submit" className="w-full" disabled={busy}>
          {step === "generating" ? "Generating key…" : step === "registering" ? "Creating account…" : "Create account"}
        </Button>
      </form>

      <p className="mt-6 text-sm text-muted">
        Already have an account?{" "}
        <Link href="/login" className="text-ink underline decoration-line-strong underline-offset-4 hover:decoration-ink">
          Sign in
        </Link>
      </p>
    </Card>
  );
}
