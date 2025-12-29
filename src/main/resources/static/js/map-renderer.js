/**
 * Map Renderer - Handles rendering of terrain maps on canvas
 */
class MapRenderer {
    constructor(canvas) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d');
        this.mapData = null;
        this.playerColors = null;

        // Off-screen canvas for caching static terrain/buildings
        this.terrainCanvas = document.createElement('canvas');
        this.terrainCtx = this.terrainCanvas.getContext('2d');
        this.terrainCached = false;
        this.terrainColors = {
            // Base Terrains
            0: '#2ecc71',  // GRASS - Green
            1: '#f39c12',  // DESERT - Sandy yellow
            2: '#ecf0f1',  // SNOW - White
            3: '#e74c3c',  // LAVA - Red
            4: '#3498db',  // WATER - Blue
            // RTS Resources
            5: '#95a5a6',  // STONE - Gray (Stone mine)
            6: '#8b4513',  // WOOD/DIRT - Brown (Forest) - DEPRECATED
            7: '#FFD700',  // GOLD - Gold (Gold mine)
            8: '#FF69B4',  // FOOD - Pink (Berries/Farm)
            9: '#CD853F',  // HUNT - Tan (Boar/Deer)
            // Forest System (AoE2-style)
            10: '#228B22', // FOREST - Dark green (forest terrain base layer)
            11: '#006400'  // TREE - Darker green (tree objects on forest)
        };
        this.tileSize = 8;  // Size of each tile in pixels
        this.viewportX = 0;
        this.viewportY = 0;
        this.zoom = 1.0;
        this.selectedBuilding = null;  // Currently selected building
        this.hoveredBuilding = null;   // Currently hovered building
        this.selectedUnits = [];       // Array of selected units (for multi-selection)
        this.hoveredUnit = null;       // Currently hovered unit

        // Client-side movement interpolation
        this.unitDisplayPositions = new Map(); // Map of unit.id -> {x, y} for smooth rendering
        this.lastUpdateTime = Date.now();
        this.animationFrameId = null;

        // Click indicators for move commands
        this.clickIndicators = []; // Array of {x, y, startTime, duration}

        // Drag selection rectangle
        this.dragRect = null; // {startX, startY, endX, endY} in pixel coordinates

        // Start animation loop
        this.startAnimationLoop();
    }

    /**
     * Load map data from the server
     */
    async loadMap(gameId) {
        try {
            const response = await fetch(`http://localhost:8080/api/maps/game/${gameId}`);
            if (!response.ok) {
                throw new Error('Failed to load map');
            }

            this.mapData = await response.json();

            // Parse terrain data if it's a JSON string
            if (typeof this.mapData.terrainData === 'string') {
                this.mapData.terrainData = JSON.parse(this.mapData.terrainData);
            }

            // Parse player starts if it's a JSON string
            if (typeof this.mapData.playerStarts === 'string') {
                this.mapData.playerStarts = JSON.parse(this.mapData.playerStarts);
            }

            // Parse buildings if it's a JSON string
            if (typeof this.mapData.buildings === 'string') {
                this.mapData.buildings = JSON.parse(this.mapData.buildings);
            }

            // Parse units and initialize display positions
            if (this.mapData.units) {
                let units = this.mapData.units;
                if (typeof units === 'string') {
                    units = JSON.parse(units);
                    this.mapData.units = units;
                }
                // Initialize display positions for all units
                units.forEach(unit => {
                    this.unitDisplayPositions.set(unit.id, { x: unit.x, y: unit.y });
                });
            }

            const canvasWidth = this.mapData.width * this.tileSize;
            const canvasHeight = this.mapData.height * this.tileSize;
            this.setCanvasSize(canvasWidth, canvasHeight);

            // Invalidate terrain cache when new map loads
            this.terrainCached = false;

            // Trigger initial render to create cache and display map
            this.render();

            return this.mapData;
        } catch (error) {
            console.error('Error loading map:', error);
            throw error;
        }
    }

    /**
     * Render the full map to the canvas
     */
    render() {
        if (!this.mapData || !this.mapData.terrainData) {
            this.renderPlaceholder();
            return;
        }

        const terrainData = this.mapData.terrainData;
        const width = this.mapData.width;
        const height = this.mapData.height;

        // Clear canvas
        this.ctx.fillStyle = '#000';
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);

        // Center the map
        const offsetX = Math.max(0, (this.canvas.width - width * this.tileSize) / 2);
        const offsetY = Math.max(0, (this.canvas.height - height * this.tileSize) / 2);

        // Render or use cached terrain layer
        if (!this.terrainCached) {
            this.renderTerrainToCache(terrainData, width, height, offsetX, offsetY);
        }

        // Draw cached terrain and buildings layer
        this.ctx.drawImage(this.terrainCanvas, 0, 0);

        // Render building selection/hover highlights (dynamic, not cached)
        if (this.mapData.buildings) {
            this.renderBuildingHighlights(offsetX, offsetY);
        }

        // Render units (villagers, soldiers, etc) - not cached, dynamic
        if (this.mapData.units) {
            this.renderUnits(offsetX, offsetY);
        }

        // Render click indicators on top of everything
        this.renderClickIndicators(offsetX, offsetY);

        // Render drag selection rectangle
        this.renderDragSelection();

        // Render player starting positions
        if (this.mapData.playerStarts) {
            this.renderPlayerStarts(offsetX, offsetY);
        }
    }

    /**
     * Render terrain and buildings to an off-screen cache canvas
     */
    renderTerrainToCache(terrainData, width, height, offsetX, offsetY) {
        // Size the terrain canvas to match main canvas
        this.terrainCanvas.width = this.canvas.width;
        this.terrainCanvas.height = this.canvas.height;

        // Clear with black background
        this.terrainCtx.fillStyle = '#000';
        this.terrainCtx.fillRect(0, 0, this.terrainCanvas.width, this.terrainCanvas.height);

        // Check if terrainData is in new format (cells array) or old format (2D array)
        if (terrainData.cells) {
            // New format: {width, height, cells: [{x, y, terrain, owner, object}, ...]}
            const cells = terrainData.cells;

            // Render each cell
            for (const cell of cells) {
                const x = cell.x;
                const y = cell.y;
                const terrainType = cell.terrain;

                // Get terrain color
                const terrainId = this.getTerrainId(terrainType);
                let color = this.terrainColors[terrainId] || '#2ecc71'; // Default to grass

                // If cell has owner, use player color
                if (cell.owner !== undefined && cell.owner !== null) {
                    color = this.playerColors ? this.playerColors[cell.owner] : color;
                }

                this.terrainCtx.fillStyle = color;
                this.terrainCtx.fillRect(
                    offsetX + x * this.tileSize,
                    offsetY + y * this.tileSize,
                    this.tileSize,
                    this.tileSize
                );

                // Render object if present
                if (cell.object) {
                    this.renderObjectToCache(x, y, cell.object, offsetX, offsetY);
                }
            }
        } else {
            // Old format: 2D array terrain[x][y]
            for (let x = 0; x < width; x++) {
                for (let y = 0; y < height; y++) {
                    const terrainType = terrainData[x][y];
                    const color = this.terrainColors[terrainType] || '#000000';

                    this.terrainCtx.fillStyle = color;
                    this.terrainCtx.fillRect(
                        offsetX + x * this.tileSize,
                        offsetY + y * this.tileSize,
                        this.tileSize,
                        this.tileSize
                    );

                    this.terrainCtx.strokeStyle = '#444';
                    this.terrainCtx.lineWidth = 0.5;
                    this.terrainCtx.strokeRect(
                        offsetX + x * this.tileSize,
                        offsetY + y * this.tileSize,
                        this.tileSize,
                        this.tileSize
                    );
                }
            }
        }

        // Render buildings to cache (buildings are static)
        if (this.mapData.buildings) {
            this.renderBuildingsToCache(offsetX, offsetY);
        }

        // Mark as cached
        this.terrainCached = true;
    }

    /**
     * Render object to cache canvas
     */
    renderObjectToCache(x, y, objectType, offsetX, offsetY) {
        const pixelX = offsetX + x * this.tileSize;
        const pixelY = offsetY + y * this.tileSize;

        if (objectType === 'TREE') {
            // Draw tree
            this.terrainCtx.fillStyle = '#228B22';
            this.terrainCtx.fillRect(pixelX, pixelY, this.tileSize, this.tileSize);
            this.terrainCtx.fillStyle = '#006400';
            this.terrainCtx.beginPath();
            this.terrainCtx.arc(
                pixelX + this.tileSize / 2,
                pixelY + this.tileSize / 2,
                this.tileSize * 0.4,
                0,
                Math.PI * 2
            );
            this.terrainCtx.fill();
        }
    }

    /**
     * Render buildings to cache canvas
     */
    renderBuildingsToCache(offsetX, offsetY) {
        let buildings = this.mapData.buildings;
        if (typeof buildings === 'string') {
            try {
                buildings = JSON.parse(buildings);
                this.mapData.buildings = buildings;
            } catch (e) {
                console.error('Failed to parse buildings:', e);
                return;
            }
        }

        if (!buildings || buildings.length === 0) {
            return;
        }

        buildings.forEach(building => {
            const x = offsetX + building.x * this.tileSize;
            const y = offsetY + building.y * this.tileSize;

            // Default to 3x3 for headquarters, 1x1 for others if width/height not specified
            const buildingWidth = building.width || (building.type === 'HEADQUARTERS' ? 3 : 1);
            const buildingHeight = building.height || (building.type === 'HEADQUARTERS' ? 3 : 1);
            const width = buildingWidth * this.tileSize;
            const height = buildingHeight * this.tileSize;

            const playerColor = this.getPlayerColor(building.playerNumber);

            // Draw building rectangle
            this.terrainCtx.fillStyle = playerColor;
            this.terrainCtx.fillRect(x, y, width, height);

            // Draw building border
            this.terrainCtx.strokeStyle = '#000';
            this.terrainCtx.lineWidth = 2;
            this.terrainCtx.strokeRect(x, y, width, height);

            // Draw building type symbol
            const symbol = building.type === 'HEADQUARTERS' ? 'HQ' : building.type.charAt(0);
            this.terrainCtx.fillStyle = '#fff';
            this.terrainCtx.font = `bold ${this.tileSize}px Arial`;
            this.terrainCtx.textAlign = 'center';
            this.terrainCtx.textBaseline = 'middle';
            this.terrainCtx.fillText(symbol, x + width / 2, y + height / 2);
        });
    }

    /**
     * Render building highlights (selection, hover, rally points, production indicators)
     * This renders only the dynamic parts on top of the cached building layer
     */
    renderBuildingHighlights(offsetX, offsetY) {
        let buildings = this.mapData.buildings;
        if (typeof buildings === 'string') {
            try {
                buildings = JSON.parse(buildings);
                this.mapData.buildings = buildings;
            } catch (e) {
                console.error('Failed to parse buildings:', e);
                return;
            }
        }

        if (!buildings || buildings.length === 0) {
            return;
        }

        buildings.forEach(building => {
            const x = offsetX + building.x * this.tileSize;
            const y = offsetY + building.y * this.tileSize;

            // Use same default logic as cache rendering
            const buildingWidth = building.width || (building.type === 'HEADQUARTERS' ? 3 : 1);
            const buildingHeight = building.height || (building.type === 'HEADQUARTERS' ? 3 : 1);

            // Check if building is selected or hovered
            const isHovered = this.hoveredBuilding &&
                this.hoveredBuilding.x === building.x &&
                this.hoveredBuilding.y === building.y &&
                this.hoveredBuilding.type === building.type;

            const isSelected = this.selectedBuilding &&
                this.selectedBuilding.x === building.x &&
                this.selectedBuilding.y === building.y &&
                this.selectedBuilding.type === building.type;

            // Draw selection indicator
            if (isSelected) {
                this.renderSelectionIndicator(x, y, buildingWidth, buildingHeight);
            } else if (isHovered) {
                this.renderHoverIndicator(x, y, buildingWidth, buildingHeight);
            }

            // Draw production indicator
            if (this.hasProductionQueue(building)) {
                const indicatorSize = this.tileSize * 0.3;
                this.ctx.fillStyle = '#00d4ff';
                this.ctx.beginPath();
                this.ctx.arc(x + indicatorSize, y + indicatorSize, indicatorSize / 2, 0, Math.PI * 2);
                this.ctx.fill();
                this.ctx.strokeStyle = '#000';
                this.ctx.lineWidth = 1;
                this.ctx.stroke();
            }

            // Draw rally point if set and building is selected
            if (building.rallyPointX != null && building.rallyPointY != null && isSelected) {
                this.renderRallyPoint(offsetX, offsetY, building);
            }
        });
    }

    /**
     * Convert terrain name to terrain ID for color lookup
     */
    getTerrainId(terrainName) {
        const terrainMap = {
            'GRASS': 0,
            'DESERT': 1,
            'SNOW': 2,
            'LAVA': 3,
            'WATER': 4,
            'STONE': 5,
            'DIRT': 6,
            'GOLD': 7,
            'FOOD': 8,
            'HUNT': 9,
            'FOREST': 10,
            'TREE': 11
        };
        return terrainMap[terrainName] !== undefined ? terrainMap[terrainName] : 0;
    }

    /**
     * Render an object (gold, stone, berries, etc.) on a tile
     */
    renderObject(x, y, objectType, offsetX, offsetY) {
        const objectColors = {
            'GOLD': '#FFD700',
            'STONE': '#808080',
            'BERRIES': '#FF1493',
            'FORAGE': '#FF1493',
            'TREE': '#228B22'
        };

        const color = objectColors[objectType] || '#FFFFFF';
        const centerX = offsetX + x * this.tileSize + this.tileSize / 2;
        const centerY = offsetY + y * this.tileSize + this.tileSize / 2;
        const radius = this.tileSize * 0.3;

        this.ctx.fillStyle = color;
        this.ctx.beginPath();
        this.ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
        this.ctx.fill();
        this.ctx.strokeStyle = '#000';
        this.ctx.lineWidth = 0.5;
        this.ctx.stroke();
    }

    /**
     * Set production queue data for rendering indicators
     */
    setProductionQueue(queueData) {
        this.productionQueue = queueData || [];
    }

    /**
     * Check if a building has items in production queue
     */
    hasProductionQueue(building) {
        if (!this.productionQueue) return false;
        return this.productionQueue.some(item =>
            item.buildingX === building.x &&
            item.buildingY === building.y &&
            item.buildingType === building.type &&
            item.status === 'IN_PROGRESS'
        );
    }

    /**
     * Render buildings on the map with player colors
     */
    renderBuildings(offsetX, offsetY) {
        const buildings = this.mapData.buildings;

        buildings.forEach(building => {
            const x = offsetX + building.x * this.tileSize;
            const y = offsetY + building.y * this.tileSize;

            // Get player color (default to white if not set)
            const playerColor = this.getPlayerColor(building.playerNumber);

            // Get building size from the building object (default to 1x1 if not specified)
            const buildingWidth = building.width || 1;
            const buildingHeight = building.height || 1;
            const totalWidth = buildingWidth * this.tileSize;
            const totalHeight = buildingHeight * this.tileSize;

            // Draw the building as a single unified rectangle
            this.ctx.fillStyle = playerColor;
            this.ctx.fillRect(x, y, totalWidth, totalHeight);

            // Draw single border around entire building
            this.ctx.strokeStyle = '#000';
            this.ctx.lineWidth = 2;
            this.ctx.strokeRect(x, y, totalWidth, totalHeight);

            // Draw building type symbol (H for headquarters) - centered on the building
            if (building.type === 'HEADQUARTERS') {
                const centerX = x + totalWidth / 2;
                const centerY = y + totalHeight / 2;

                this.ctx.fillStyle = '#fff';
                this.ctx.font = `bold ${Math.min(totalWidth, totalHeight) - 2}px Arial`;
                this.ctx.textAlign = 'center';
                this.ctx.textBaseline = 'middle';
                this.ctx.fillText('H', centerX, centerY);
            }

            // Draw production indicator in top-left corner if building has queue
            if (this.hasProductionQueue(building)) {
                const indicatorSize = this.tileSize * 0.3;
                this.ctx.fillStyle = '#00d4ff';
                this.ctx.beginPath();
                this.ctx.arc(x + indicatorSize, y + indicatorSize, indicatorSize / 2, 0, Math.PI * 2);
                this.ctx.fill();
                this.ctx.strokeStyle = '#000';
                this.ctx.lineWidth = 1;
                this.ctx.stroke();
            }

            // Draw hover indicator if this building is hovered (but not selected)
            const isHovered = this.hoveredBuilding &&
                this.hoveredBuilding.x === building.x &&
                this.hoveredBuilding.y === building.y &&
                this.hoveredBuilding.type === building.type;

            const isSelected = this.selectedBuilding &&
                this.selectedBuilding.x === building.x &&
                this.selectedBuilding.y === building.y &&
                this.selectedBuilding.type === building.type;

            if (isSelected) {
                this.renderSelectionIndicator(x, y, buildingWidth, buildingHeight);
            } else if (isHovered) {
                this.renderHoverIndicator(x, y, buildingWidth, buildingHeight);
            }

            // Draw rally point if set and building is selected
            if (building.rallyPointX != null && building.rallyPointY != null && isSelected) {
                this.renderRallyPoint(offsetX, offsetY, building);
            }
        });
    }

    /**
     * Render rally point indicator for a building
     */
    renderRallyPoint(offsetX, offsetY, building) {
        const buildingCenterX = offsetX + (building.x + (building.width || 1) / 2) * this.tileSize;
        const buildingCenterY = offsetY + (building.y + (building.height || 1) / 2) * this.tileSize;
        const rallyX = offsetX + building.rallyPointX * this.tileSize + this.tileSize / 2;
        const rallyY = offsetY + building.rallyPointY * this.tileSize + this.tileSize / 2;

        // Draw line from building to rally point
        this.ctx.strokeStyle = 'rgba(255, 255, 255, 0.5)';
        this.ctx.lineWidth = 1;
        this.ctx.setLineDash([4, 4]);
        this.ctx.beginPath();
        this.ctx.moveTo(buildingCenterX, buildingCenterY);
        this.ctx.lineTo(rallyX, rallyY);
        this.ctx.stroke();
        this.ctx.setLineDash([]);

        // Draw flag at rally point
        const flagSize = this.tileSize;
        const playerColor = this.getPlayerColor(building.playerNumber);

        // Flag pole
        this.ctx.strokeStyle = '#666';
        this.ctx.lineWidth = 2;
        this.ctx.beginPath();
        this.ctx.moveTo(rallyX, rallyY);
        this.ctx.lineTo(rallyX, rallyY - flagSize);
        this.ctx.stroke();

        // Flag
        this.ctx.fillStyle = playerColor;
        this.ctx.beginPath();
        this.ctx.moveTo(rallyX, rallyY - flagSize);
        this.ctx.lineTo(rallyX + flagSize * 0.6, rallyY - flagSize * 0.7);
        this.ctx.lineTo(rallyX, rallyY - flagSize * 0.4);
        this.ctx.fill();

        // Flag outline
        this.ctx.strokeStyle = '#000';
        this.ctx.lineWidth = 1;
        this.ctx.stroke();
    }

    /**
     * Render units on the map
     */
    renderUnits(offsetX, offsetY) {
        // Get units (should already be parsed array)
        let units = this.mapData.units;

        // Only parse if it's still a string (backward compatibility)
        if (typeof units === 'string') {
            try {
                units = JSON.parse(units);
                this.mapData.units = units; // Cache parsed version
            } catch (e) {
                console.error('Failed to parse units:', e);
                return;
            }
        }

        if (!units || units.length === 0) {
            return;
        }

        units.forEach(unit => {
            // Use interpolated display position for smooth movement
            const displayPos = this.unitDisplayPositions.get(unit.id) || { x: unit.x, y: unit.y };
            const x = offsetX + displayPos.x * this.tileSize;
            const y = offsetY + displayPos.y * this.tileSize;

            // Get player color (default to white if not set)
            const playerColor = this.getPlayerColor(unit.playerNumber);

            // Draw unit circle
            const unitSize = this.tileSize * 0.8;
            const centerX = x + this.tileSize / 2;
            const centerY = y + this.tileSize / 2;

            this.ctx.fillStyle = playerColor;
            this.ctx.beginPath();
            this.ctx.arc(centerX, centerY, unitSize / 2, 0, Math.PI * 2);
            this.ctx.fill();

            // Draw unit border
            this.ctx.strokeStyle = '#000';
            this.ctx.lineWidth = 1;
            this.ctx.stroke();

            // Draw unit type symbol (V for villager, S for soldier)
            const symbol = unit.type === 'VILLAGER' ? 'V' : 'S';
            this.ctx.fillStyle = '#fff';
            this.ctx.font = `bold ${unitSize * 0.7}px Arial`;
            this.ctx.textAlign = 'center';
            this.ctx.textBaseline = 'middle';
            this.ctx.fillText(symbol, centerX, centerY);

            // Draw selection indicator if this unit is selected
            const isSelected = this.selectedUnits.some(u => u.id === unit.id);
            if (isSelected) {
                this.ctx.strokeStyle = '#00ff00';  // Green selection
                this.ctx.lineWidth = 2;
                this.ctx.beginPath();
                this.ctx.arc(centerX, centerY, unitSize / 2 + 3, 0, Math.PI * 2);
                this.ctx.stroke();
            }

            // Draw hover indicator if this unit is hovered
            if (this.hoveredUnit && this.hoveredUnit.id === unit.id) {
                this.ctx.strokeStyle = '#ffff00';  // Yellow hover
                this.ctx.lineWidth = 1;
                this.ctx.globalAlpha = 0.6;
                this.ctx.beginPath();
                this.ctx.arc(centerX, centerY, unitSize / 2 + 3, 0, Math.PI * 2);
                this.ctx.stroke();
                this.ctx.globalAlpha = 1.0;
            }
        });
    }

    /**
     * Render selection indicator around a building
     */
    renderSelectionIndicator(x, y, buildingWidth, buildingHeight) {
        const width = buildingWidth * this.tileSize;
        const height = buildingHeight * this.tileSize;

        // Draw animated selection box
        this.ctx.strokeStyle = '#00ff00';  // Bright green
        this.ctx.lineWidth = 2;
        this.ctx.setLineDash([4, 4]);  // Dashed line
        this.ctx.strokeRect(x - 2, y - 2, width + 4, height + 4);
        this.ctx.setLineDash([]);  // Reset to solid line

        // Draw corner brackets for extra visibility
        const cornerSize = 4;
        this.ctx.strokeStyle = '#ffffff';
        this.ctx.lineWidth = 2;

        // Top-left corner
        this.ctx.beginPath();
        this.ctx.moveTo(x - 2, y - 2 + cornerSize);
        this.ctx.lineTo(x - 2, y - 2);
        this.ctx.lineTo(x - 2 + cornerSize, y - 2);
        this.ctx.stroke();

        // Top-right corner
        this.ctx.beginPath();
        this.ctx.moveTo(x + width + 2 - cornerSize, y - 2);
        this.ctx.lineTo(x + width + 2, y - 2);
        this.ctx.lineTo(x + width + 2, y - 2 + cornerSize);
        this.ctx.stroke();

        // Bottom-left corner
        this.ctx.beginPath();
        this.ctx.moveTo(x - 2, y + height + 2 - cornerSize);
        this.ctx.lineTo(x - 2, y + height + 2);
        this.ctx.lineTo(x - 2 + cornerSize, y + height + 2);
        this.ctx.stroke();

        // Bottom-right corner
        this.ctx.beginPath();
        this.ctx.moveTo(x + width + 2 - cornerSize, y + height + 2);
        this.ctx.lineTo(x + width + 2, y + height + 2);
        this.ctx.lineTo(x + width + 2, y + height + 2 - cornerSize);
        this.ctx.stroke();
    }

    /**
     * Get player color for a given player number
     */
    getPlayerColor(playerNumber) {
        if (!this.playerColors || !this.playerColors[playerNumber]) {
            // Default colors if player colors not set
            const defaultColors = [
                '#FF0000', // Red - Player 1
                '#0000FF', // Blue - Player 2
                '#00FF00', // Green - Player 3
                '#FFFF00', // Yellow - Player 4
                '#FF00FF', // Magenta - Player 5
                '#00FFFF', // Cyan - Player 6
                '#FFA500', // Orange - Player 7
                '#800080'  // Purple - Player 8
            ];
            return defaultColors[(playerNumber - 1) % defaultColors.length];
        }
        return this.playerColors[playerNumber];
    }

    /**
     * Set player colors from game data
     */
    setPlayerColors(gamePlayers) {
        this.playerColors = {};
        gamePlayers.forEach(player => {
            // Map by playerSlot which should match playerNumber
            this.playerColors[player.playerSlot] = player.teamColor;
        });
    }

    /**
     * Render player starting positions on the map
     * Note: Player positions are now indicated by their colored headquarters buildings,
     * so we don't need to draw additional indicators here
     */
    renderPlayerStarts(offsetX, offsetY) {
        // Player starts are now clearly indicated by headquarters buildings
        // No additional rendering needed
    }

    /**
     * Render minimap version (smaller, overview)
     */
    renderMinimap(miniCanvas) {
        if (!this.mapData || !this.mapData.terrainData) {
            return;
        }

        const ctx = miniCanvas.getContext('2d');
        const terrainData = this.mapData.terrainData;
        const width = this.mapData.width;
        const height = this.mapData.height;

        // Calculate scale to fit minimap
        const scale = Math.min(miniCanvas.width / width, miniCanvas.height / height);
        const tileSize = Math.max(1, Math.floor(scale));

        // Center minimap
        const offsetX = (miniCanvas.width - width * tileSize) / 2;
        const offsetY = (miniCanvas.height - height * tileSize) / 2;

        // Clear
        ctx.fillStyle = '#000';
        ctx.fillRect(0, 0, miniCanvas.width, miniCanvas.height);

        // Check if terrainData is in new format (cells array) or old format (2D array)
        if (terrainData.cells) {
            // New format: {width, height, cells: [{x, y, terrain, owner, object}, ...]}
            const cells = terrainData.cells;

            // Render each cell
            for (const cell of cells) {
                const terrainId = this.getTerrainId(cell.terrain);
                let color = this.terrainColors[terrainId] || '#2ecc71';

                // If cell has owner, use player color
                if (cell.owner !== undefined && cell.owner !== null) {
                    color = this.playerColors ? this.playerColors[cell.owner] : color;
                }

                ctx.fillStyle = color;
                ctx.fillRect(
                    offsetX + cell.x * tileSize,
                    offsetY + cell.y * tileSize,
                    tileSize,
                    tileSize
                );
            }
        } else {
            // Old format: 2D array terrain[x][y]
            for (let x = 0; x < width; x++) {
                for (let y = 0; y < height; y++) {
                    const terrainType = terrainData[x][y];
                    const color = this.terrainColors[terrainType] || '#000000';

                    ctx.fillStyle = color;
                    ctx.fillRect(
                        offsetX + x * tileSize,
                        offsetY + y * tileSize,
                        tileSize,
                        tileSize
                    );
                }
            }
        }

        // Render buildings on minimap
        if (this.mapData.buildings) {
            this.mapData.buildings.forEach(building => {
                const playerColor = this.getPlayerColor(building.playerNumber);
                ctx.fillStyle = playerColor;
                ctx.fillRect(
                    offsetX + building.x * tileSize,
                    offsetY + building.y * tileSize,
                    Math.max(2, tileSize),
                    Math.max(2, tileSize)
                );
            });
        }

        // Player starts are indicated by headquarters buildings (rendered above)
        // No additional rendering needed
    }

    /**
     * Render placeholder when map is not loaded
     */
    renderPlaceholder() {
        this.ctx.fillStyle = '#1a1a1a';
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);

        this.ctx.fillStyle = '#00d4ff';
        this.ctx.font = '24px Arial';
        this.ctx.textAlign = 'center';
        this.ctx.textBaseline = 'middle';
        this.ctx.fillText('Loading map...', this.canvas.width / 2, this.canvas.height / 2);
    }

    /**
     * Set canvas size
     */
    setCanvasSize(width, height) {
        this.canvas.width = width;
        this.canvas.height = height;
    }

    /**
     * Get map info
     */
    getMapInfo() {
        if (!this.mapData) return null;

        return {
            width: this.mapData.width,
            height: this.mapData.height,
            playerCount: this.mapData.playerCount,
            templateName: this.mapData.mapTemplateName
        };
    }

    /**
     * Set the currently selected building
     */
    setSelectedBuilding(building) {
        this.selectedBuilding = building;
    }

    /**
     * Get the currently selected building
     */
    getSelectedBuilding() {
        return this.selectedBuilding;
    }

    /**
     * Set the currently hovered building
     */
    setHoveredBuilding(building) {
        this.hoveredBuilding = building;
    }

    /**
     * Get the currently hovered building
     */
    getHoveredBuilding() {
        return this.hoveredBuilding;
    }

    /**
     * Render hover indicator around a building
     */
    renderHoverIndicator(x, y, buildingWidth, buildingHeight) {
        const width = buildingWidth * this.tileSize;
        const height = buildingHeight * this.tileSize;

        // Draw subtle hover outline
        this.ctx.strokeStyle = '#ffff00';  // Yellow for hover
        this.ctx.lineWidth = 2;
        this.ctx.globalAlpha = 0.6;
        this.ctx.strokeRect(x - 1, y - 1, width + 2, height + 2);
        this.ctx.globalAlpha = 1.0;
    }

    /**
     * Start the animation loop for smooth unit movement
     */
    startAnimationLoop() {
        const animate = () => {
            const currentTime = Date.now();
            const deltaTime = (currentTime - this.lastUpdateTime) / 1000; // Convert to seconds
            this.lastUpdateTime = currentTime;

            // Cap delta time to prevent large jumps (e.g., when tab is inactive)
            const cappedDeltaTime = Math.min(deltaTime, 0.1); // Max 100ms

            // Update interpolated positions
            this.updateUnitInterpolation(cappedDeltaTime);

            // Continue animation
            this.animationFrameId = requestAnimationFrame(animate);
        };

        animate();
    }

    /**
     * Update unit display positions using lerp towards their target
     */
    updateUnitInterpolation(deltaTime) {
        if (!this.mapData || !this.mapData.units) {
            return;
        }

        let needsRedraw = false;

        // Parse units if needed
        let units = this.mapData.units;
        if (typeof units === 'string') {
            try {
                units = JSON.parse(units);
            } catch (e) {
                return;
            }
        }

        // Check if any units are actually moving first (optimization)
        let hasMovingUnits = false;
        for (const unit of units) {
            const displayPos = this.unitDisplayPositions.get(unit.id);
            if (!displayPos) continue;

            const dx = unit.x - displayPos.x;
            const dy = unit.y - displayPos.y;
            if (Math.abs(dx) > 0.01 || Math.abs(dy) > 0.01) {
                hasMovingUnits = true;
                break;
            }
        }

        // Skip interpolation if no units are moving
        if (!hasMovingUnits) {
            return;
        }

        units.forEach(unit => {
            // Initialize display position if not exists
            if (!this.unitDisplayPositions.has(unit.id)) {
                this.unitDisplayPositions.set(unit.id, { x: unit.x, y: unit.y });
                return;
            }

            const displayPos = this.unitDisplayPositions.get(unit.id);
            const serverPos = { x: unit.x, y: unit.y };

            // Calculate distance to server position
            const dx = serverPos.x - displayPos.x;
            const dy = serverPos.y - displayPos.y;
            const distance = Math.sqrt(dx * dx + dy * dy);

            // If there's a significant difference, lerp towards it
            if (distance > 0.01) {
                // Use unit's movement speed (default 0.1 tiles per tick at 10 FPS = 1 tile/sec)
                // Convert to client-side smooth movement
                const speed = (unit.movementSpeed || 0.1) * 10; // tiles per second
                const maxMove = speed * deltaTime;

                if (distance <= maxMove) {
                    // Snap to final position
                    displayPos.x = serverPos.x;
                    displayPos.y = serverPos.y;
                } else {
                    // Lerp towards server position
                    const t = maxMove / distance;
                    displayPos.x += dx * t;
                    displayPos.y += dy * t;
                }

                needsRedraw = true;
            }
        });

        // Clean up display positions for units that no longer exist
        const unitIds = new Set(units.map(u => u.id));
        for (const [id] of this.unitDisplayPositions) {
            if (!unitIds.has(id)) {
                this.unitDisplayPositions.delete(id);
            }
        }

        // Redraw only if any unit actually moved
        if (needsRedraw) {
            this.render();
        }
    }

    /**
     * Update units data without reloading entire map
     * @param {Array} units - Updated units array
     */
    updateUnits(units) {
        if (!this.mapData) {
            console.warn('Cannot update units: map data not loaded');
            return;
        }

        // Update selected units to maintain selection through position changes
        // Simply match by ID - much simpler!
        if (this.selectedUnits.length > 0) {
            const selectedIds = this.selectedUnits.map(u => u.id);
            this.selectedUnits = units.filter(u => selectedIds.includes(u.id));
        }

        // Initialize display positions for new units
        units.forEach(unit => {
            if (!this.unitDisplayPositions.has(unit.id)) {
                this.unitDisplayPositions.set(unit.id, { x: unit.x, y: unit.y });
            }
        });

        // Update units in map data (store as parsed array, not string)
        this.mapData.units = units;

        // Don't render here - let the animation loop handle it
    }

    /**
     * Update a single unit's position
     * @param {number} unitId - Unit identifier (or use x,y coords)
     * @param {number} newX - New X position
     * @param {number} newY - New Y position
     */
    updateUnitPosition(oldX, oldY, newX, newY) {
        if (!this.mapData || !this.mapData.units) {
            return;
        }

        // Parse units if string
        let units = this.mapData.units;
        if (typeof units === 'string') {
            try {
                units = JSON.parse(units);
                this.mapData.units = units;
            } catch (e) {
                console.error('Failed to parse units:', e);
                return;
            }
        }

        // Find and update the unit
        const unit = units.find(u => u.x === oldX && u.y === oldY);
        if (unit) {
            unit.x = newX;
            unit.y = newY;
            this.render(); // Re-render scene
        }
    }

    /**
     * Start a render loop for smooth animations
     * Call this once when the game starts
     */
    startRenderLoop() {
        // Removed continuous animation loop - we now render only on changes
        // Initial render
        this.render();
    }

    /**
     * Stop the render loop
     */
    stopRenderLoop() {
        if (this.animationFrameId) {
            cancelAnimationFrame(this.animationFrameId);
            this.animationFrameId = null;
        }
    }

    /**
     * Get unit at a specific map coordinate
     * @param {number} mapX - Map X coordinate (in tiles)
     * @param {number} mapY - Map Y coordinate (in tiles)
     * @returns {Object|null} Unit at that position or null
     */
    getUnitAt(mapX, mapY) {
        if (!this.mapData || !this.mapData.units) {
            return null;
        }

        let units = this.mapData.units;
        if (typeof units === 'string') {
            try {
                units = JSON.parse(units);
            } catch (e) {
                return null;
            }
        }

        // Use display positions for click detection (for smooth movement)
        // Check if click is within 0.5 tiles of the display position
        return units.find(unit => {
            const displayPos = this.unitDisplayPositions.get(unit.id) || { x: unit.x, y: unit.y };
            const dx = Math.abs(displayPos.x - mapX);
            const dy = Math.abs(displayPos.y - mapY);
            return dx < 1 && dy < 1;
        }) || null;
    }

    /**
     * Set the selected units (replaces current selection)
     * @param {Array} units - Array of units to select
     */
    setSelectedUnits(units) {
        this.selectedUnits = units || [];
    }

    /**
     * Add a unit to the selection (for Shift+Click)
     * @param {Object} unit - Unit to add to selection
     */
    addSelectedUnit(unit) {
        // Check if already selected (by ID)
        const alreadySelected = this.selectedUnits.some(u => u.id === unit.id);
        if (!alreadySelected) {
            this.selectedUnits.push(unit);
        }
    }

    /**
     * Remove a unit from the selection
     * @param {Object} unit - Unit to remove
     */
    removeSelectedUnit(unit) {
        this.selectedUnits = this.selectedUnits.filter(u => u.id !== unit.id);
    }

    /**
     * Get all currently selected units
     * @returns {Array}
     */
    getSelectedUnits() {
        return this.selectedUnits;
    }

    /**
     * Check if any units are selected
     * @returns {boolean}
     */
    hasSelectedUnits() {
        return this.selectedUnits.length > 0;
    }

    /**
     * Set the currently hovered unit
     * @param {Object|null} unit - Unit to hover or null to clear hover
     */
    setHoveredUnit(unit) {
        this.hoveredUnit = unit;
    }

    /**
     * Get the currently hovered unit
     * @returns {Object|null}
     */
    getHoveredUnit() {
        return this.hoveredUnit;
    }

    /**
     * Add a click indicator at the specified position
     * @param {number} x - Tile X coordinate
     * @param {number} y - Tile Y coordinate
     */
    addClickIndicator(x, y) {
        this.clickIndicators.push({
            x: x,
            y: y,
            startTime: Date.now(),
            duration: 600 // milliseconds
        });
    }

    /**
     * Render click indicators with animation
     */
    renderClickIndicators(offsetX, offsetY) {
        const currentTime = Date.now();

        // Remove expired indicators
        this.clickIndicators = this.clickIndicators.filter(indicator => {
            return (currentTime - indicator.startTime) < indicator.duration;
        });

        // Render active indicators
        this.clickIndicators.forEach(indicator => {
            const elapsed = currentTime - indicator.startTime;
            const progress = elapsed / indicator.duration; // 0 to 1

            // Position in pixels
            const centerX = offsetX + (indicator.x + 0.5) * this.tileSize;
            const centerY = offsetY + (indicator.y + 0.5) * this.tileSize;

            // Expanding circle animation
            const maxRadius = this.tileSize * 0.8;
            const radius = maxRadius * progress;

            // Fading opacity
            const opacity = 1 - progress;

            this.ctx.save();
            this.ctx.globalAlpha = opacity;

            // Draw outer circle (green)
            this.ctx.strokeStyle = '#00ff00';
            this.ctx.lineWidth = 2;
            this.ctx.beginPath();
            this.ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
            this.ctx.stroke();

            // Draw inner circle (white)
            this.ctx.strokeStyle = '#ffffff';
            this.ctx.lineWidth = 1;
            this.ctx.beginPath();
            this.ctx.arc(centerX, centerY, radius * 0.5, 0, Math.PI * 2);
            this.ctx.stroke();

            // Draw crosshair
            const crosshairSize = this.tileSize * 0.3;
            this.ctx.strokeStyle = '#00ff00';
            this.ctx.lineWidth = 2;
            this.ctx.beginPath();
            // Horizontal line
            this.ctx.moveTo(centerX - crosshairSize, centerY);
            this.ctx.lineTo(centerX + crosshairSize, centerY);
            // Vertical line
            this.ctx.moveTo(centerX, centerY - crosshairSize);
            this.ctx.lineTo(centerX, centerY + crosshairSize);
            this.ctx.stroke();

            this.ctx.restore();
        });
    }

    /**
     * Set drag selection rectangle coordinates
     * @param {number} startX - Start X in pixel coordinates
     * @param {number} startY - Start Y in pixel coordinates
     * @param {number} endX - End X in pixel coordinates
     * @param {number} endY - End Y in pixel coordinates
     */
    setDragSelection(startX, startY, endX, endY) {
        this.dragRect = { startX, startY, endX, endY };
        this.render(); // Trigger re-render to show the rectangle
    }

    /**
     * Clear drag selection rectangle
     */
    clearDragSelection() {
        this.dragRect = null;
        this.render(); // Trigger re-render to hide the rectangle
    }

    /**
     * Render the drag selection rectangle
     */
    renderDragSelection() {
        if (!this.dragRect) {
            return;
        }

        const { startX, startY, endX, endY } = this.dragRect;

        // Calculate rectangle bounds
        const minX = Math.min(startX, endX);
        const minY = Math.min(startY, endY);
        const width = Math.abs(endX - startX);
        const height = Math.abs(endY - startY);

        this.ctx.save();

        // Draw semi-transparent fill
        this.ctx.fillStyle = 'rgba(0, 255, 0, 0.1)'; // Light green fill
        this.ctx.fillRect(minX, minY, width, height);

        // Draw border
        this.ctx.strokeStyle = '#00ff00'; // Bright green border
        this.ctx.lineWidth = 2;
        this.ctx.setLineDash([4, 4]); // Dashed line
        this.ctx.strokeRect(minX, minY, width, height);

        // Reset line dash
        this.ctx.setLineDash([]);

        this.ctx.restore();
    }
}
