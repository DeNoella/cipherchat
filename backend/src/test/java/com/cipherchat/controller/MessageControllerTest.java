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
import org.springframework.test.web.servlet.ResultActions;

import static com.cipherchat.support.ApiClient.map;
import static com.cipherchat.support.ApiClient.uniqueName;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uses OpenPGP.js fixtures: alice-to-bob.asc is encrypted to alice + bob and signed by alice. */
@IntegrationTest
class MessageControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    ApiClient api;
    String alice;
    String bob;
    String aliceToken;
    String bobToken;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, objectMapper);
        alice = uniqueName("alice");
        bob = uniqueName("bob");
        aliceToken = api.registerWithKey(alice, "alice.pub.asc");
        bobToken = api.registerWithKey(bob, "bob.pub.asc");
    }

    private ResultActions send(String token, String recipient, String ciphertext) throws Exception {
        return mvc.perform(post("/api/messages")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.toJson(map("recipientUsername", recipient, "ciphertext", ciphertext))));
    }

    @Test
    void sendStoresCiphertextAndBothParticipantsSeeIt() throws Exception {
        String ciphertext = Fixtures.text("alice-to-bob.asc");
        String body = send(aliceToken, bob, ciphertext)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sender").value(alice))
                .andExpect(jsonPath("$.ciphertext").value(ciphertext))
                .andReturn().getResponse().getContentAsString();
        long conversationId = api.read(body).get("conversationId").asLong();

        for (String token : new String[]{aliceToken, bobToken}) {
            mvc.perform(get("/api/conversations/" + conversationId + "/messages").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].ciphertext").value(ciphertext));
        }
        mvc.perform(get("/api/conversations").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].peer.username").value(alice))
                .andExpect(jsonPath("$[0].lastMessageAt").isNotEmpty());
    }

    @Test
    void rejectsPlaintext() throws Exception {
        send(aliceToken, bob, "hello bob, in the clear")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ciphertext must be an ASCII-armored OpenPGP message"));
    }

    @Test
    void rejectsSignedButUnencryptedMessage() throws Exception {
        send(aliceToken, bob, Fixtures.text("signed-only.asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCiphertextNotAddressedToRecipient() throws Exception {
        send(aliceToken, bob, Fixtures.text("alice-to-carol.asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Message is not encrypted to the recipient's current public key"));
    }

    @Test
    void rejectsWhenRecipientHasNoKey() throws Exception {
        String keyless = uniqueName("nokey");
        api.register(keyless);

        send(aliceToken, keyless, Fixtures.text("alice-to-bob.asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Recipient has not uploaded a public key yet"));
    }

    @Test
    void rejectsUnknownRecipientAndSelf() throws Exception {
        send(aliceToken, uniqueName("ghost"), Fixtures.text("alice-to-bob.asc"))
                .andExpect(status().isNotFound());
        send(aliceToken, alice, Fixtures.text("alice-to-bob.asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void outsidersCannotReadHistory() throws Exception {
        String body = send(aliceToken, bob, Fixtures.text("alice-to-bob.asc"))
                .andReturn().getResponse().getContentAsString();
        long conversationId = api.read(body).get("conversationId").asLong();
        String eveToken = api.register(uniqueName("eve"));

        mvc.perform(get("/api/conversations/" + conversationId + "/messages").header("Authorization", "Bearer " + eveToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void historyPagesBackwards() throws Exception {
        String ciphertext = Fixtures.text("alice-to-bob.asc");
        long conversationId = 0;
        long lastId = 0;
        for (int i = 0; i < 3; i++) {
            String body = send(aliceToken, bob, ciphertext).andReturn().getResponse().getContentAsString();
            conversationId = api.read(body).get("conversationId").asLong();
            lastId = api.read(body).get("id").asLong();
        }

        mvc.perform(get("/api/conversations/" + conversationId + "/messages")
                        .param("size", "2").header("Authorization", "Bearer " + bobToken))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].id").value(lastId));
        mvc.perform(get("/api/conversations/" + conversationId + "/messages")
                        .param("before", String.valueOf(lastId - 1)).header("Authorization", "Bearer " + bobToken))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void startConversationIsIdempotent() throws Exception {
        String first = mvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("username", bob))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("username", alice))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(api.read(first).get("id").asLong()))
                .andExpect(jsonPath("$.peer.username").value(alice));
    }
}
