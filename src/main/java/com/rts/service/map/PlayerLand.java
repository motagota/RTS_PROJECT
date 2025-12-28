package com.rts.service.map;

/**
 * Represents a circular area around a player's starting position
 * Used for fair resource distribution and player-specific map generation
 */
public class PlayerLand {
    private int centerX;
    private int centerY;
    private int radius;
    private int playerNumber;

    public PlayerLand(int centerX, int centerY, int radius, int playerNumber) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.radius = radius;
        this.playerNumber = playerNumber;
    }

    /**
     * Check if a point is within this player's land
     */
    public boolean contains(int x, int y) {
        double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));
        return distance <= radius;
    }

    /**
     * Get a random point within this player's land
     */
    public int[] getRandomPoint(java.util.Random random) {
        // Generate random point in circle using polar coordinates
        double angle = random.nextDouble() * 2 * Math.PI;
        double r = Math.sqrt(random.nextDouble()) * radius; // sqrt for uniform distribution

        int x = (int) (centerX + r * Math.cos(angle));
        int y = (int) (centerY + r * Math.sin(angle));

        return new int[]{x, y};
    }

    /**
     * Get a random point within a specific radius from center
     */
    public int[] getRandomPointInRadius(java.util.Random random, int minRadius, int maxRadius) {
        double angle = random.nextDouble() * 2 * Math.PI;
        double r = minRadius + (Math.sqrt(random.nextDouble()) * (maxRadius - minRadius));

        int x = (int) (centerX + r * Math.cos(angle));
        int y = (int) (centerY + r * Math.sin(angle));

        return new int[]{x, y};
    }

    /**
     * Get distance from a point to the center of this player land
     */
    public double getDistanceFromCenter(int x, int y) {
        return Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));
    }

    // Getters
    public int getCenterX() {
        return centerX;
    }

    public int getCenterY() {
        return centerY;
    }

    public int getRadius() {
        return radius;
    }

    public int getPlayerNumber() {
        return playerNumber;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }
}
