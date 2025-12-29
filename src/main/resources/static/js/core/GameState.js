/**
 * GameState - Centralized state management
 * Single Responsibility: Manage game state and notify observers of changes
 * Follows Single Responsibility Principle - only manages state
 */
class GameState {
    constructor(eventBus) {
        this.eventBus = eventBus;

        // Game metadata
        this.gameId = null;
        this.playerName = null;
        this.currentGame = null;

        // Players
        this.players = [];
        this.disconnectedPlayers = [];

        // Selection state
        this.selectedUnits = [];
        this.selectedBuilding = null;
        this.hoveredBuilding = null;

        // Input state
        this.isDragging = false;
        this.dragStart = null;
        this.dragCurrent = null;
        this.dragSelectionOccurred = false;
        this.doubleClickHandled = false;

        // UI state
        this.settingRallyPoint = false;
        this.lastHoverRenderTime = 0;

        // Network state
        this.stompClient = null;

        // Intervals
        this.intervals = {
            heartbeat: null,
            loadingTick: null,
            queueUpdate: null,
            mapUpdate: null
        };

        // Renderer reference
        this.mapRenderer = null;
    }

    /**
     * Initialize game state with game ID and player name
     */
    init(gameId, playerName) {
        this.gameId = gameId;
        this.playerName = playerName;
        this.eventBus.emit('game:initialized', { gameId, playerName });
    }

    /**
     * Update current game data
     */
    setCurrentGame(game) {
        this.currentGame = game;
        this.eventBus.emit('game:updated', game);
    }

    /**
     * Update players list
     */
    setPlayers(players) {
        this.players = players;
        this.eventBus.emit('players:updated', players);
    }

    /**
     * Get current player
     */
    getCurrentPlayer() {
        return this.players.find(p => p.playerName === this.playerName);
    }

    /**
     * Update player status in list
     */
    updatePlayerStatus(playerName, status) {
        const player = this.players.find(p => p.playerName === playerName);
        if (player) {
            player.playerStatus = status;
            this.eventBus.emit('player:statusChanged', { playerName, status });
        }
    }

    /**
     * Set selected units
     */
    setSelectedUnits(units) {
        this.selectedUnits = units || [];
        this.eventBus.emit('selection:unitsChanged', this.selectedUnits);
    }

    /**
     * Get selected units
     */
    getSelectedUnits() {
        return this.selectedUnits;
    }

    /**
     * Set selected building
     */
    setSelectedBuilding(building) {
        this.selectedBuilding = building;
        this.eventBus.emit('selection:buildingChanged', building);
    }

    /**
     * Get selected building
     */
    getSelectedBuilding() {
        return this.selectedBuilding;
    }

    /**
     * Set hovered building
     */
    setHoveredBuilding(building) {
        if (this.hoveredBuilding !== building) {
            this.hoveredBuilding = building;
            this.eventBus.emit('hover:buildingChanged', building);
        }
    }

    /**
     * Get hovered building
     */
    getHoveredBuilding() {
        return this.hoveredBuilding;
    }

    /**
     * Set drag state
     */
    setDragState(isDragging, start = null, current = null) {
        const wasDragging = this.isDragging;

        this.isDragging = isDragging;
        this.dragStart = start;
        this.dragCurrent = current;

        if (isDragging) {
            this.eventBus.emit('drag:started', { start });
        } else if (wasDragging) {
            // Emit drag:ended whenever we stop dragging
            this.eventBus.emit('drag:ended');
        }
    }

    /**
     * Update drag current position
     */
    updateDragCurrent(current) {
        this.dragCurrent = current;
        this.eventBus.emit('drag:updated', { start: this.dragStart, current });
    }

    /**
     * Set rally point mode
     */
    setRallyPointMode(enabled) {
        this.settingRallyPoint = enabled;
        this.eventBus.emit('rallyPoint:modeChanged', enabled);
    }

    /**
     * Set map renderer reference
     */
    setMapRenderer(renderer) {
        this.mapRenderer = renderer;
    }

    /**
     * Get map renderer
     */
    getMapRenderer() {
        return this.mapRenderer;
    }

    /**
     * Set STOMP client
     */
    setStompClient(client) {
        this.stompClient = client;
    }

    /**
     * Get STOMP client
     */
    getStompClient() {
        return this.stompClient;
    }

    /**
     * Set an interval
     */
    setInterval(name, intervalId) {
        this.intervals[name] = intervalId;
    }

    /**
     * Clear an interval
     */
    clearInterval(name) {
        if (this.intervals[name]) {
            clearInterval(this.intervals[name]);
            this.intervals[name] = null;
        }
    }

    /**
     * Clear all intervals
     */
    clearAllIntervals() {
        Object.keys(this.intervals).forEach(name => {
            this.clearInterval(name);
        });
    }

    /**
     * Cleanup - clear all state and intervals
     */
    cleanup() {
        this.clearAllIntervals();
        this.eventBus.clear();

        // Reset state
        this.selectedUnits = [];
        this.selectedBuilding = null;
        this.hoveredBuilding = null;
        this.isDragging = false;
        this.dragStart = null;
        this.dragCurrent = null;
    }
}
