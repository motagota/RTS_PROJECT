/**
 * ProductionPanel - Manages production button display and interaction
 * Single Responsibility: Manage production panel UI
 */
class ProductionPanel {
    constructor(gameState, eventBus, networkManager) {
        this.gameState = gameState;
        this.eventBus = eventBus;
        this.networkManager = networkManager;

        this.setupEventListeners();
        this.setupProductionButtons();
    }

    setupEventListeners() {
        this.eventBus.on('selection:buildingChanged', (building) => {
            this.updateButtons(building);
        });

        this.eventBus.on('selection:unitsChanged', () => {
            // Hide production buttons when units are selected
            this.updateButtons(null);
        });

        this.eventBus.on('hotkey:production', (data) => {
            this.handleProductionHotkey(data.slot);
        });
    }

    setupProductionButtons() {
        const productionButtons = document.querySelectorAll('.production-btn[data-action]');

        productionButtons.forEach(button => {
            button.addEventListener('click', () => {
                const action = button.getAttribute('data-action');
                this.handleProductionClick(action);
            });
        });
    }

    async handleProductionClick(action) {
        const building = this.gameState.getSelectedBuilding();
        if (!building) return;

        const currentPlayer = this.gameState.getCurrentPlayer();
        if (!currentPlayer || building.playerNumber !== currentPlayer.playerSlot) {
            return;
        }

        try {
            await this.networkManager.enqueueProduction(action);
        } catch (error) {
            alert(error.message || 'Failed to queue unit production');
        }
    }

    handleProductionHotkey(slot) {
        const buttons = document.querySelectorAll('.production-btn[data-action]');
        if (slot >= 1 && slot <= buttons.length) {
            const button = buttons[slot - 1];
            if (button && !button.disabled) {
                button.click();
            }
        }
    }

    updateButtons(building) {
        const productionButtons = document.querySelectorAll('.production-btn[data-action]');

        if (!building) {
            // Hide all production buttons
            productionButtons.forEach(btn => {
                btn.style.display = 'none';
                btn.disabled = true;
            });
            return;
        }

        const currentPlayer = this.gameState.getCurrentPlayer();
        const isOwned = currentPlayer && building.playerNumber === currentPlayer.playerSlot;

        if (!isOwned) {
            // Hide buttons for enemy buildings
            productionButtons.forEach(btn => {
                btn.style.display = 'none';
                btn.disabled = true;
            });
            return;
        }

        // Show buttons based on building type
        productionButtons.forEach(btn => {
            const action = btn.getAttribute('data-action');
            const buildingType = btn.getAttribute('data-building');

            if (buildingType === building.type) {
                btn.style.display = 'flex';
                btn.disabled = false;
            } else {
                btn.style.display = 'none';
                btn.disabled = true;
            }
        });
    }
}
