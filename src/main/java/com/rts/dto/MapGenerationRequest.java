package com.rts.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Request DTO for map generation
 * 
 * Example JSON:
 * {
 * "mapSize": 100,
 * "seed": 42
 * }
 */

@Data
public class MapGenerationRequest {

    /**
     * RMS ( Random Map Script) text to execute
     * Can be empty for simple streaming generation
     */
    private String rmsScript;

    /**
     * Map size ( width and height in tiles)
     * Must be between 32 and 256
     * Default is 100
     */
    @Min(value = 32 , message = "Map size must be at lest 32")
    @Max(value = 256, message = "Map size must not exceed 256")
    private int mapSize = 100;

    /**
     * Random seed for deterministic generation
     * Same seed + same script = same map
     * Default is 42
     */
    private long seed = 42L;

    /**
     * Update interval for streaming generation
     * Controls how often updates are sent to the client
     * Default is 10 (send update every 10 iterations)
     */
    private Integer updateInterval;

    /**
     * Enable step-by-step mode
     * If true, execution will pause after each RMS command
     * and wait for client to send 'next-step' command
     * Default is false
     */
    private Boolean stepByStep = false;

    /**
     * Number of players for map generation
     * Used to determine player start positions and land distribution
     * Default is 2
     */
    @Min(value = 2, message = "Player count must be at least 2")
    @Max(value = 8, message = "Player count must not exceed 8")
    private int playerCount = 2;
}
