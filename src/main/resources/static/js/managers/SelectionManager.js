/**
 * SelectionManager - Handles unit and building selection logic
 * Single Responsibility: Manage selection state and operations
 * Dependency Inversion: Depends on abstractions (GameState, EventBus) not concretions
 */
class SelectionManager {
    constructor(gameState, eventBus) {
        this.gameState = gameState;
        this.eventBus = eventBus;
    }

    /**
     * Check if a unit is owned by the current player
     */
    isOwnedUnit(unit) {
        const currentPlayer = this.gameState.getCurrentPlayer();
        return currentPlayer && unit.playerNumber === currentPlayer.playerSlot;
    }

    /**
     * Filter units to only include those owned by current player
     */
    filterOwnedUnits(units) {
        const currentPlayer = this.gameState.getCurrentPlayer();
        if (!currentPlayer) return [];
        return units.filter(unit => unit.playerNumber === currentPlayer.playerSlot);
    }

    /**
     * Select a single unit (clears previous selection)
     */
    selectUnit(unit) {
        this.gameState.setSelectedUnits([unit]);
        this.gameState.setSelectedBuilding(null);
        this.eventBus.emit('render:requested');
    }

    /**
     * Toggle a unit in the selection (for Shift+Click)
     */
    toggleUnitSelection(unit) {
        const selectedUnits = this.gameState.getSelectedUnits();
        const isSelected = selectedUnits.some(u => u.id === unit.id);

        // Don't allow adding enemy units to selection
        if (!isSelected && !this.isOwnedUnit(unit)) {
            return;
        }

        // If we have units selected, verify they're all owned by player
        if (!isSelected && selectedUnits.length > 0) {
            const hasEnemyUnits = selectedUnits.some(u => !this.isOwnedUnit(u));
            if (hasEnemyUnits) {
                // Clear enemy selection and start fresh with this unit
                this.selectUnit(unit);
                return;
            }
        }

        let newSelection;
        if (isSelected) {
            // Remove from selection
            newSelection = selectedUnits.filter(u => u.id !== unit.id);
        } else {
            // Add to selection
            newSelection = [...selectedUnits, unit];
        }

        this.gameState.setSelectedUnits(newSelection);
        this.eventBus.emit('render:requested');
    }

    /**
     * Select all units of a specific type owned by the player
     */
    selectAllUnitsOfType(unitType) {
        const mapRenderer = this.gameState.getMapRenderer();
        if (!mapRenderer || !mapRenderer.mapData || !mapRenderer.mapData.units) {
            return;
        }

        // Parse units
        let units = mapRenderer.mapData.units;
        if (typeof units === 'string') {
            try {
                units = JSON.parse(units);
            } catch (e) {
                return;
            }
        }

        // Find all units of the same type owned by the player
        const unitsOfType = this.filterOwnedUnits(units).filter(unit => unit.type === unitType);

        if (unitsOfType.length > 0) {
            this.gameState.setSelectedUnits(unitsOfType);
            this.gameState.setSelectedBuilding(null);
            this.eventBus.emit('render:requested');
        }
    }

    /**
     * Select units within a rectangle
     */
    selectUnitsInRectangle(minX, minY, maxX, maxY, addToSelection = false) {
        const mapRenderer = this.gameState.getMapRenderer();
        if (!mapRenderer || !mapRenderer.mapData || !mapRenderer.mapData.units) {
            return;
        }

        // Parse units
        let units = mapRenderer.mapData.units;
        if (typeof units === 'string') {
            try {
                units = JSON.parse(units);
            } catch (e) {
                return;
            }
        }

        // Find units within rectangle that are owned by current player
        const unitsInRect = units.filter(unit => {
            const displayPos = mapRenderer.unitDisplayPositions.get(unit.id) || { x: unit.x, y: unit.y };
            const inRect = displayPos.x >= minX && displayPos.x <= maxX &&
                          displayPos.y >= minY && displayPos.y <= maxY;
            return inRect && this.isOwnedUnit(unit);
        });

        if (unitsInRect.length > 0) {
            if (addToSelection) {
                // Add to existing selection (remove duplicates)
                const currentSelection = this.gameState.getSelectedUnits();
                const currentIds = new Set(currentSelection.map(u => u.id));
                const newUnits = unitsInRect.filter(u => !currentIds.has(u.id));
                this.gameState.setSelectedUnits([...currentSelection, ...newUnits]);
            } else {
                // Replace selection
                this.gameState.setSelectedUnits(unitsInRect);
            }

            // Deselect building when selecting units
            this.gameState.setSelectedBuilding(null);
            this.eventBus.emit('render:requested');
        } else if (!addToSelection) {
            // If no units found and not adding to selection, clear selection
            this.deselectAllUnits();
        }
    }

    /**
     * Deselect all units
     */
    deselectAllUnits() {
        this.gameState.setSelectedUnits([]);
        this.eventBus.emit('render:requested');
    }

    /**
     * Select a building
     */
    selectBuilding(building) {
        this.gameState.setSelectedBuilding(building);
        this.gameState.setSelectedUnits([]);

        // Update map renderer
        const mapRenderer = this.gameState.getMapRenderer();
        if (mapRenderer) {
            mapRenderer.setSelectedBuilding(building);
        }

        this.eventBus.emit('render:requested');
    }

    /**
     * Deselect building
     */
    deselectBuilding() {
        this.gameState.setSelectedBuilding(null);
        this.eventBus.emit('render:requested');
    }

    /**
     * Find building at a specific tile position
     */
    findBuildingAtPosition(tileX, tileY) {
        const mapRenderer = this.gameState.getMapRenderer();
        if (!mapRenderer || !mapRenderer.mapData || !mapRenderer.mapData.buildings) {
            return null;
        }

        // Parse buildings if needed
        let buildings = mapRenderer.mapData.buildings;
        if (typeof buildings === 'string') {
            try {
                buildings = JSON.parse(buildings);
            } catch (e) {
                return null;
            }
        }

        // Check if any building occupies this tile
        for (const building of buildings) {
            // Get building dimensions (default to 2x2 for town center, 1x1 for others)
            const width = building.width || (building.type === 'TOWN_CENTER' ? 2 : 1);
            const height = building.height || (building.type === 'TOWN_CENTER' ? 2 : 1);

            // Check if click is within the building's area
            if (tileX >= building.x && tileX < building.x + width &&
                tileY >= building.y && tileY < building.y + height) {
                return building;
            }
        }

        return null;
    }

    /**
     * Select town center (headquarters)
     */
    selectTownCenter() {
        const mapRenderer = this.gameState.getMapRenderer();
        if (!mapRenderer || !mapRenderer.mapData || !mapRenderer.mapData.buildings) {
            return;
        }

        // Parse buildings if needed
        let buildings = mapRenderer.mapData.buildings;
        if (typeof buildings === 'string') {
            try {
                buildings = JSON.parse(buildings);
            } catch (e) {
                return;
            }
        }

        const currentPlayer = this.gameState.getCurrentPlayer();
        if (!currentPlayer) return;

        // Find player's town center
        const townCenter = buildings.find(b =>
            b.type === 'TOWN_CENTER' && b.playerNumber === currentPlayer.playerSlot
        );

        if (townCenter) {
            this.selectBuilding(townCenter);
        }
    }
}
