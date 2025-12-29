package com.rts.dto;

import jakarta.validation.constraints.NotBlank;

public class HeartbeatRequest {
    @NotBlank(message = "Player name is required")
    private String playerName;

    public HeartbeatRequest() {
    }

    public HeartbeatRequest(String playerName) {
        this.playerName = playerName;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
}
