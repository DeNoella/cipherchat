package com.cipherchat.crypto;

import com.cipherchat.support.Fixtures;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenPgpInspectorTest {

    private final OpenPgpInspector inspector = new OpenPgpInspector(Clock.systemUTC());

    @Test
    void acceptsOpenPgpJsCurve25519Key() {
        PublicKeyInfo info = inspector.inspectPublicKey(Fixtures.text("alice.pub.asc"));

        assertThat(info.fingerprint()).matches("[0-9A-F]{40}");
        assertThat(info.algorithm()).isEqualTo("EdDSA/Ed25519 + ECDH/Curve25519");
        assertThat(info.userIds()).containsExactly("alice");
        assertThat(info.encryptionKeyIds()).hasSize(1);
        assertThat(info.armored()).startsWith("-----BEGIN PGP PUBLIC KEY BLOCK-----");
    }

    @Test
    void canonicalArmorReparsesToSameFingerprint() {
        PublicKeyInfo first = inspector.inspectPublicKey(Fixtures.text("alice.pub.asc"));
        PublicKeyInfo second = inspector.inspectPublicKey(first.armored());

        assertThat(second.fingerprint()).isEqualTo(first.fingerprint());
    }

    @Test
    void rejectsPrivateKeyBlock() {
        String privateBlock = "-----BEGIN PGP PRIVATE KEY BLOCK-----\n\nxVgEabc\n-----END PGP PRIVATE KEY BLOCK-----\n";

        assertThatThrownBy(() -> inspector.inspectPublicKey(privateBlock))
                .isInstanceOf(InvalidPgpDataException.class)
                .hasMessageContaining("PRIVATE key");
    }

    @Test
    void rejectsGarbage() {
        String garbage = "-----BEGIN PGP PUBLIC KEY BLOCK-----\n\nbm90IGEga2V5\n-----END PGP PUBLIC KEY BLOCK-----\n";

        assertThatThrownBy(() -> inspector.inspectPublicKey(garbage))
                .isInstanceOf(InvalidPgpDataException.class);
        assertThatThrownBy(() -> inspector.inspectPublicKey("hello"))
                .isInstanceOf(InvalidPgpDataException.class);
    }

    @Test
    void rejectsMultipleKeys() {
        String two = Fixtures.text("alice.pub.asc") + "\n" + Fixtures.text("bob.pub.asc");

        assertThatThrownBy(() -> inspector.inspectPublicKey(two))
                .isInstanceOf(InvalidPgpDataException.class);
    }

    @Test
    void rejectsKeyWithTamperedUserId() {
        // Flipping bytes inside the key body breaks the self-signature (or the packet structure).
        String armored = Fixtures.text("alice.pub.asc");
        String[] lines = armored.split("\n");
        lines[3] = new StringBuilder(lines[3]).reverse().toString();
        String tampered = String.join("\n", lines);

        assertThatThrownBy(() -> inspector.inspectPublicKey(tampered))
                .isInstanceOf(InvalidPgpDataException.class);
    }

    @Test
    void readsRecipientsOfArmoredMessage() {
        Set<Long> alice = inspector.inspectPublicKey(Fixtures.text("alice.pub.asc")).encryptionKeyIds();
        Set<Long> bob = inspector.inspectPublicKey(Fixtures.text("bob.pub.asc")).encryptionKeyIds();

        Set<Long> recipients = inspector.encryptedMessageRecipients(Fixtures.text("alice-to-bob.asc"));

        assertThat(recipients).containsAll(alice).containsAll(bob).hasSize(2);
    }

    @Test
    void readsRecipientsOfBinaryMessage() {
        Set<Long> bob = inspector.inspectPublicKey(Fixtures.text("bob.pub.asc")).encryptionKeyIds();

        Set<Long> recipients = inspector.encryptedMessageRecipients(
                new ByteArrayInputStream(Fixtures.bytes("alice-to-bob.bin")));

        assertThat(recipients).containsAll(bob);
    }

    @Test
    void rejectsPlaintextAndSignedOnlyMessages() {
        assertThatThrownBy(() -> inspector.encryptedMessageRecipients("hello in plaintext"))
                .isInstanceOf(InvalidPgpDataException.class);
        assertThatThrownBy(() -> inspector.encryptedMessageRecipients(Fixtures.text("signed-only.asc")))
                .isInstanceOf(InvalidPgpDataException.class);
        assertThatThrownBy(() -> inspector.encryptedMessageRecipients(
                new ByteArrayInputStream("plain bytes".getBytes())))
                .isInstanceOf(InvalidPgpDataException.class);
    }
}
