class GameController {
    constructor(gameId, playerName) {
        this.eventBus = new EventBus();
        this.gameState = new GameState(this.eventBus);
        this.gameState.init(gameId, playerName);

        this.networkManager = new NetworkManager(
            this.gameState,
            this.eventBus,
            'http://localhost:8080/api/games',
            'http://localhost:8080/ws'
        );

        this.selectionManager = new SelectionManager(this.gameState, this.eventBus);
        this.inputHandler = null;

        this.resourceDisplay = new ResourceDisplay(this.eventBus);
        this.villagerStatsPanel = new VillagerStatsPanel(this.eventBus);
        this.unitInfoPanel = new UnitInfoPanel(this.gameState, this.eventBus);
        this.buildingInfoPanel = new BuildingInfoPanel(this.gameState, this.eventBus);
        this.productionPanel = new ProductionPanel(this.gameState, this.eventBus, this.networkManager);
        this.buildQueuePanel = new BuildQueuePanel(this.gameState, this.eventBus);
        this.resourceNodeInfoPanel = new ResourceNodeInfoPanel(this.gameState, this.eventBus);
        this.gatherSlotDebugPanel = new GatherSlotDebugPanel(this.gameState, this.eventBus);

        this.resourceNodesMap = new Map();

        this.setupEventHandlers();
    }

    setupEventHandlers() {
        this.eventBus.on('network:playersDisconnected', () => this.loadPlayers());
        this.eventBus.on('network:playersBooted', () => this.loadPlayers());
        this.eventBus.on('network:gameStatus', (data) => this.handleGameStatus(data.status));
        this.eventBus.on('network:productionQueueUpdate', () => this.loadProductionQueue());
        this.eventBus.on('network:mapUpdate', () => this.reloadMap());
        this.eventBus.on('network:unitsUpdate', (units) => this.updateUnits(units));
        this.eventBus.on('network:resourceDepleted', (data) => this.handleResourceDepleted(data));
        this.eventBus.on('network:playerStatus', (data) => {
            this.gameState.updatePlayerStatus(data.playerName, data.status);
        });

        this.eventBus.on('input:click', (data) => this.handleClick(data));
        this.eventBus.on('input:doubleClick', (data) => this.handleDoubleClick(data));
        this.eventBus.on('input:rightClick', (data) => this.handleRightClick(data));
        this.eventBus.on('input:hover', (data) => this.handleHover(data));
        this.eventBus.on('input:dragEnd', (data) => this.handleDragEnd(data));

        this.eventBus.on('hotkey:selectTownCenter', () => this.selectionManager.selectTownCenter());
        this.eventBus.on('hotkey:toggleRallyPoint', () => this.toggleRallyPoint());

        this.eventBus.on('selection:unitsChanged', () => this.render());
        this.eventBus.on('selection:buildingChanged', () => this.render());
        this.eventBus.on('drag:updated', (data) => this.updateDragVisual(data));
        this.eventBus.on('drag:ended', () => this.clearDragVisual());

        this.eventBus.on('render:requested', () => this.render());
    }

    async init() {
        try {
            const game = await this.networkManager.loadGame();
            this.gameState.setCurrentGame(game);

            await this.loadPlayers();

            const canvas = document.getElementById('gameCanvas');
            const mapRenderer = new MapRenderer(canvas);
            this.gameState.setMapRenderer(mapRenderer);

            this.gatherSlotDebugPanel.mapRenderer = mapRenderer;

            await mapRenderer.loadMap(this.gameState.gameId);

            await this.loadResourceNodes();

            this.inputHandler = new InputHandler(this.gameState, this.eventBus, canvas);

            await this.networkManager.connectWebSocket();

            this.startHeartbeat();

            this.startQueuePolling();

            await this.networkManager.updatePlayerStatus('READY');

            return true;
        } catch (error) {
            console.error('Error initializing game:', error);
            throw error;
        }
    }

    async loadPlayers() {
        const players = await this.networkManager.loadPlayers();
        this.gameState.setPlayers(players);

        const currentPlayer = this.gameState.getCurrentPlayer();
        if (currentPlayer && currentPlayer.resources) {
            this.eventBus.emit('network:resourcesUpdate', currentPlayer.resources);
        }
    }

    async loadProductionQueue() {
        const queue = await this.networkManager.loadProductionQueue();
        this.eventBus.emit('productionQueue:updated', queue);
    }

    /**
     * Load resource nodes from server
     */
    async loadResourceNodes() {
        try {
            const response = await fetch(`http://localhost:8080/api/games/${this.gameState.gameId}/resources`);
            if (!response.ok) {
                return;
            }

            const resourceNodes = await response.json();

            // Build map of resource nodes by coordinates
            this.resourceNodesMap.clear();
            resourceNodes.forEach(node => {
                const key = `${node.x},${node.y}`;
                this.resourceNodesMap.set(key, node);
            });
        } catch (error) {
            console.error('Error loading resource nodes:', error);
        }
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
                // Check if a resource node was clicked (for debug panel)
                const clickedResource = this.getResourceNodeAt(tileX, tileY);
                if (clickedResource) {
                    this.eventBus.emit('resourceNode:clicked', clickedResource);
                } else {
                    // Emit map clicked event
                    this.eventBus.emit('map:clicked', { tileX, tileY });
                }

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

        const unitIds = ownedUnits.map(u => u.id);

        // Check if there's a resource node at this location
        const resourceNode = this.getResourceNodeAt(tileX, tileY);
        const mapRenderer = this.gameState.getMapRenderer();

        if (resourceNode && !resourceNode.depleted) {
            // Show gather click indicator (gold color)
            mapRenderer.addClickIndicator(tileX, tileY, 'gather');

            // Command units to gather from resource
            try {
                await this.networkManager.gatherResource(unitIds, tileX, tileY);
            } catch (error) {
                console.error('Failed to command resource gathering:', error);
            }
        } else {
            // Show move click indicator (green color)
            mapRenderer.addClickIndicator(tileX, tileY, 'move');

            // Move units
            try {
                await this.networkManager.moveUnits(unitIds, tileX, tileY);
            } catch (error) {
                console.error('Failed to move units:', error);
            }
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
            mapRenderer.setHoveredResource(null);
            this.eventBus.emit('resourceNode:hovered', null);
            this.render();
            return;
        }

        const unit = mapRenderer.getUnitAt(tileX, tileY);
        const building = this.selectionManager.findBuildingAtPosition(tileX, tileY);
        const resourceNode = this.getResourceNodeAt(tileX, tileY);

        // Update hovered resource in map renderer
        mapRenderer.setHoveredResource(resourceNode);

        // Emit resource node hover event if hovering over a resource
        if (resourceNode) {
            this.eventBus.emit('resourceNode:hovered', resourceNode);
        } else {
            this.eventBus.emit('resourceNode:hovered', null);
        }

        if (unit !== mapRenderer.hoveredUnit ||
            building !== this.gameState.getHoveredBuilding() ||
            resourceNode !== mapRenderer.getHoveredResource()) {
            mapRenderer.setHoveredUnit(unit);
            this.gameState.setHoveredBuilding(building);
            this.render();
        }
    }

    /**
     * Get resource node at coordinates
     */
    getResourceNodeAt(x, y) {
        const key = `${x},${y}`;
        return this.resourceNodesMap.get(key);
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
            await this.networkManager.setRallyPoint(building, tileX, tileY);

            // Update the building object directly instead of reloading the entire map
            building.rallyPointX = tileX;
            building.rallyPointY = tileY;

            // Update the building in the map data
            const mapRenderer = this.gameState.getMapRenderer();
            if (mapRenderer && mapRenderer.mapData && mapRenderer.mapData.buildings) {
                const buildings = Array.isArray(mapRenderer.mapData.buildings)
                    ? mapRenderer.mapData.buildings
                    : JSON.parse(mapRenderer.mapData.buildings);

                const buildingInMap = buildings.find(b => b.x === building.x && b.y === building.y && b.type === building.type);
                if (buildingInMap) {
                    buildingInMap.rallyPointX = tileX;
                    buildingInMap.rallyPointY = tileY;
                }
            }

            // Trigger UI update by re-emitting the selection event
            this.eventBus.emit('selection:buildingChanged', building);

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
     * Handle resource node depletion
     * Removes the resource visually from the map
     */
    handleResourceDepleted(data) {
        const mapRenderer = this.gameState.getMapRenderer();
        if (mapRenderer) {
            mapRenderer.removeResource(data.x, data.y);
        }
    }

    /**
     * Update units
     */
    updateUnits(units) {
        const mapRenderer = this.gameState.getMapRenderer();
        if (mapRenderer) {
            mapRenderer.updateUnits(units);

            // Update GameState's selected units with fresh data from map renderer
            // This ensures the UI panels show current unit state (gather status, carrying resources, etc.)
            const selectedUnits = mapRenderer.getSelectedUnits();
            if (selectedUnits && selectedUnits.length > 0) {
                this.gameState.setSelectedUnits(selectedUnits);
                this.unitInfoPanel.update();
            }
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
     * Start production queue polling
     */
    startQueuePolling() {
        // Load queue immediately
        this.loadProductionQueue();

        // Poll every 1 second to update progress
        const intervalId = setInterval(() => {
            this.loadProductionQueue();
        }, 1000);
        this.gameState.setInterval('queuePolling', intervalId);
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
