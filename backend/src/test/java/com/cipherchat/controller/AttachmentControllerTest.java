package com.cipherchat.controller;

import com.cipherchat.support.ApiClient;
import com.cipherchat.support.Fixtures;
import com.cipherchat.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static com.cipherchat.support.ApiClient.map;
import static com.cipherchat.support.ApiClient.uniqueName;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AttachmentControllerTest {

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

    private ResultActions upload(String token, String recipient, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/attachments")
                .file(new MockMultipartFile("file", "blob.pgp", MediaType.APPLICATION_OCTET_STREAM_VALUE, bytes))
                .param("recipientUsername", recipient)
                .header("Authorization", "Bearer " + token));
    }

    @Test
    void uploadLinkAndDownloadEncryptedBlob() throws Exception {
        byte[] blob = Fixtures.bytes("alice-to-bob.bin");
        String body = upload(aliceToken, bob, blob)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sizeBytes").value(blob.length))
                .andReturn().getResponse().getContentAsString();
        String id = api.read(body).get("id").asText();

        mvc.perform(post("/api/messages")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("recipientUsername", bob,
                                "ciphertext", Fixtures.text("alice-to-bob.asc"), "attachmentId", id))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachmentId").value(id));

        mvc.perform(get("/api/attachments/" + id).header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(blob));
    }

    @Test
    void attachmentCannotBeLinkedTwice() throws Exception {
        String id = api.read(upload(aliceToken, bob, Fixtures.bytes("alice-to-bob.bin"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        String message = api.toJson(map("recipientUsername", bob,
                "ciphertext", Fixtures.text("alice-to-bob.asc"), "attachmentId", id));

        mvc.perform(post("/api/messages").header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON).content(message))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/messages").header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON).content(message))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnencryptedUpload() throws Exception {
        upload(aliceToken, bob, "%PDF-1.7 plaintext document".getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Content must be an OpenPGP encrypted message"));
    }

    @Test
    void rejectsEmptyAndOversizedUploads() throws Exception {
        upload(aliceToken, bob, new byte[0])
                .andExpect(status().isBadRequest());
        upload(aliceToken, bob, new byte[11_010_049])
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("Attachment exceeds the 10 MB limit"));
    }

    @Test
    void outsidersGet404() throws Exception {
        String id = api.read(upload(aliceToken, bob, Fixtures.bytes("alice-to-bob.bin"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        String eveToken = api.register(uniqueName("eve"));

        mvc.perform(get("/api/attachments/" + id).header("Authorization", "Bearer " + eveToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/attachments/" + UUID.randomUUID()).header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/attachments/not-a-uuid").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void messageCannotReferenceSomeoneElsesAttachment() throws Exception {
        String id = api.read(upload(aliceToken, bob, Fixtures.bytes("alice-to-bob.bin"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        String carol = uniqueName("carol");
        api.registerWithKey(carol, "carol.pub.asc");

        mvc.perform(post("/api/messages").header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("recipientUsername", carol,
                                "ciphertext", Fixtures.text("alice-to-carol.asc"), "attachmentId", id))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown attachment"));
    }
}
