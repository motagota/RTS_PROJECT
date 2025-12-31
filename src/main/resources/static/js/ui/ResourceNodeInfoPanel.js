/**
 * ResourceNodeInfoPanel - Displays hovered resource node information
 * Single Responsibility: Show resource node details
 */
class ResourceNodeInfoPanel {
    constructor(gameState, eventBus) {
        this.gameState = gameState;
        this.eventBus = eventBus;
        this.panel = document.querySelector('.resource-node-info');
        this.buildingInfoContent = document.querySelector('.building-info-content');
        this.currentResourceNode = null; // Track currently displayed resource node

        this.setupEventListeners();
        this.startRefreshInterval();
    }

    setupEventListeners() {
        // Listen for resource node hover events
        this.eventBus.on('resourceNode:hovered', (resourceNode) => {
            if (resourceNode) {
                this.currentResourceNode = resourceNode;
                this.show(resourceNode);
            } else {
                this.currentResourceNode = null;
                this.hide();
            }
        });
    }

    /**
     * Start interval to refresh resource node display
     * This updates the panel in real-time as resources are gathered
     */
    startRefreshInterval() {
        setInterval(() => {
            if (this.currentResourceNode) {       
                this.refreshCurrentNode();
            }
        }, 500); 
    }

    /**
     * Refresh the currently displayed resource node
     */
    async refreshCurrentNode() {
        if (!this.currentResourceNode) return;

        try {
            const response = await fetch(
                `http://localhost:8080/api/games/${this.gameState.gameId}/resources`
            );
            if (!response.ok) return;

            const resourceNodes = await response.json();

            const updatedNode = resourceNodes.find(
                node => node.x === this.currentResourceNode.x &&
                        node.y === this.currentResourceNode.y
            );

            if (updatedNode) {
                this.currentResourceNode = updatedNode;
                this.show(updatedNode);
            } else {
                this.hide();
            }
        } catch (error) {
        }
    }

    /**
     * Show resource node information
     */
    show(resourceNode) {
        if (!this.panel) return;

        const percentRemaining = resourceNode.percentageRemaining ||
            Math.floor((resourceNode.amount / resourceNode.maxAmount) * 100);

        this.panel.innerHTML = `
            <h3>${this.formatResourceType(resourceNode.type)}</h3>
            <div class="resource-info">
                <div>Amount: ${resourceNode.amount} / ${resourceNode.maxAmount}</div>
                <div class="resource-bar-container">
                    <div class="resource-bar" style="width: ${percentRemaining}%"></div>
                </div>
                <div>${percentRemaining}% remaining</div>
            </div>
        `;

        this.panel.style.display = 'block';

        // Hide the placeholder content
        if (this.buildingInfoContent) {
            this.buildingInfoContent.style.display = 'none';
        }
    }

    /**
     * Hide resource node information
     */
    hide() {
        if (!this.panel) return;
        this.panel.style.display = 'none';
        this.panel.innerHTML = '';
        
        if (this.buildingInfoContent) {
            this.buildingInfoContent.style.display = 'block';
        }
    }

    /**
     * Format resource type for display
     */
    formatResourceType(type) {
        switch (type.toUpperCase()) {
            case 'GOLD':
                return '⭐ Gold Mine';
            case 'STONE':
                return '🪨 Stone Deposit';
            case 'BERRIES':
            case 'FORAGE':
                return '🫐 Berry Bush';
            case 'TREE':
                return '🌲 Tree';
            default:
                return type;
        }
    }
}
