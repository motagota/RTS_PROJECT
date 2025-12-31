/**
 * BuildingInfoPanel - Displays selected building information
 * Single Responsibility: Manage building info panel display
 */
class BuildingInfoPanel {
    constructor(gameState, eventBus) {
        this.gameState = gameState;
        this.eventBus = eventBus;
        this.panelElement = document.getElementById('buildingInfoContent');

        this.setupEventListeners();
    }

    setupEventListeners() {
        this.eventBus.on('selection:buildingChanged', (building) => {
            if (building) {
                this.update(building);
            }
        });
    }

    update(building) {
        // Ensure the building info content is visible
        if (this.panelElement) {
            this.panelElement.style.display = 'block';
        }

        // Hide resource node info panel if visible
        const resourceNodePanel = document.querySelector('.resource-node-info');
        if (resourceNodePanel) {
            resourceNodePanel.style.display = 'none';
        }

        const currentPlayer = this.gameState.getCurrentPlayer();
        const isOwned = currentPlayer && building.playerNumber === currentPlayer.playerSlot;
        const buildingName = this.getBuildingName(building.type);

        if (isOwned) {
            this.panelElement.innerHTML = `
                <h3>${buildingName}</h3>
                <p><strong>Owner:</strong> You</p>
                <p><strong>Position:</strong> (${building.x}, ${building.y})</p>
                ${(building.rallyPointX !== null && building.rallyPointX !== undefined) ||
                  (building.rallyX !== null && building.rallyX !== undefined) ?
                    `<p><strong>Rally Point:</strong> (${building.rallyPointX || building.rallyX}, ${building.rallyPointY || building.rallyY})</p>` :
                    '<p><strong>Rally Point:</strong> Not set</p>'}
                <div style="margin-top: 10px; padding: 8px; background: rgba(0,0,0,0.3); border-radius: 4px;">
                    <small style="color: #aaa;">💡 Use production buttons to train units | Press T to set rally point</small>
                </div>
            `;
        } else {
            const players = this.gameState.players;
            const owner = players.find(p => p.playerSlot === building.playerNumber);
            const ownerName = owner ? owner.playerName : `Player ${building.playerNumber}`;

            this.panelElement.innerHTML = `
                <h3>${buildingName}</h3>
                <p><strong>Owner:</strong> ${ownerName}</p>
                <p><strong>Position:</strong> (${building.x}, ${building.y})</p>
                <div style="margin-top: 10px; padding: 8px; background: rgba(255,0,0,0.2); border: 1px solid rgba(255,0,0,0.4); border-radius: 4px;">
                    <small style="color: #ff6666;">⚠️ Enemy building - cannot control</small>
                </div>
            `;
        }
    }

    getBuildingName(type) {
        const names = {
            'TOWN_CENTER': 'Town Center',
            'BARRACKS': 'Barracks',
            'ARCHERY_RANGE': 'Archery Range'
        };
        return names[type] || type;
    }
}
