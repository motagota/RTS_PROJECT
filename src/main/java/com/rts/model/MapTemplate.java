package com.rts.model;

import jakarta.persistence.*;

@Entity
@Table(name = "map_templates")
public class MapTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String displayName;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private Integer minPlayers = 2;

    @Column(nullable = false)
    private Integer maxPlayers = 8;

    @Column(nullable = false)
    private String mapSize; // SMALL, MEDIUM, LARGE, HUGE

    @Column(nullable = false)
    private String baseTerrain; // GRASS, DESERT, SNOW, LAVA

    @Column(nullable = false)
    private Integer edgeDistanceMin = 10; // Min tiles from edge for player starts

    @Column(nullable = false)
    private Integer startingAreaRadius = 5; // Radius of cleared/flattened starting area

    @Column(nullable = false)
    private Integer playerLandRadius = 20; // Radius of player land zone for resource distribution

    @Column(length = 5000)
    private String rmsCommands; // JSON array of RMS commands (DEPRECATED - use rmsScript)

    @Column(length = 10000)
    private String rmsScript; // RMS script content

    @Column(nullable = false)
    private Boolean isDefault = false;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getMinPlayers() {
        return minPlayers;
    }

    public void setMinPlayers(Integer minPlayers) {
        this.minPlayers = minPlayers;
    }

    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public String getMapSize() {
        return mapSize;
    }

    public void setMapSize(String mapSize) {
        this.mapSize = mapSize;
    }

    public String getBaseTerrain() {
        return baseTerrain;
    }

    public void setBaseTerrain(String baseTerrain) {
        this.baseTerrain = baseTerrain;
    }

    public Integer getEdgeDistanceMin() {
        return edgeDistanceMin;
    }

    public void setEdgeDistanceMin(Integer edgeDistanceMin) {
        this.edgeDistanceMin = edgeDistanceMin;
    }

    public Integer getStartingAreaRadius() {
        return startingAreaRadius;
    }

    public void setStartingAreaRadius(Integer startingAreaRadius) {
        this.startingAreaRadius = startingAreaRadius;
    }

    public String getRmsCommands() {
        return rmsCommands;
    }

    public void setRmsCommands(String rmsCommands) {
        this.rmsCommands = rmsCommands;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public Integer getPlayerLandRadius() {
        return playerLandRadius;
    }

    public void setPlayerLandRadius(Integer playerLandRadius) {
        this.playerLandRadius = playerLandRadius;
    }

    public String getRmsScript() {
        return rmsScript;
    }

    public void setRmsScript(String rmsScript) {
        this.rmsScript = rmsScript;
    }
}
