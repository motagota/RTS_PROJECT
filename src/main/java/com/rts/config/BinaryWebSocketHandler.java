package com.rts.config;

import com.rts.service.StreamingMapExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native WebSocket handler for binary map generation messages.
 * Replaces STOMP which doesn't properly support binary data.
 */
@Slf4j
@Component
public class BinaryWebSocketHandler extends AbstractWebSocketHandler {

    // Map of session ID to WebSocket session
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    private final StreamingMapExecutor streamingMapExecutor;

    // Use @Lazy to avoid circular dependency
    public BinaryWebSocketHandler(@Lazy StreamingMapExecutor streamingMapExecutor) {
        this.streamingMapExecutor = streamingMapExecutor;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = getSessionId(session);
        sessions.put(sessionId, session);
        log.info("WebSocket connection established: sessionId={}, remoteAddress={}",
                sessionId, session.getRemoteAddress());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = getSessionId(session);
        sessions.remove(sessionId);
        log.info("WebSocket connection closed: sessionId={}, status={}", sessionId, status);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        // Client shouldn't send binary messages to us, but handle if needed
        log.debug("Received binary message from client: {} bytes", message.getPayloadLength());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Handle control messages like "next-step" command
        String payload = message.getPayload();
        String sessionId = getSessionId(session);

        log.debug("Received text message from sessionId={}: {}", sessionId, payload);

        // Parse JSON and handle commands
        if (payload.contains("next-step")) {
            log.info("Received next-step command for session {}", sessionId);
            // Trigger the next step in the map executor
            streamingMapExecutor.nextStep(sessionId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session {}: {}",
                getSessionId(session), exception.getMessage(), exception);
    }

    /**
     * Send binary data to a specific session.
     */
    public void sendBinaryMessage(String sessionId, byte[] data) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                BinaryMessage message = new BinaryMessage(ByteBuffer.wrap(data));
                session.sendMessage(message);
                log.debug("Sent binary message to sessionId={}: {} bytes", sessionId, data.length);
            } catch (IOException e) {
                log.error("Failed to send binary message to sessionId={}: {}", sessionId, e.getMessage());
            }
        } else {
            log.warn("Cannot send message - session {} not found or closed", sessionId);
        }
    }

    /**
     * Extract session ID from WebSocket URI.
     * Expected format: /ws/map-generation/{sessionId}
     */
    private String getSessionId(WebSocketSession session) {
        String uri = session.getUri().toString();
        String[] parts = uri.split("/");
        return parts[parts.length - 1].split("\\?")[0]; // Remove query params if any
    }

    /**
     * Check if a session is connected.
     */
    public boolean isSessionConnected(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        return session != null && session.isOpen();
    }

    /**
     * Get all active session IDs.
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
}
