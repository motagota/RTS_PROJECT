package com.rts.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a resource node on the map (gold mine, stone deposit, berry bush, tree)
 * Each resource node has an amount that depletes as units gather from it
 */
@Entity
@Data
@NoArgsConstructor
@Table(name = "resource_nodes")
public class ResourceNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The game this resource node belongs to
     */
    @ManyToOne
    @JoinColumn(name = "game_id", nullable = false)
    @JsonIgnore
    private Game game;

    /**
     * X coordinate on the map
     */
    @Column(nullable = false)
    private int x;

    /**
     * Y coordinate on the map
     */
    @Column(nullable = false)
    private int y;

    /**
     * Type of resource: GOLD, STONE, BERRIES, TREE
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ResourceType type;

    /**
     * Current amount of resources remaining
     */
    @Column(nullable = false)
    private int amount;

    /**
     * Maximum/initial amount of resources
     */
    @Column(nullable = false)
    private int maxAmount;

    /**
     * Whether this resource node has been depleted
     */
    @Column(nullable = false)
    private boolean depleted = false;

    public enum ResourceType {
        GOLD(800),      // Gold mines start with 800 gold
        STONE(350),     // Stone deposits start with 350 stone
        BERRIES(125),   // Berry bushes start with 125 food (per bush, typically 6 bushes)
        TREE(100),      // Trees start with 100 wood
        FORAGE(125);    // Alias for berries

        private final int defaultAmount;

        ResourceType(int defaultAmount) {
            this.defaultAmount = defaultAmount;
        }

        public int getDefaultAmount() {
            return defaultAmount;
        }
    }

    /**
     * Constructor with game, position, and type
     */
    public ResourceNode(Game game, int x, int y, ResourceType type) {
        this.game = game;
        this.x = x;
        this.y = y;
        this.type = type;
        this.maxAmount = type.getDefaultAmount();
        this.amount = this.maxAmount;
        this.depleted = false;
    }

    /**
     * Constructor with custom amount
     */
    public ResourceNode(Game game, int x, int y, ResourceType type, int amount) {
        this.game = game;
        this.x = x;
        this.y = y;
        this.type = type;
        this.maxAmount = amount;
        this.amount = amount;
        this.depleted = false;
    }

    /**
     * Gather resources from this node
     * @param gatherAmount Amount to gather
     * @return Amount actually gathered (may be less if node doesn't have enough)
     */
    public int gather(int gatherAmount) {
        if (depleted || amount <= 0) {
            return 0;
        }

        int actuallyGathered = Math.min(gatherAmount, amount);
        amount -= actuallyGathered;

        if (amount <= 0) {
            amount = 0;
            depleted = true;
        }

        return actuallyGathered;
    }

    /**
     * Check if resource node is empty
     */
    public boolean isEmpty() {
        return depleted || amount <= 0;
    }

    /**
     * Get percentage of resources remaining
     */
    public int getPercentageRemaining() {
        if (maxAmount == 0) return 0;
        return (int) ((amount * 100.0) / maxAmount);
    }

    /**
     * Get the resource type string for map object compatibility
     */
    public String getObjectType() {
        return type.name();
    }

    /**
     * Create from object type string
     */
    public static ResourceType fromObjectType(String objectType) {
        return switch (objectType.toUpperCase()) {
            case "GOLD" -> ResourceType.GOLD;
            case "STONE" -> ResourceType.STONE;
            case "BERRIES", "FORAGE", "BERRY_BUSH" -> ResourceType.BERRIES;
            case "TREE" -> ResourceType.TREE;
            default -> throw new IllegalArgumentException("Unknown resource type: " + objectType);
        };
    }
}
