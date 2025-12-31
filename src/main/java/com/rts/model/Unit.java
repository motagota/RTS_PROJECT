package com.rts.model;

/**
 * Represents a unit (like villagers, soldiers) on the map
 */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class Unit {

    public enum UnitType {
        VILLAGER('V', 1, 1, 50, 0, 0, 0),  // Symbol, width, height, food, wood, stone, gold
        SOLDIER('S', 1, 1, 60, 20, 0, 0);

        private final char symbol;
        private final int width;
        private final int height;
        private final int foodCost;
        private final int woodCost;
        private final int stoneCost;
        private final int goldCost;

        UnitType(char symbol, int width, int height, int foodCost, int woodCost, int stoneCost, int goldCost) {
            this.symbol = symbol;
            this.width = width;
            this.height = height;
            this.foodCost = foodCost;
            this.woodCost = woodCost;
            this.stoneCost = stoneCost;
            this.goldCost = goldCost;
        }

        public char getSymbol() {
            return symbol;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getFoodCost() {
            return foodCost;
        }

        public int getWoodCost() {
            return woodCost;
        }

        public int getStoneCost() {
            return stoneCost;
        }

        public int getGoldCost() {
            return goldCost;
        }
    }

    private static int nextId = 1;  // Static counter for generating unique IDs

    private int id;  // Unique identifier for this unit
    private int x;
    private int y;
    private UnitType type;
    private int playerNumber;
    private int health;
    private int maxHealth;

    // Movement-related fields
    private Integer targetX;  // Destination X coordinate
    private Integer targetY;  // Destination Y coordinate

    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient java.util.List<PathNode> path;  // Pathfinding path to follow (not serialized)

    private double movementSpeed = 0.1;  // Tiles per game tick (10 ticks/sec = 1 tile/sec)

    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient double movementProgress = 0.0;  // Accumulated movement progress towards next tile

    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient int blockedTicks = 0;  // Number of ticks unit has been blocked by another unit

    // Resource gathering fields
    private Long targetResourceNodeId;  // Resource node being gathered from
    private String carryingResourceType;  // Type of resource being carried (GOLD, STONE, BERRIES, TREE)
    private int carryingAmount = 0;  // Amount of resource being carried
    private int carryCapacity = 10;  // Maximum amount that can be carried
    private GatherState gatherState = GatherState.IDLE;  // Current gathering state

    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient int gatherTickCounter = 0;  // Counts ticks for gathering timing

    // Gather slot assignment (local offset from resource node)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient Integer assignedSlotIndex;  // Index of the slot assigned to this unit

    // Staggered movement delay for squad coordination
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient int movementDelayTicks = 0;  // Delay before starting movement (in ticks)

    public enum GatherState {
        IDLE,              // Not gathering
        MOVING_TO_RESOURCE, // Moving to resource node
        GATHERING,         // At resource node, gathering
        MOVING_TO_DROPOFF, // Carrying resources, moving to drop-off
        DEPOSITING         // At drop-off, depositing resources
    }

    // No-arg constructor for Jackson deserialization
    public Unit() {
        this.id = nextId++;
    }

    public Unit(int x, int y, UnitType type, int playerNumber) {
        this.id = nextId++;
        this.x = x;
        this.y = y;
        this.type = type;
        this.playerNumber = playerNumber;

        // Set default health based on unit type
        switch (type) {
            case VILLAGER -> {
                this.maxHealth = 50;
                this.health = 50;
            }
            case SOLDIER -> {
                this.maxHealth = 100;
                this.health = 100;
            }
        }
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public UnitType getType() {
        return type;
    }

    public void setType(UnitType type) {
        this.type = type;
    }

    public int getPlayerNumber() {
        return playerNumber;
    }

    public void setPlayerNumber(int playerNumber) {
        this.playerNumber = playerNumber;
    }

    public int getHealth() {
        return health;
    }

    public void setHealth(int health) {
        this.health = Math.max(0, Math.min(health, maxHealth));
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(int maxHealth) {
        this.maxHealth = maxHealth;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public int getWidth() {
        return type.getWidth();
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public int getHeight() {
        return type.getHeight();
    }

    public Integer getTargetX() {
        return targetX;
    }

    public void setTargetX(Integer targetX) {
        this.targetX = targetX;
    }

    public Integer getTargetY() {
        return targetY;
    }

    public void setTargetY(Integer targetY) {
        this.targetY = targetY;
    }

    public java.util.List<PathNode> getPath() {
        return path;
    }

    public void setPath(java.util.List<PathNode> path) {
        this.path = path;
    }

    public double getMovementSpeed() {
        return movementSpeed;
    }

    public void setMovementSpeed(double movementSpeed) {
        this.movementSpeed = movementSpeed;
    }

    public boolean isMoving() {
        return targetX != null && targetY != null && (x != targetX || y != targetY);
    }

    public Long getTargetResourceNodeId() {
        return targetResourceNodeId;
    }

    public void setTargetResourceNodeId(Long targetResourceNodeId) {
        this.targetResourceNodeId = targetResourceNodeId;
    }

    public String getCarryingResourceType() {
        return carryingResourceType;
    }

    public void setCarryingResourceType(String carryingResourceType) {
        this.carryingResourceType = carryingResourceType;
    }

    public int getCarryingAmount() {
        return carryingAmount;
    }

    public void setCarryingAmount(int carryingAmount) {
        this.carryingAmount = carryingAmount;
    }

    public int getCarryCapacity() {
        return carryCapacity;
    }

    public void setCarryCapacity(int carryCapacity) {
        this.carryCapacity = carryCapacity;
    }

    public GatherState getGatherState() {
        return gatherState;
    }

    public void setGatherState(GatherState gatherState) {
        this.gatherState = gatherState;
    }

    public boolean isGathering() {
        return gatherState != GatherState.IDLE;
    }

    public boolean isCarryingResources() {
        return carryingAmount > 0;
    }

    public double getMovementProgress() {
        return movementProgress;
    }

    public void setMovementProgress(double movementProgress) {
        this.movementProgress = movementProgress;
    }

    public int getGatherTickCounter() {
        return gatherTickCounter;
    }

    public void setGatherTickCounter(int gatherTickCounter) {
        this.gatherTickCounter = gatherTickCounter;
    }

    public int getBlockedTicks() {
        return blockedTicks;
    }

    public void setBlockedTicks(int blockedTicks) {
        this.blockedTicks = blockedTicks;
    }

    public Integer getAssignedSlotIndex() {
        return assignedSlotIndex;
    }

    public void setAssignedSlotIndex(Integer assignedSlotIndex) {
        this.assignedSlotIndex = assignedSlotIndex;
    }

    public int getMovementDelayTicks() {
        return movementDelayTicks;
    }

    public void setMovementDelayTicks(int movementDelayTicks) {
        this.movementDelayTicks = movementDelayTicks;
    }
}
