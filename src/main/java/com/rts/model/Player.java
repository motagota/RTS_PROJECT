package com.rts.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Boolean isHost = false;

    @Column(nullable = false)
    private Boolean isReady = false;

    @Column(nullable = false)
    private Boolean isAI = false;

    @Column
    private String aiDifficulty; // EASY, MEDIUM, HARD

    @ManyToOne
    @JoinColumn(name = "lobby_id", nullable = false)
    @JsonBackReference
    private Lobby lobby;

    private Integer slotNumber;

    public Player() {
    }

    public Player(String name, Boolean isHost, Lobby lobby, Integer slotNumber) {
        this.name = name;
        this.isHost = isHost;
        this.lobby = lobby;
        this.slotNumber = slotNumber;
        this.isReady = false;
    }

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

    public Boolean getIsHost() {
        return isHost;
    }

    public void setIsHost(Boolean isHost) {
        this.isHost = isHost;
    }

    public Boolean getIsReady() {
        return isReady;
    }

    public void setIsReady(Boolean isReady) {
        this.isReady = isReady;
    }

    public Lobby getLobby() {
        return lobby;
    }

    public void setLobby(Lobby lobby) {
        this.lobby = lobby;
    }

    public Integer getSlotNumber() {
        return slotNumber;
    }

    public void setSlotNumber(Integer slotNumber) {
        this.slotNumber = slotNumber;
    }

    public Boolean getIsAI() {
        return isAI;
    }

    public void setIsAI(Boolean isAI) {
        this.isAI = isAI;
    }

    public String getAiDifficulty() {
        return aiDifficulty;
    }

    public void setAiDifficulty(String aiDifficulty) {
        this.aiDifficulty = aiDifficulty;
    }
}
