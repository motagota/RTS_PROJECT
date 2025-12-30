/**
 * UnitInfoPanel - Displays selected unit/units information
 * Single Responsibility: Manage unit info panel display
 */
class UnitInfoPanel {
    constructor(gameState, eventBus) {
        this.gameState = gameState;
        this.eventBus = eventBus;
        this.panelElement = document.getElementById('buildingInfoContent');

        this.setupEventListeners();
    }

    setupEventListeners() {
        this.eventBus.on('selection:unitsChanged', () => {
            this.update();
        });

        this.eventBus.on('selection:buildingChanged', (building) => {
            if (!building) {
                this.update();
            }
        });

        // Update when units data changes (for real-time gathering status, carrying amount, etc.)
        this.eventBus.on('network:unitsUpdate', () => {
            // Only update if we have units selected
            const selectedUnits = this.gameState.getSelectedUnits();
            if (selectedUnits && selectedUnits.length > 0) {
                this.update();
            }
        });
    }

    update() {
        const selectedUnits = this.gameState.getSelectedUnits();
        const selectedBuilding = this.gameState.getSelectedBuilding();

        // Don't update if a building is selected - let BuildingInfoPanel handle it
        if (selectedBuilding) {
            return;
        }

        if (selectedUnits.length === 0) {
            this.showPlaceholder();
            return;
        }

        if (selectedUnits.length === 1) {
            this.showSingleUnit(selectedUnits[0]);
        } else {
            this.showMultipleUnits(selectedUnits);
        }
    }

    showPlaceholder() {
        this.panelElement.innerHTML = '<p class="info-placeholder">Select a building or unit to see details</p>';
    }

    showSingleUnit(unit) {
        const currentPlayer = this.gameState.getCurrentPlayer();
        const isOwned = currentPlayer && unit.playerNumber === currentPlayer.playerSlot;
        const unitTypeName = unit.type === 'VILLAGER' ? 'Villager' : 'Soldier';
        const healthPercent = Math.round((unit.health / unit.maxHealth) * 100);

        // Determine status based on gather state and movement
        let status = 'Idle';
        if (unit.gatherState) {
            switch (unit.gatherState) {
                case 'MOVING_TO_RESOURCE':
                    status = `Moving to resource (${unit.targetX}, ${unit.targetY})`;
                    break;
                case 'GATHERING':
                    status = `Gathering ${unit.carryingResourceType || 'resources'}`;
                    break;
                case 'MOVING_TO_DROPOFF':
                    status = `Returning to dropoff (${unit.targetX}, ${unit.targetY})`;
                    break;
                case 'DEPOSITING':
                    status = 'Depositing resources';
                    break;
                default:
                    if (unit.targetX !== null && unit.targetX !== undefined) {
                        status = `Moving to (${unit.targetX}, ${unit.targetY})`;
                    }
            }
        } else if (unit.targetX !== null && unit.targetX !== undefined) {
            status = `Moving to (${unit.targetX}, ${unit.targetY})`;
        }

        // Show carrying resources indicator
        let carryingIndicator = '';
        if (unit.carryingAmount && unit.carryingAmount > 0) {
            const resourceColors = {
                'GOLD': '#FFD700',
                'STONE': '#808080',
                'BERRIES': '#FF1493',
                'TREE': '#8B4513'
            };
            const color = resourceColors[unit.carryingResourceType] || '#FFFFFF';
            carryingIndicator = `
                <p><strong>Carrying:</strong> <span style="color: ${color};">${unit.carryingAmount}/${unit.carryCapacity} ${unit.carryingResourceType}</span></p>
            `;
        }

        if (isOwned) {
            this.panelElement.innerHTML = `
                <h3>${unitTypeName}</h3>
                <p><strong>Owner:</strong> You</p>
                <p><strong>Position:</strong> (${unit.x}, ${unit.y})</p>
                <p><strong>Health:</strong> ${unit.health}/${unit.maxHealth} (${healthPercent}%)</p>
                <p><strong>Status:</strong> ${status}</p>
                ${carryingIndicator}
                <div style="margin-top: 10px; padding: 8px; background: rgba(0,0,0,0.3); border-radius: 4px;">
                    <small style="color: #aaa;">💡 Right-click to move | Shift+Click to multi-select | Double-click to select all of type</small>
                </div>
            `;
        } else {
            const players = this.gameState.players;
            const owner = players.find(p => p.playerSlot === unit.playerNumber);
            const ownerName = owner ? owner.playerName : `Player ${unit.playerNumber}`;

            this.panelElement.innerHTML = `
                <h3>${unitTypeName}</h3>
                <p><strong>Owner:</strong> ${ownerName}</p>
                <p><strong>Position:</strong> (${unit.x}, ${unit.y})</p>
                <div style="margin-top: 10px; padding: 8px; background: rgba(255,0,0,0.2); border: 1px solid rgba(255,0,0,0.4); border-radius: 4px;">
                    <small style="color: #ff6666;">⚠️ Enemy unit - cannot control</small>
                </div>
            `;
        }
    }

    showMultipleUnits(units) {
        const villagerCount = units.filter(u => u.type === 'VILLAGER').length;
        const soldierCount = units.filter(u => u.type === 'SOLDIER').length;

        let unitBreakdown = '';
        if (villagerCount > 0) unitBreakdown += `${villagerCount} Villager${villagerCount > 1 ? 's' : ''}<br>`;
        if (soldierCount > 0) unitBreakdown += `${soldierCount} Soldier${soldierCount > 1 ? 's' : ''}`;

        this.panelElement.innerHTML = `
            <h3>${units.length} Units Selected</h3>
            <p><strong>Owner:</strong> You</p>
            <p style="margin-top: 8px;">${unitBreakdown}</p>
            <div style="margin-top: 10px; padding: 8px; background: rgba(0,0,0,0.3); border-radius: 4px;">
                <small style="color: #aaa;">💡 Right-click to move group | Shift+Click to modify selection</small>
            </div>
        `;
    }
}
