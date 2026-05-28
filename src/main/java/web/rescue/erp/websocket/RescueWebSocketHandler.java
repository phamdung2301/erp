package web.rescue.erp.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import web.rescue.erp.entity.ChatMessage;
import web.rescue.erp.entity.RescueRequest;
import web.rescue.erp.repository.ChatMessageRepository;
import web.rescue.erp.repository.RescueRequestRepository;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class RescueWebSocketHandler extends TextWebSocketHandler {

    private final RescueRequestRepository rescueRequestRepository;
    private final ChatMessageRepository chatMessageRepository;

    // Map to keep track of active sessions by user's phone number
    private static final Map<String, WebSocketSession> sessionsByPhone = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Principal principal = session.getPrincipal();
        if (principal != null) {
            String phone = principal.getName();
            // Close old session if exists to prevent leaks
            WebSocketSession oldSession = sessionsByPhone.put(phone, session);
            if (oldSession != null && oldSession.isOpen()) {
                try {
                    oldSession.close();
                } catch (IOException e) {
                    log.error("Error closing old session for user {}", phone, e);
                }
            }
            log.info("WebSocket connection established for user: {}", phone);
        } else {
            log.warn("WebSocket connection attempted without principal");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Principal principal = session.getPrincipal();
        if (principal != null) {
            String phone = principal.getName();
            sessionsByPhone.remove(phone, session);
            log.info("WebSocket connection closed for user: {}", phone);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        Principal principal = session.getPrincipal();
        String senderPhone = principal != null ? principal.getName() : null;

        log.debug("Received message from user {}: {}", senderPhone != null ? senderPhone : "unknown", payload);

        if (senderPhone == null) return;

        String type = extractJsonValue(payload, "type");
        if ("CHAT_MESSAGE".equals(type)) {
            String requestIdStr = extractJsonValue(payload, "requestId");
            String messageContent = extractJsonValue(payload, "messageContent");

            if (requestIdStr != null && messageContent != null) {
                try {
                    UUID requestId = UUID.fromString(requestIdStr);
                    RescueRequest request = rescueRequestRepository.findById(requestId).orElse(null);
                    if (request != null) {
                        // Save message
                        ChatMessage chatMsg = ChatMessage.builder()
                                .rescueRequest(request)
                                .senderPhone(senderPhone)
                                .messageContent(messageContent)
                                .build();
                        chatMessageRepository.save(chatMsg);

                        // Format json response
                        String escapedContent = messageContent.replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n")
                                .replace("\r", "\\r");
                        String chatResponseJson = String.format(
                                "{\"type\":\"CHAT_MESSAGE\",\"requestId\":\"%s\",\"senderPhone\":\"%s\",\"messageContent\":\"%s\",\"createdAt\":\"%s\"}",
                                requestIdStr, senderPhone, escapedContent, java.time.LocalDateTime.now().toString()
                        );

                        // Broadcast to both Customer and Rescuer if they are online
                        if (request.getCustomer() != null) {
                            sendMessageToUser(request.getCustomer().getPhone(), chatResponseJson);
                        }
                        if (request.getRescuer() != null) {
                            sendMessageToUser(request.getRescuer().getPhone(), chatResponseJson);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error processing CHAT_MESSAGE websocket event", e);
                }
            }
        }
    }

    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"[\\s]*:[\\s]*\"([^\"]*)\"";
        java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = r.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * Send message to a specific user by phone
     */
    public boolean sendMessageToUser(String phone, String jsonMessage) {
        WebSocketSession session = sessionsByPhone.get(phone);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(jsonMessage));
                return true;
            } catch (IOException e) {
                log.error("Failed to send message to user {}", phone, e);
            }
        }
        return false;
    }

    /**
     * Send message to all users with a specific role
     */
    public void sendMessageToRole(String roleName, String jsonMessage) {
        sessionsByPhone.forEach((phone, session) -> {
            if (session.isOpen()) {
                Principal principal = session.getPrincipal();
                if (principal instanceof Authentication auth) {
                    boolean hasRole = auth.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_" + roleName) || a.getAuthority().equals(roleName));
                    if (hasRole) {
                        try {
                            session.sendMessage(new TextMessage(jsonMessage));
                        } catch (IOException e) {
                            log.error("Failed to send message to role {} (user {})", roleName, phone, e);
                        }
                    }
                }
            }
        });
    }
}
