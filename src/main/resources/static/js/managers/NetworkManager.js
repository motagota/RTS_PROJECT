/**
 * NetworkManager - Handles all network communication (WebSocket and HTTP)
 * Single Responsibility: Manage network communication
 * Dependency Inversion: Depends on GameState and EventBus abstractions
 */
class NetworkManager {
    constructor(gameState, eventBus, apiBaseUrl, wsBaseUrl) {
        this.gameState = gameState;
        this.eventBus = eventBus;
        this.apiBaseUrl = apiBaseUrl;
        this.wsBaseUrl = wsBaseUrl;
        this.stompClient = null;
    }

    /**
     * Connect to WebSocket
     */
    connectWebSocket() {
        const socket = new SockJS(this.wsBaseUrl);
        this.stompClient = Stomp.over(socket);

        // Disable debug logging
        this.stompClient.debug = null;

        return new Promise((resolve, reject) => {
            this.stompClient.connect({},
                (frame) => {
                    // Subscribe to game updates
                    this.stompClient.subscribe(
                        `/topic/game/${this.gameState.gameId}`,
                        (message) => {
                            const update = JSON.parse(message.body);
                            this.handleGameUpdate(update);
                        }
                    );

                    this.gameState.setStompClient(this.stompClient);
                    this.eventBus.emit('network:connected');
                    resolve();
                },
                (error) => {
                    console.error('WebSocket connection error:', error);
                    this.eventBus.emit('network:error', error);
                    reject(error);
                }
            );
        });
    }

    /**
     * Handle incoming WebSocket game update
     */
    handleGameUpdate(update) {
        switch (update.type) {
            case 'PLAYER_STATUS':
                this.eventBus.emit('network:playerStatus', {
                    playerName: update.playerName,
                    status: update.status
                });
                break;

            case 'PLAYER_DISCONNECTED':
                if (update.players) {
                    this.eventBus.emit('network:playersDisconnected', update.players);
                }
                break;

            case 'PLAYERS_BOOTED':
                this.eventBus.emit('network:playersBooted');
                break;

            case 'GAME_STATUS':
                this.eventBus.emit('network:gameStatus', {
                    status: update.status
                });
                break;

            case 'RESOURCES_UPDATE':
                if (update.playerName === this.gameState.playerName && update.resources) {
                    this.eventBus.emit('network:resourcesUpdate', update.resources);
                }
                break;

            case 'PRODUCTION_QUEUE_UPDATE':
                if (update.playerName === this.gameState.playerName) {
                    this.eventBus.emit('network:productionQueueUpdate');
                }
                break;

            case 'MAP_UPDATE':
                this.eventBus.emit('network:mapUpdate');
                break;

            case 'UNITS_UPDATE':
                if (update.units) {
                    this.eventBus.emit('network:unitsUpdate', update.units);
                }
                break;
        }
    }

    /**
     * Send heartbeat to server
     */
    async sendHeartbeat() {
        try {
            await fetch(`${this.apiBaseUrl}/${this.gameState.gameId}/players/${this.gameState.playerName}/heartbeat`, {
                method: 'POST'
            });
        } catch (error) {
            console.error('Failed to send heartbeat:', error);
        }
    }

    /**
     * Update player status
     */
    async updatePlayerStatus(status) {
        try {
            const response = await fetch(
                `${this.apiBaseUrl}/${this.gameState.gameId}/players/${this.gameState.playerName}/status`,
                {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ status })
                }
            );
            return response.ok;
        } catch (error) {
            console.error('Failed to update player status:', error);
            return false;
        }
    }

    /**
     * Load game data
     */
    async loadGame() {
        try {
            const response = await fetch(`${this.apiBaseUrl}/${this.gameState.gameId}`);
            if (!response.ok) throw new Error('Failed to load game');
            return await response.json();
        } catch (error) {
            console.error('Error loading game:', error);
            throw error;
        }
    }

    /**
     * Load players
     */
    async loadPlayers() {
        try {
            const response = await fetch(`${this.apiBaseUrl}/${this.gameState.gameId}/players`);
            if (!response.ok) throw new Error('Failed to load players');
            return await response.json();
        } catch (error) {
            console.error('Error loading players:', error);
            throw error;
        }
    }

    /**
     * Load production queue
     */
    async loadProductionQueue() {
        try {
            const response = await fetch(
                `${this.apiBaseUrl}/${this.gameState.gameId}/players/${this.gameState.playerName}/queue`
            );
            if (!response.ok) throw new Error('Failed to load production queue');
            return await response.json();
        } catch (error) {
            console.error('Error loading production queue:', error);
            return [];
        }
    }

    /**
     * Enqueue production item
     */
    async enqueueProduction(unitType) {
        try {
            const response = await fetch(
                `${this.apiBaseUrl}/${this.gameState.gameId}/players/${this.gameState.playerName}/queue`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ unitType })
                }
            );

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.error || 'Failed to enqueue production');
            }

            return await response.json();
        } catch (error) {
            console.error('Error enqueuing production:', error);
            throw error;
        }
    }

    /**
     * Move units to a location
     */
    async moveUnits(unitIds, targetX, targetY) {
        try {
            const response = await fetch(
                `${this.apiBaseUrl}/${this.gameState.gameId}/units/move`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ unitIds, targetX, targetY })
                }
            );

            if (!response.ok) {
                throw new Error('Failed to move units');
            }
        } catch (error) {
            console.error('Error moving units:', error);
            throw error;
        }
    }

    /**
     * Command units to gather from a resource node
     */
    async gatherResource(unitIds, resourceX, resourceY) {
        console.log('=== NetworkManager.gatherResource called ===');
        console.log('Unit IDs:', unitIds);
        console.log('Resource at:', resourceX, resourceY);
        console.log('API URL:', `${this.apiBaseUrl}/${this.gameState.gameId}/units/gather`);

        try {
            const payload = { unitIds, resourceX, resourceY };
            console.log('Sending payload:', JSON.stringify(payload));

            const response = await fetch(
                `${this.apiBaseUrl}/${this.gameState.gameId}/units/gather`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                }
            );

            console.log('Response status:', response.status);

            if (!response.ok) {
                const errorText = await response.text();
                console.error('Server error response:', errorText);
                throw new Error('Failed to command resource gathering');
            }

            const result = await response.json();
            console.log('Gather command successful:', result);
        } catch (error) {
            console.error('Error commanding resource gathering:', error);
            throw error;
        }
    }

    /**
     * Set rally point for a building
     */
    async setRallyPoint(buildingId, rallyX, rallyY) {
        try {
            const response = await fetch(
                `${this.apiBaseUrl}/${this.gameState.gameId}/buildings/${buildingId}/rally-point`,
                {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ rallyX, rallyY })
                }
            );

            if (!response.ok) {
                throw new Error('Failed to set rally point');
            }
        } catch (error) {
            console.error('Error setting rally point:', error);
            throw error;
        }
    }

    /**
     * Disconnect WebSocket
     */
    disconnect() {
        if (this.stompClient && this.stompClient.connected) {
            this.stompClient.disconnect();
        }
    }
}
