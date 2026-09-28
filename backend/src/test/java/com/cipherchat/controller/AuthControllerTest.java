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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AuthControllerTest {

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
    void registerReturnsTokenAndNormalisesUsername() throws Exception {
        String name = uniqueName("Reg");

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("username", name, "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value(name.toLowerCase()))
                .andExpect(jsonPath("$.hasPublicKey").value(false));
    }

    @Test
    void registerWithPublicKeyStoresFingerprint() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("username", uniqueName("withkey"), "password", PASSWORD,
                                "publicKey", Fixtures.text("alice.pub.asc")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasPublicKey").value(true))
                .andExpect(jsonPath("$.fingerprint", matchesPattern("[0-9A-F]{40}")));
    }

    @Test
    void registerRejectsDuplicateUsernameCaseInsensitively() throws Exception {
        String name = uniqueName("dup");
        api.register(name);

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("username", name.toUpperCase(), "password", PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Username is already taken"));
    }

    @Test
    void registerValidatesInput() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("username", "a!", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void registerRejectsInvalidPublicKeyWithoutCreatingUser() throws Exception {
        String name = uniqueName("badkey");
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("username", name, "password", PASSWORD, "publicKey", "nope"))))
                .andExpect(status().isBadRequest());

        api.register(name); // name is still free
    }

    @Test
    void malformedJsonGivesCleanError() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void loginSucceedsWithCorrectPassword() throws Exception {
        String name = uniqueName("login");
        api.register(name);

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("username", name, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginFailureDoesNotRevealWhetherUserExists() throws Exception {
        String name = uniqueName("enum");
        api.register(name);

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("username", name, "password", "wrong password!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("username", uniqueName("ghost"), "password", "wrong password!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void loginIsRateLimitedPerUsername() throws Exception {
        String name = uniqueName("brute");
        api.register(name);
        String wrong = api.toJson(map("username", name, "password", "wrong password!"));

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized());
        }
        // Even the correct password is refused while locked out.
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("username", name, "password", PASSWORD))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void protectedEndpointsRequireValidToken() throws Exception {
        mvc.perform(get("/api/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
        mvc.perform(get("/api/conversations").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        mvc.perform(get("/api/conversations"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")));
    }

    @Test
    void corsAllowsOnlyConfiguredOrigin() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .options("/api/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .options("/api/auth/login")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
