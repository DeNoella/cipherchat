package com.cipherchat.service;

import com.cipherchat.security.StompAuthChannelInterceptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Pushes newly stored ciphertext to both participants once the transaction has committed. */
@Component
public class MessageRelay {

    private final SimpMessagingTemplate messaging;

    public MessageRelay(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(MessageService.MessageSentEvent event) {
        messaging.convertAndSendToUser(event.recipient(), StompAuthChannelInterceptor.MESSAGES_QUEUE, event.message());
        // Echo to the sender's other tabs/devices.
        messaging.convertAndSendToUser(event.sender(), StompAuthChannelInterceptor.MESSAGES_QUEUE, event.message());
    }
}
