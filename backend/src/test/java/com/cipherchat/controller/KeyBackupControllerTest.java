package com.cipherchat.controller;

import com.cipherchat.support.ApiClient;
import com.cipherchat.support.Fixtures;
import com.cipherchat.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.cipherchat.support.ApiClient.PASSWORD;
import static com.cipherchat.support.ApiClient.map;
import static com.cipherchat.support.ApiClient.uniqueName;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class KeyBackupControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mvc, objectMapper);
    }

    @Test
    void registerWithBackupThenDownloadItOnANewDevice() throws Exception {
        String name = uniqueName("backup");
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("username", name, "password", PASSWORD,
                                "publicKey", Fixtures.text("dave.pub.asc"),
                                "keyBackup", Fixtures.text("dave.key-backup.asc")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasKeyBackup").value(true))
                .andReturn().getResponse().getContentAsString();
        String fingerprint = api.read(body).get("fingerprint").asText();

        // "New device": log in with the password only, then fetch the still-locked backup.
        String login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("username", name, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasKeyBackup").value(true))
                .andReturn().getResponse().getContentAsString();
        String token = api.read(login).get("token").asText();

        mvc.perform(get("/api/keys/me/backup").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fingerprint").value(fingerprint))
                .andExpect(jsonPath("$.keyBackup").value(Fixtures.text("dave.key-backup.asc")))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void unprotectedPrivateKeyIsRefused() throws Exception {
        String token = api.registerWithKey(uniqueName("plain"), "dave.pub.asc");

        mvc.perform(put("/api/keys/me/backup")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("keyBackup", Fixtures.text("dave.unprotected.asc")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("Key backup must be locked with a passphrase")));

        mvc.perform(get("/api/keys/me/backup").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void backupMustMatchTheAccountKey() throws Exception {
        String token = api.registerWithKey(uniqueName("mismatch"), "alice.pub.asc");

        mvc.perform(put("/api/keys/me/backup")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("keyBackup", Fixtures.text("dave.key-backup.asc")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Key backup does not match your public key"));
    }

    @Test
    void backupNeedsAPublicKeyAndRejectsGarbage() throws Exception {
        String token = api.register(uniqueName("nokey"));

        mvc.perform(put("/api/keys/me/backup")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("keyBackup", Fixtures.text("dave.key-backup.asc")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Upload your public key before its backup"));

        String withKey = api.registerWithKey(uniqueName("garbage"), "dave.pub.asc");
        mvc.perform(put("/api/keys/me/backup")
                        .header("Authorization", "Bearer " + withKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("keyBackup", Fixtures.text("dave.pub.asc")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", matchesPattern("Expected an ASCII-armored OpenPGP private key.*")));
    }

    @Test
    void replacingThePublicKeyDropsTheOldBackup() throws Exception {
        String token = api.registerWithKey(uniqueName("rotate"), "dave.pub.asc");
        mvc.perform(put("/api/keys/me/backup")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("keyBackup", Fixtures.text("dave.key-backup.asc")))))
                .andExpect(status().isOk());

        mvc.perform(put("/api/keys/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("publicKey", Fixtures.text("alice.pub.asc")))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/keys/me/backup").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No key backup is stored for this account"));
    }

    @Test
    void backupRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/keys/me/backup")).andExpect(status().isUnauthorized());
    }
}
