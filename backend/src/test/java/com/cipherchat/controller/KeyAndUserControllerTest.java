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

import static com.cipherchat.support.ApiClient.map;
import static com.cipherchat.support.ApiClient.uniqueName;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class KeyAndUserControllerTest {

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
    void uploadThenFetchPublicKey() throws Exception {
        String name = uniqueName("keyup");
        String token = api.register(name);

        mvc.perform(put("/api/keys/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("publicKey", Fixtures.text("bob.pub.asc")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name))
                .andExpect(jsonPath("$.fingerprint", matchesPattern("[0-9A-F]{40}")))
                .andExpect(jsonPath("$.algorithm").value("EdDSA/Ed25519 + ECDH/Curve25519"));

        mvc.perform(get("/api/keys/" + name).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey", startsWith("-----BEGIN PGP PUBLIC KEY BLOCK-----")));
    }

    @Test
    void uploadRejectsPrivateKeyAndGarbage() throws Exception {
        String token = api.register(uniqueName("keybad"));

        mvc.perform(put("/api/keys/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("publicKey",
                                "-----BEGIN PGP PRIVATE KEY BLOCK-----\n\nabc\n-----END PGP PRIVATE KEY BLOCK-----"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("That is a PRIVATE key")));

        mvc.perform(put("/api/keys/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("publicKey", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.publicKey").exists());
    }

    @Test
    void uploadRequiresAuthentication() throws Exception {
        mvc.perform(put("/api/keys/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("publicKey", Fixtures.text("bob.pub.asc")))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fetchKeyOfUnknownUserOrUserWithoutKeyIs404() throws Exception {
        String keyless = uniqueName("nokey");
        String token = api.register(keyless);

        mvc.perform(get("/api/keys/" + uniqueName("ghost")).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
        mvc.perform(get("/api/keys/" + keyless).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User has not uploaded a public key yet"));
    }

    @Test
    void searchFindsByPrefixAndExcludesSelf() throws Exception {
        String prefix = "srch" + uniqueName("").substring(1, 6);
        String me = prefix + "_me";
        String token = api.register(me);
        api.registerWithKey(prefix + "_ann", "alice.pub.asc");
        api.register(prefix + "_ben");

        mvc.perform(get("/api/users").param("query", prefix).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].username").value(prefix + "_ann"))
                .andExpect(jsonPath("$[0].hasPublicKey").value(true))
                .andExpect(jsonPath("$[1].hasPublicKey").value(false));
    }

    @Test
    void searchValidatesQuery() throws Exception {
        String token = api.register(uniqueName("srchv"));

        mvc.perform(get("/api/users").param("query", "a%").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing parameter 'query'"));
    }
}
