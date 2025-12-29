package com.rts.dto;

import jakarta.validation.constraints.NotBlank;

public class PlayerStatusRequest {
    @NotBlank(message = "Status is required")
    private String status;

    public PlayerStatusRequest() {
    }

    public PlayerStatusRequest(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
