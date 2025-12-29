package com.rts.dto;

import jakarta.validation.constraints.NotNull;

public class SetRallyPointRequest {
    @NotNull(message = "Rally X coordinate is required")
    private Integer rallyX;

    @NotNull(message = "Rally Y coordinate is required")
    private Integer rallyY;

    public SetRallyPointRequest() {
    }

    public SetRallyPointRequest(Integer rallyX, Integer rallyY) {
        this.rallyX = rallyX;
        this.rallyY = rallyY;
    }

    public Integer getRallyX() {
        return rallyX;
    }

    public void setRallyX(Integer rallyX) {
        this.rallyX = rallyX;
    }

    public Integer getRallyY() {
        return rallyY;
    }

    public void setRallyY(Integer rallyY) {
        this.rallyY = rallyY;
    }
}
