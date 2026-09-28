package com.cipherchat.controller;

import com.cipherchat.dto.MessageResponse;
import com.cipherchat.support.ApiClient;
import com.cipherchat.support.Fixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.cipherchat.support.ApiClient.map;
import static com.cipherchat.support.ApiClient.uniqueName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebSocketIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    ApiClient api;
    WebSocketStompClient stomp;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mvc, objectMapper);
        stomp = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stomp.setMessageConverter(converter);
    }

    @AfterEach
    void tearDown() {
        stomp.stop();
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.setOrigin("http://localhost:3000");
        return stomp.connectAsync("ws://localhost:" + port + "/ws", handshake, connectHeaders,
                new StompSessionHandlerAdapter() {
                }).get(5, TimeUnit.SECONDS);
    }

    @Test
    void recipientReceivesCiphertextInRealTime() throws Exception {
        String alice = uniqueName("wsalice");
        String bob = uniqueName("wsbob");
        String aliceToken = api.registerWithKey(alice, "alice.pub.asc");
        String bobToken = api.registerWithKey(bob, "bob.pub.asc");

        BlockingQueue<MessageResponse> inbox = new LinkedBlockingQueue<>();
        StompSession session = connect(bobToken);
        session.subscribe("/user/queue/messages", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                inbox.add((MessageResponse) payload);
            }
        });
        Thread.sleep(300); // let the SUBSCRIBE frame register with the broker

        mvc.perform(post("/api/messages")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.toJson(map("recipientUsername", bob,
                                "ciphertext", Fixtures.text("alice-to-bob.asc")))))
                .andExpect(status().isCreated());

        MessageResponse received = inbox.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.sender()).isEqualTo(alice);
        assertThat(received.ciphertext()).startsWith("-----BEGIN PGP MESSAGE-----");
        session.disconnect();
    }

    @Test
    void connectWithoutTokenIsRejected() {
        assertThatThrownBy(() -> connect(null)).isInstanceOf(ExecutionException.class);
    }

    @Test
    void connectWithInvalidTokenIsRejected() {
        assertThatThrownBy(() -> connect("not-a-jwt")).isInstanceOf(ExecutionException.class);
    }
}
