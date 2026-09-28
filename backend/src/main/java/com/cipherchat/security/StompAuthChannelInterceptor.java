package com.cipherchat.security;

import com.cipherchat.repository.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Authenticates the STOMP session and authorises every inbound frame.
 * <ul>
 *   <li>CONNECT must carry {@code Authorization: Bearer <jwt>} (browsers cannot set headers on the
 *       WebSocket upgrade itself, and query-string tokens would end up in access logs).</li>
 *   <li>SUBSCRIBE is limited to the caller's own queues.</li>
 *   <li>SEND is refused: messages go through REST so they are validated and persisted.</li>
 * </ul>
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    public static final String MESSAGES_QUEUE = "/queue/messages";
    private static final Set<String> ALLOWED_SUBSCRIPTIONS = Set.of("/user" + MESSAGES_QUEUE, "/user/queue/errors");
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository users;

    public StompAuthChannelInterceptor(JwtService jwtService, UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT, STOMP -> {
                String header = accessor.getFirstNativeHeader("Authorization");
                AuthUser user = header != null && header.startsWith(BEARER)
                        ? jwtService.verify(header.substring(BEARER.length()).trim())
                        .filter(u -> users.existsById(u.id()))
                        .orElse(null)
                        : null;
                if (user == null) {
                    throw new MessageDeliveryException("Authentication required");
                }
                accessor.setUser(JwtAuthenticationFilter.toAuthentication(user));
            }
            case SUBSCRIBE -> {
                requireSession(accessor);
                if (!ALLOWED_SUBSCRIPTIONS.contains(accessor.getDestination())) {
                    throw new MessageDeliveryException("Subscription not allowed");
                }
            }
            case SEND -> throw new MessageDeliveryException("Send messages via POST /api/messages");
            default -> {
                // DISCONNECT, UNSUBSCRIBE, ACK, NACK... are harmless.
            }
        }
        return message;
    }

    private static void requireSession(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new MessageDeliveryException("Authentication required");
        }
    }
}
