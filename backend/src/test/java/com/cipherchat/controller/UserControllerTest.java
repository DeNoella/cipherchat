package com.cipherchat.controller;

import com.cipherchat.support.ApiClient;
import com.cipherchat.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static com.cipherchat.support.ApiClient.PASSWORD;
import static com.cipherchat.support.ApiClient.map;
import static com.cipherchat.support.ApiClient.uniqueName;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class UserControllerTest {

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
    void meReturnsOwnProfileWithoutSecrets() throws Exception {
        String name = uniqueName("me");
        String token = api.registerWithKey(name, "bob.pub.asc");

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.hasPublicKey").value(true))
                .andExpect(jsonPath("$.fingerprint", matchesPattern("[0-9A-F]{40}")))
                .andExpect(jsonPath("$.hasKeyBackup").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.keyBackup").doesNotExist());
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void getByUsernameReturnsSummaryOr404() throws Exception {
        String token = api.register(uniqueName("look"));
        String other = uniqueName("other");
        api.registerWithKey(other, "alice.pub.asc");

        mvc.perform(get("/api/users/" + other.toUpperCase()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(other))
                .andExpect(jsonPath("$.hasPublicKey").value(true));
        mvc.perform(get("/api/users/" + uniqueName("ghost")).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
        mvc.perform(get("/api/users/a-b").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePasswordThenLoginWithNewPasswordOnly() throws Exception {
        String name = uniqueName("pw");
        String token = api.register(name);
        String newPassword = "a brand new passphrase";

        mvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("currentPassword", PASSWORD, "newPassword", newPassword))))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(Map.of("username", name, "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(Map.of("username", name, "password", newPassword))))
                .andExpect(status().isOk());
    }

    @Test
    void changePasswordRejectsWrongCurrentSameOrShortNew() throws Exception {
        String token = api.register(uniqueName("pwbad"));

        mvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("currentPassword", "not my password", "newPassword", "whatever 12345"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
        mvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("currentPassword", PASSWORD, "newPassword", PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("New password must be different from the current one"));
        mvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("currentPassword", PASSWORD, "newPassword", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
    }
}
