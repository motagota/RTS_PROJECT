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
    }

    update() {
        const selectedUnits = this.gameState.getSelectedUnits();

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

        if (isOwned) {
            this.panelElement.innerHTML = `
                <h3>${unitTypeName}</h3>
                <p><strong>Owner:</strong> You</p>
                <p><strong>Position:</strong> (${unit.x}, ${unit.y})</p>
                <p><strong>Health:</strong> ${unit.health}/${unit.maxHealth} (${healthPercent}%)</p>
                <p><strong>Status:</strong> ${unit.targetX !== null && unit.targetX !== undefined ? `Moving to (${unit.targetX}, ${unit.targetY})` : 'Idle'}</p>
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
