package com.cipherchat.vault;

import com.cipherchat.model.User;
import com.cipherchat.repository.UserRepository;
import com.cipherchat.support.ApiClient;
import com.cipherchat.support.Fixtures;
import com.cipherchat.support.IntegrationTest;
import com.cipherchat.support.VaultTestExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.Container;

import java.util.stream.StreamSupport;

import static com.cipherchat.support.ApiClient.PASSWORD;
import static com.cipherchat.support.ApiClient.map;
import static com.cipherchat.support.ApiClient.uniqueName;
import static com.cipherchat.support.VaultTestExtension.ROLE_ID;
import static com.cipherchat.support.VaultTestExtension.ROOT_TOKEN;
import static com.cipherchat.support.VaultTestExtension.SECRET_ID;
import static com.cipherchat.support.VaultTestExtension.vault;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spring Cloud Vault (KV v2 secrets) and Transit envelope encryption against a real Vault. */
@IntegrationTest
class VaultIntegrationTest {

    private static final String TRANSIT_KEY = "cipherchat-key-backup";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ConfigurableEnvironment environment;

    @Autowired
    UserRepository users;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mvc, objectMapper);
    }

    @Test
    void jwtSecretAndDatabaseCredentialsComeFromVaultKv() throws Exception {
        String jwtInVault = vault(ROOT_TOKEN, "kv", "get", "-field=app.jwt.secret", "secret/cipherchat").getStdout();

        assertThat(environment.getProperty("app.jwt.secret")).isEqualTo(jwtInVault).hasSizeGreaterThanOrEqualTo(32);
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo(VaultTestExtension.DB_USERNAME);
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo(VaultTestExtension.DB_PASSWORD);
        PropertySource<?> source = StreamSupport.stream(environment.getPropertySources().spliterator(), false)
                .filter(s -> !s.getName().equals("configurationProperties")) // Spring's view over all sources
                .filter(s -> s.containsProperty("app.jwt.secret"))
                .findFirst().orElseThrow();
        assertThat(source.getName()).isEqualTo("secret/cipherchat");
        assertThat(source.getClass().getName()).startsWith("org.springframework.vault");
    }

    @Test
    void backupIsStoredAsTransitCiphertextAndReturnedPassphraseLocked() throws Exception {
        String name = registerWithBackup();
        User user = users.findByUsername(name).orElseThrow();

        // What the database holds: Vault ciphertext, not the OpenPGP block.
        assertThat(user.getKeyBackup()).startsWith("vault:v1:").doesNotContain("PGP");

        // What the owner gets back: the passphrase-locked key, exactly as uploaded.
        mvc.perform(get("/api/keys/me/backup").header("Authorization", "Bearer " + login(name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyBackup").value(Fixtures.text("dave.key-backup.asc")));
    }

    @Test
    void ciphertextIsBoundToItsUser() throws Exception {
        String owner = registerWithBackup();
        String other = registerWithBackup();
        User victim = users.findByUsername(other).orElseThrow();
        // Simulate someone with database access moving a backup onto another account.
        victim.setKeyBackup(users.findByUsername(owner).orElseThrow().getKeyBackup());
        users.saveAndFlush(victim);

        mvc.perform(get("/api/keys/me/backup").header("Authorization", "Bearer " + login(other)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Key backup storage is unavailable. Try again in a moment."));
    }

    @Test
    void rotatedTransitKeyStillOpensOldBackups() throws Exception {
        String before = registerWithBackup();
        assertThat(vault(ROOT_TOKEN, "write", "-f", "transit/keys/" + TRANSIT_KEY + "/rotate").getExitCode()).isZero();
        String after = registerWithBackup();

        assertThat(users.findByUsername(after).orElseThrow().getKeyBackup()).doesNotStartWith("vault:v1:");
        mvc.perform(get("/api/keys/me/backup").header("Authorization", "Bearer " + login(before)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyBackup").value(Fixtures.text("dave.key-backup.asc")));
    }

    @Test
    void backendPolicyAllowsOnlyItsOwnSecretAndTransitKey() throws Exception {
        String token = vault(ROOT_TOKEN, "write", "-field=token", "auth/approle/login",
                "role_id=" + ROLE_ID, "secret_id=" + SECRET_ID).getStdout().trim();

        assertThat(vault(token, "kv", "get", "secret/cipherchat").getExitCode()).isZero();
        assertDenied(vault(token, "kv", "put", "secret/cipherchat", "app.jwt.secret=stolen"));
        assertDenied(vault(token, "kv", "get", "secret/other-app"));
        assertDenied(vault(token, "read", "transit/keys/" + TRANSIT_KEY));
        assertDenied(vault(token, "write", "-f", "transit/keys/" + TRANSIT_KEY + "/rotate"));
        assertDenied(vault(token, "read", "transit/export/encryption-key/" + TRANSIT_KEY));
        // Even an admin cannot take the Transit key out of Vault: it was created non-exportable.
        assertThat(vault(ROOT_TOKEN, "read", "transit/export/encryption-key/" + TRANSIT_KEY).getExitCode())
                .isNotZero();
    }

    private static void assertDenied(Container.ExecResult result) {
        assertThat(result.getExitCode()).isNotZero();
        assertThat(result.getStderr()).contains("permission denied");
    }

    private String registerWithBackup() throws Exception {
        String name = uniqueName("vault");
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("username", name, "password", PASSWORD,
                                "publicKey", Fixtures.text("dave.pub.asc"),
                                "keyBackup", Fixtures.text("dave.key-backup.asc")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasKeyBackup").value(true));
        return name;
    }

    private String login(String name) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("username", name, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return api.read(body).get("token").asText();
    }
}
