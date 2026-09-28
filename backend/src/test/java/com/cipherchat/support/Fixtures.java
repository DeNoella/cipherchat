package com.cipherchat.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** OpenPGP.js-generated fixtures (see src/test/resources/pgp/generate-fixtures.mjs). */
public final class Fixtures {

    private Fixtures() {
    }

    public static String text(String name) {
        return new String(bytes(name), StandardCharsets.US_ASCII);
    }

    public static byte[] bytes(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/pgp/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing fixture " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
