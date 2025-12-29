package com.rts.model;

/**
 * Represents a node in a pathfinding path
 */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class PathNode {
    private int x;
    private int y;

    // For A* algorithm
    private double gCost;  // Distance from start
    private double hCost;  // Heuristic distance to end
    private PathNode parent;

    public PathNode() {
    }

    public PathNode(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public double getFCost() {
        return gCost + hCost;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public double getGCost() {
        return gCost;
    }

    public void setGCost(double gCost) {
        this.gCost = gCost;
    }

    public double getHCost() {
        return hCost;
    }

    public void setHCost(double hCost) {
        this.hCost = hCost;
    }

    public PathNode getParent() {
        return parent;
    }

    public void setParent(PathNode parent) {
        this.parent = parent;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PathNode pathNode = (PathNode) obj;
        return x == pathNode.x && y == pathNode.y;
    }

    @Override
    public int hashCode() {
        return 31 * x + y;
    }
}
