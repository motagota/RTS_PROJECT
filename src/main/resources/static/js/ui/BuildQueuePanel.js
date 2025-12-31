/**
 * BuildQueuePanel - Displays production queue for selected building
 * Single Responsibility: Manage build queue panel display
 */
class BuildQueuePanel {
    constructor(gameState, eventBus) {
        this.gameState = gameState;
        this.eventBus = eventBus;
        this.panelElement = document.getElementById('buildQueue');
        this.currentQueue = [];

        this.setupEventListeners();
    }

    setupEventListeners() {
        // Update queue when new data arrives
        this.eventBus.on('productionQueue:updated', (queue) => {
            this.currentQueue = queue || [];
            this.update();
        });

        // Update display when building selection changes
        this.eventBus.on('selection:buildingChanged', () => {
            this.update();
        });

        // Clear display when units are selected
        this.eventBus.on('selection:unitsChanged', (units) => {
            if (units && units.length > 0) {
                this.showEmpty();
            }
        });
    }

    update() {
        const building = this.gameState.getSelectedBuilding();

        if (!building) {
            this.showEmpty();
            return;
        }

        // Filter queue to only show items for the selected building
        const filteredQueue = this.currentQueue.filter(item =>
            item.buildingX === building.x &&
            item.buildingY === building.y &&
            item.buildingType === building.type
        );

        if (!filteredQueue || filteredQueue.length === 0) {
            this.showEmpty();
            return;
        }

        this.renderQueue(filteredQueue);
    }

    showEmpty() {
        if (this.panelElement) {
            this.panelElement.innerHTML = '<p class="queue-placeholder">No units in queue</p>';
        }
    }

    renderQueue(queue) {
        if (!this.panelElement) return;

        this.panelElement.innerHTML = '';

        queue.forEach(item => {
            const queueItem = document.createElement('div');
            queueItem.className = 'queue-item';

            // Get unit symbol
            const unitSymbol = this.getUnitSymbol(item.unitType);

            // Calculate time remaining
            let timeText = '';
            if (item.status === 'IN_PROGRESS' && item.completesAt) {
                const now = new Date();
                const completesAt = new Date(item.completesAt);
                const secondsRemaining = Math.max(0, Math.ceil((completesAt - now) / 1000));
                timeText = secondsRemaining + 's';
            } else if (item.status === 'QUEUED') {
                timeText = 'Queued';
            } else if (item.status === 'COMPLETED') {
                timeText = 'Done';
            }

            // Calculate progress percentage
            let progressPercent = 0;
            if (item.status === 'IN_PROGRESS' && item.startedAt && item.completesAt) {
                const now = new Date();
                const startedAt = new Date(item.startedAt);
                const completesAt = new Date(item.completesAt);
                const totalTime = completesAt - startedAt;
                const elapsed = now - startedAt;
                progressPercent = Math.min(100, Math.max(0, (elapsed / totalTime) * 100));
            } else if (item.status === 'COMPLETED') {
                progressPercent = 100;
            }

            // Add status class for styling
            if (item.status === 'QUEUED') {
                queueItem.classList.add('queued');
            }

            queueItem.innerHTML = `
                <div class="queue-item-header">
                    <span class="queue-unit-icon">${unitSymbol}</span>
                </div>
                <div class="queue-time-remaining">${timeText}</div>
            `;

            // Set progress as CSS variable for the circular progress (expects 0-100 number)
            queueItem.style.setProperty('--progress', progressPercent);

            this.panelElement.appendChild(queueItem);
        });
    }

    getUnitSymbol(unitType) {
        const symbols = {
            'VILLAGER': 'V',
            'SOLDIER': 'S',
            'ARCHER': 'A'
        };
        return symbols[unitType] || '?';
    }
}
