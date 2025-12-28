package com.rts.model;

public class ChatMessage {
    private String sender;
    private String content;
    private MessageType type;
    private Long lobbyId;

    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE,
        SYSTEM
    }

    public ChatMessage() {
    }

    public ChatMessage(String sender, String content, MessageType type, Long lobbyId) {
        this.sender = sender;
        this.content = content;
        this.type = type;
        this.lobbyId = lobbyId;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public Long getLobbyId() {
        return lobbyId;
    }

    public void setLobbyId(Long lobbyId) {
        this.lobbyId = lobbyId;
    }
}
