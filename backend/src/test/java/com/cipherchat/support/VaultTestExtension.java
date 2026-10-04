package com.cipherchat.support;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.Container;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.vault.VaultContainer;

import java.nio.file.Path;

/**
 * Starts one real Vault (dev mode, in Docker) for the whole test run and configures it with the
 * project's own vault/init.sh, so tests prove the script, the policy and the AppRole login work.
 * The app then reads its JWT secret and database credentials from Vault KV, exactly as in production.
 *
 * <p>Runs before Spring starts, because Spring Cloud Vault reads its settings while the
 * application environment is prepared (too early for @DynamicPropertySource).
 */
public class VaultTestExtension implements BeforeAllCallback {

    public static final String IMAGE = "hashicorp/vault:2.1";
    public static final String ROOT_TOKEN = "test-root-token";
    public static final String ROLE_ID = "test-role-id";
    public static final String SECRET_ID = "test-secret-id";
    /** Seeded into KV; H2 creates the in-memory database with whatever credentials connect first. */
    public static final String DB_USERNAME = "vault_user";
    public static final String DB_PASSWORD = "vault-test-db-password";

    private static VaultContainer<?> vault;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        start();
    }

    public static synchronized VaultContainer<?> start() throws Exception {
        if (vault != null) {
            return vault;
        }
        VaultContainer<?> container = new VaultContainer<>(IMAGE)
                .withVaultToken(ROOT_TOKEN)
                .withCopyFileToContainer(MountableFile.forHostPath(Path.of("../vault/init.sh")), "/init.sh");
        container.start();
        Container.ExecResult init = container.execInContainer("sh", "-c", String.join(" ",
                "VAULT_ADDR=http://127.0.0.1:8200", "VAULT_TOKEN=" + ROOT_TOKEN,
                "DB_USERNAME=" + DB_USERNAME, "DB_PASSWORD=" + DB_PASSWORD,
                "VAULT_ROLE_ID=" + ROLE_ID, "VAULT_SECRET_ID=" + SECRET_ID,
                "sh /init.sh"));
        if (init.getExitCode() != 0) {
            throw new IllegalStateException("vault/init.sh failed:\n" + init.getStdout() + init.getStderr());
        }
        System.setProperty("spring.cloud.vault.uri", container.getHttpHostAddress());
        System.setProperty("spring.cloud.vault.app-role.role-id", ROLE_ID);
        System.setProperty("spring.cloud.vault.app-role.secret-id", SECRET_ID);
        vault = container; // Testcontainers' Ryuk stops it when the JVM exits
        return vault;
    }

    /** Runs a Vault CLI command inside the container with the given token. */
    public static Container.ExecResult vault(String token, String... args) throws Exception {
        String[] command = new String[args.length + 4];
        command[0] = "env";
        command[1] = "VAULT_ADDR=http://127.0.0.1:8200";
        command[2] = "VAULT_TOKEN=" + token;
        command[3] = "vault";
        System.arraycopy(args, 0, command, 4, args.length);
        return start().execInContainer(command);
    }
}
