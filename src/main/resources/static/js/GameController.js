/**
 * GameController - Main controller that wires all modules together
 * Single Responsibility: Orchestrate the application
 * Dependency Inversion: Depends on abstractions (EventBus, managers, etc.)
 *
 * This is the only class that knows about all the other classes
 * Everything else communicates through events
 */
class GameController {
    constructor(gameId, playerName) {
        // Core
        this.eventBus = new EventBus();
        this.gameState = new GameState(this.eventBus);
        this.gameState.init(gameId, playerName);

        // Network
        this.networkManager = new NetworkManager(
            this.gameState,
            this.eventBus,
            'http://localhost:8080/api/games',
            'http://localhost:8080/ws'
        );

        // Managers
        this.selectionManager = new SelectionManager(this.gameState, this.eventBus);
        this.inputHandler = null; // Created after canvas is ready

        // UI Components
        this.resourceDisplay = new ResourceDisplay(this.eventBus);
        this.unitInfoPanel = new UnitInfoPanel(this.gameState, this.eventBus);
        this.buildingInfoPanel = new BuildingInfoPanel(this.gameState, this.eventBus);
        this.productionPanel = new ProductionPanel(this.gameState, this.eventBus, this.networkManager);

        // Setup event handlers
        this.setupEventHandlers();
    }

    /**
     * Setup all event handlers
     */
    setupEventHandlers() {
        // Network events
        this.eventBus.on('network:playersDisconnected', () => this.loadPlayers());
        this.eventBus.on('network:playersBooted', () => this.loadPlayers());
        this.eventBus.on('network:gameStatus', (data) => this.handleGameStatus(data.status));
        this.eventBus.on('network:productionQueueUpdate', () => this.loadProductionQueue());
        this.eventBus.on('network:mapUpdate', () => this.reloadMap());
        this.eventBus.on('network:unitsUpdate', (units) => this.updateUnits(units));
        this.eventBus.on('network:playerStatus', (data) => {
            this.gameState.updatePlayerStatus(data.playerName, data.status);
        });

        // Input events
        this.eventBus.on('input:click', (data) => this.handleClick(data));
        this.eventBus.on('input:doubleClick', (data) => this.handleDoubleClick(data));
        this.eventBus.on('input:rightClick', (data) => this.handleRightClick(data));
        this.eventBus.on('input:hover', (data) => this.handleHover(data));
        this.eventBus.on('input:dragEnd', (data) => this.handleDragEnd(data));

        // Hotkey events
        this.eventBus.on('hotkey:selectTownCenter', () => this.selectionManager.selectTownCenter());
        this.eventBus.on('hotkey:toggleRallyPoint', () => this.toggleRallyPoint());

        // Selection events
        this.eventBus.on('selection:unitsChanged', () => this.render());
        this.eventBus.on('selection:buildingChanged', () => this.render());
        this.eventBus.on('drag:updated', (data) => this.updateDragVisual(data));
        this.eventBus.on('drag:ended', () => this.clearDragVisual());

        // Render events
        this.eventBus.on('render:requested', () => this.render());
    }

    /**
     * Initialize the game
     */
    async init() {
        try {
            // Load game data
            const game = await this.networkManager.loadGame();
            this.gameState.setCurrentGame(game);

            // Load players
            await this.loadPlayers();

            // Initialize map renderer
            const canvas = document.getElementById('gameCanvas');
            const mapRenderer = new MapRenderer(canvas);
            this.gameState.setMapRenderer(mapRenderer);

            // Load map data
            await mapRenderer.loadMap(this.gameState.gameId);

            // Create input handler now that canvas is ready
            this.inputHandler = new InputHandler(this.gameState, this.eventBus, canvas);

            // Connect WebSocket
            await this.networkManager.connectWebSocket();

            // Start heartbeat
            this.startHeartbeat();

            // Update player status
            await this.networkManager.updatePlayerStatus('READY');

            return true;
        } catch (error) {
            console.error('Error initializing game:', error);
            throw error;
        }
    }

    /**
     * Load players from server
     */
    async loadPlayers() {
        const players = await this.networkManager.loadPlayers();
        this.gameState.setPlayers(players);

        // Update resources for current player
        const currentPlayer = this.gameState.getCurrentPlayer();
        if (currentPlayer && currentPlayer.resources) {
            this.eventBus.emit('network:resourcesUpdate', currentPlayer.resources);
        }
    }

    /**
     * Load production queue
     */
    async loadProductionQueue() {
        const queue = await this.networkManager.loadProductionQueue();
        this.eventBus.emit('productionQueue:updated', queue);
    }

    /**
     * Handle game status change
     */
    handleGameStatus(status) {
        if (status === 'CANCELLED') {
            alert('Game has been cancelled due to player disconnection. Redirecting to lobby...');
            window.location.href = 'lobby.html';
        }
    }

    /**
     * Handle click event
     */
    handleClick(data) {
        const { tileX, tileY, shiftKey } = data;
        const mapRenderer = this.gameState.getMapRenderer();

        // Check if in rally point mode
        if (this.gameState.settingRallyPoint && this.gameState.getSelectedBuilding()) {
            this.setRallyPoint(tileX, tileY);
            return;
        }

        // Check if a unit was clicked first (units have priority)
        const clickedUnit = mapRenderer.getUnitAt(tileX, tileY);

        if (clickedUnit) {
            if (shiftKey && this.selectionManager.isOwnedUnit(clickedUnit)) {
                this.selectionManager.toggleUnitSelection(clickedUnit);
            } else {
                this.selectionManager.selectUnit(clickedUnit);
            }
        } else {
            // Check if a building was clicked
            const clickedBuilding = this.selectionManager.findBuildingAtPosition(tileX, tileY);

            if (clickedBuilding) {
                this.selectionManager.selectBuilding(clickedBuilding);
                this.selectionManager.deselectAllUnits();
            } else {
                // Clicked on empty space
                if (!shiftKey) {
                    this.selectionManager.deselectAllUnits();
                    this.selectionManager.deselectBuilding();
                }
            }
        }
    }

    /**
     * Handle double-click event
     */
    handleDoubleClick(data) {
        const { tileX, tileY } = data;
        const mapRenderer = this.gameState.getMapRenderer();
        const clickedUnit = mapRenderer.getUnitAt(tileX, tileY);

        if (clickedUnit && this.selectionManager.isOwnedUnit(clickedUnit)) {
            this.selectionManager.selectAllUnitsOfType(clickedUnit.type);
        }
    }

    /**
     * Handle right-click event
     */
    async handleRightClick(data) {
        const { tileX, tileY } = data;
        const selectedUnits = this.gameState.getSelectedUnits();
        const ownedUnits = this.selectionManager.filterOwnedUnits(selectedUnits);

        if (ownedUnits.length === 0) {
            return;
        }

        // Show click indicator
        const mapRenderer = this.gameState.getMapRenderer();
        mapRenderer.addClickIndicator(tileX, tileY);

        // Move units
        const unitIds = ownedUnits.map(u => u.id);
        try {
            await this.networkManager.moveUnits(unitIds, tileX, tileY);
        } catch (error) {
            console.error('Failed to move units:', error);
        }
    }

    /**
     * Handle hover event
     */
    handleHover(data) {
        const { tileX, tileY } = data;
        const mapRenderer = this.gameState.getMapRenderer();

        if (tileX === null || tileY === null) {
            mapRenderer.setHoveredUnit(null);
            this.gameState.setHoveredBuilding(null);
            this.render();
            return;
        }

        const unit = mapRenderer.getUnitAt(tileX, tileY);
        const building = this.selectionManager.findBuildingAtPosition(tileX, tileY);

        if (unit !== mapRenderer.hoveredUnit || building !== this.gameState.getHoveredBuilding()) {
            mapRenderer.setHoveredUnit(unit);
            this.gameState.setHoveredBuilding(building);
            this.render();
        }
    }

    /**
     * Handle drag end event
     */
    handleDragEnd(data) {
        const { minX, maxX, minY, maxY, shiftKey } = data;
        this.selectionManager.selectUnitsInRectangle(minX, minY, maxX, maxY, shiftKey);
    }

    /**
     * Update drag visual
     */
    updateDragVisual(data) {
        const mapRenderer = this.gameState.getMapRenderer();
        if (mapRenderer) {
            mapRenderer.setDragSelection(
                data.start.x,
                data.start.y,
                data.current.x,
                data.current.y
            );
        }
    }

    /**
     * Clear drag visual
     */
    clearDragVisual() {
        const mapRenderer = this.gameState.getMapRenderer();
        if (mapRenderer) {
            mapRenderer.clearDragSelection();
        }
    }

    /**
     * Toggle rally point mode
     */
    toggleRallyPoint() {
        const building = this.gameState.getSelectedBuilding();
        if (!building) return;

        const currentPlayer = this.gameState.getCurrentPlayer();
        if (!currentPlayer || building.playerNumber !== currentPlayer.playerSlot) {
            return;
        }

        const newState = !this.gameState.settingRallyPoint;
        this.gameState.setRallyPointMode(newState);

        const canvas = document.getElementById('gameCanvas');
        canvas.style.cursor = newState ? 'crosshair' : 'default';

        const rallyBtn = document.querySelector('.rally-point-btn');
        if (rallyBtn) {
            if (newState) {
                rallyBtn.classList.add('active');
            } else {
                rallyBtn.classList.remove('active');
            }
        }
    }

    /**
     * Set rally point
     */
    async setRallyPoint(tileX, tileY) {
        const building = this.gameState.getSelectedBuilding();
        if (!building) return;

        try {
            await this.networkManager.setRallyPoint(building.id, tileX, tileY);

            // Exit rally point mode
            this.gameState.setRallyPointMode(false);
            const canvas = document.getElementById('gameCanvas');
            canvas.style.cursor = 'default';

            const rallyBtn = document.querySelector('.rally-point-btn');
            if (rallyBtn) rallyBtn.classList.remove('active');
        } catch (error) {
            console.error('Failed to set rally point:', error);
        }
    }

    /**
     * Reload map
     */
    async reloadMap() {
        const mapRenderer = this.gameState.getMapRenderer();
        if (mapRenderer) {
            await mapRenderer.loadMap(this.gameState.gameId);
        }
    }

    /**
     * Update units
     */
    updateUnits(units) {
        const mapRenderer = this.gameState.getMapRenderer();
        if (mapRenderer) {
            mapRenderer.updateUnits(units);
        }
    }

    /**
     * Render
     */
    render() {
        const mapRenderer = this.gameState.getMapRenderer();
        if (mapRenderer) {
            // Sync selected units to map renderer
            mapRenderer.setSelectedUnits(this.gameState.getSelectedUnits());

            // Sync selected building to map renderer
            mapRenderer.setSelectedBuilding(this.gameState.getSelectedBuilding());

            // Sync hovered building to map renderer
            mapRenderer.setHoveredBuilding(this.gameState.getHoveredBuilding());

            mapRenderer.render();

            // Update minimap
            const miniMapCanvas = document.getElementById('miniMap');
            if (miniMapCanvas) {
                mapRenderer.renderMinimap(miniMapCanvas);
            }
        }
    }

    /**
     * Start heartbeat interval
     */
    startHeartbeat() {
        const intervalId = setInterval(() => {
            this.networkManager.sendHeartbeat();
        }, 5000);
        this.gameState.setInterval('heartbeat', intervalId);
    }

    /**
     * Cleanup
     */
    cleanup() {
        this.gameState.clearAllIntervals();
        if (this.inputHandler) {
            this.inputHandler.cleanup();
        }
        this.networkManager.disconnect();
    }
}
