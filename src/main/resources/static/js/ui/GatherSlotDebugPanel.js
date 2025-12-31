/**
 * GatherSlotDebugPanel - Debug visualization for gather slots on resource nodes
 * Shows slot occupancy, assigned villagers, and visualizes slot positions
 */
class GatherSlotDebugPanel {
    constructor(gameState, eventBus, mapRenderer) {
        this.gameState = gameState;
        this.eventBus = eventBus;
        this.mapRenderer = mapRenderer;
        this.currentResourceNode = null;
        this.currentResourceId = null;
        this.slotData = null;
        this.enabled = false; // Toggle with 'G' key
        this.refreshInterval = null; // Store interval ID for cleanup

        this.setupEventListeners();
    }

    setupEventListeners() {
        // Toggle debug panel with 'G' key
        document.addEventListener('keydown', (e) => {
            if (e.key === 'g' || e.key === 'G') {
                if (!e.ctrlKey && !e.shiftKey && !e.altKey) {
                    this.enabled = !this.enabled;
                    console.log(`Gather Slot Debug Panel: ${this.enabled ? 'ENABLED' : 'DISABLED'}`);
                    if (!this.enabled) {
                        this.hide();
                    }
                }
            }
        });

        // Listen for resource node click events
        this.eventBus.on('resourceNode:clicked', (resourceNode) => {
            console.log('GatherSlotDebugPanel: resourceNode:clicked event received', resourceNode);
            console.log('Debug panel enabled:', this.enabled);
            if (this.enabled && resourceNode) {
                this.currentResourceNode = resourceNode;
                this.currentResourceId = resourceNode.id;
                console.log('Fetching slot data for resource ID:', this.currentResourceId);
                this.fetchSlotData();
                this.startRefreshInterval(); // Start refreshing when viewing a resource
            }
        });

        // Clear when clicking elsewhere
        this.eventBus.on('map:clicked', () => {
            if (this.enabled) {
                this.currentResourceNode = null;
                this.currentResourceId = null;
                this.slotData = null;
                this.stopRefreshInterval(); // Stop refreshing when panel is hidden
                this.hide();
                // Clear map visualization
                if (this.mapRenderer) {
                    this.mapRenderer.clearGatherSlots();
                }
            }
        });
    }

    /**
     * Fetch gather slot data from backend
     */
    async fetchSlotData() {
        if (!this.currentResourceId) {
            console.log('No currentResourceId, skipping fetch');
            return;
        }

        const url = `http://localhost:8080/api/games/${this.gameState.gameId}/resources/${this.currentResourceId}/slots`;
        console.log('Fetching slot data from:', url);

        try {
            const response = await fetch(url);
            console.log('Response status:', response.status);

            if (response.ok) {
                this.slotData = await response.json();
                console.log('=== SLOT DATA RECEIVED ===');
                console.log('Resource:', this.slotData.resourceNode);
                console.log('Total slots:', this.slotData.slots.length);

                // Log each slot's accessibility
                this.slotData.slots.forEach(slot => {
                    console.log(`Slot ${slot.slotIndex}: worldPos=(${slot.worldX},${slot.worldY}) accessible=${slot.accessible} occupied=${slot.occupied}`);
                });

                this.show();

                // Update map visualization
                if (this.mapRenderer && this.slotData) {
                    this.mapRenderer.setGatherSlotsToRender({
                        resourceNode: this.slotData.resourceNode,
                        slots: this.slotData.slots
                    });
                }
            } else {
                console.error('Failed to fetch slot data, status:', response.status);
                const errorText = await response.text();
                console.error('Error response:', errorText);
            }
        } catch (error) {
            console.error('Error fetching slot data:', error);
        }
    }

    /**
     * Start refresh interval to update slot data in real-time
     * Only runs when actively viewing a resource
     */
    startRefreshInterval() {
        // Clear any existing interval first
        this.stopRefreshInterval();

        // Start new interval - only refresh when panel is actively being viewed
        this.refreshInterval = setInterval(() => {
            if (this.enabled && this.currentResourceId) {
                this.fetchSlotData();
            } else {
                // Stop refreshing if conditions no longer met
                this.stopRefreshInterval();
            }
        }, 1000); // Update every 1 second (reduced from 500ms)
    }

    /**
     * Stop the refresh interval to save CPU when panel is not in use
     */
    stopRefreshInterval() {
        if (this.refreshInterval) {
            clearInterval(this.refreshInterval);
            this.refreshInterval = null;
        }
    }

    /**
     * Show the debug panel
     */
    show() {
        console.log('show() called - enabled:', this.enabled, 'hasResourceNode:', !!this.currentResourceNode, 'hasSlotData:', !!this.slotData);

        if (!this.enabled || !this.currentResourceNode || !this.slotData) {
            console.log('Skipping show - conditions not met');
            return;
        }

        // Create or get debug panel
        let panel = document.getElementById('gatherSlotDebugPanel');
        if (!panel) {
            console.log('Creating new panel');
            panel = this.createPanel();
        } else {
            console.log('Using existing panel');
        }

        // Update panel content
        console.log('Updating panel content');
        this.updatePanel(panel);
        panel.style.display = 'block';
        console.log('Panel should now be visible');
    }

    /**
     * Hide the debug panel
     */
    hide() {
        const panel = document.getElementById('gatherSlotDebugPanel');
        if (panel) {
            panel.style.display = 'none';
        }
    }

    /**
     * Create the debug panel element
     */
    createPanel() {
        const panel = document.createElement('div');
        panel.id = 'gatherSlotDebugPanel';
        panel.className = 'gather-slot-debug-panel';
        panel.style.cssText = `
            position: fixed;
            top: 120px;
            right: 20px;
            width: 350px;
            background: rgba(0, 0, 0, 0.9);
            border: 2px solid #ffd700;
            border-radius: 8px;
            padding: 15px;
            color: #fff;
            font-family: monospace;
            font-size: 12px;
            z-index: 1000;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
        `;
        document.body.appendChild(panel);
        return panel;
    }

    /**
     * Update panel content with current slot data
     */
    updatePanel(panel) {
        const { slots, resourceNode, units } = this.slotData;
        const occupiedCount = slots.filter(s => s.occupied).length;

        let html = `
            <div style="border-bottom: 2px solid #ffd700; padding-bottom: 10px; margin-bottom: 10px;">
                <h3 style="margin: 0 0 10px 0; color: #ffd700;">🔍 Gather Slot Debug</h3>
                <div style="color: #aaa; font-size: 11px;">Press 'G' to toggle</div>
            </div>

            <div style="margin-bottom: 15px;">
                <div style="color: #ffd700; margin-bottom: 5px;">Resource Node #${resourceNode.id}</div>
                <div>Type: ${this.formatResourceType(resourceNode.type)}</div>
                <div>Position: (${resourceNode.x}, ${resourceNode.y})</div>
                <div>Amount: ${resourceNode.amount}/${resourceNode.maxAmount}</div>
            </div>

            <div style="background: rgba(255, 215, 0, 0.1); padding: 10px; border-radius: 4px; margin-bottom: 15px;">
                <div style="color: #ffd700; margin-bottom: 8px; font-weight: bold;">Slot Occupancy</div>
                <div style="display: flex; justify-content: space-between; margin-bottom: 5px;">
                    <span>Total Slots:</span>
                    <span style="color: #4af;">${slots.length}</span>
                </div>
                <div style="display: flex; justify-content: space-between; margin-bottom: 5px;">
                    <span>Occupied:</span>
                    <span style="color: #f44;">${occupiedCount}</span>
                </div>
                <div style="display: flex; justify-content: space-between;">
                    <span>Available:</span>
                    <span style="color: #4f4;">${slots.length - occupiedCount}</span>
                </div>

                <div style="margin-top: 10px;">
                    <div style="display: flex; gap: 4px; flex-wrap: wrap;">
                        ${slots.map(slot => {
                            let color, bgColor, tooltip;
                            if (!slot.accessible) {
                                color = '#888';
                                bgColor = 'rgba(136, 136, 136, 0.2)';
                                tooltip = `Slot ${slot.slotIndex} - BLOCKED by adjacent resource`;
                            } else if (slot.occupied) {
                                color = '#f44';
                                bgColor = 'rgba(255, 68, 68, 0.2)';
                                tooltip = `Slot ${slot.slotIndex} - Unit #${slot.occupierUnitId}`;
                            } else {
                                color = '#4f4';
                                bgColor = 'rgba(68, 255, 68, 0.1)';
                                tooltip = `Slot ${slot.slotIndex} - Free`;
                            }
                            return `
                            <div style="
                                width: 30px;
                                height: 30px;
                                border: 2px solid ${color};
                                background: ${bgColor};
                                border-radius: 4px;
                                display: flex;
                                align-items: center;
                                justify-content: center;
                                font-size: 10px;
                                color: ${color};
                            " title="${tooltip}">
                                ${slot.slotIndex}
                            </div>
                        `}).join('')}
                    </div>
                </div>
            </div>

            <div style="max-height: 200px; overflow-y: auto;">
                <div style="color: #ffd700; margin-bottom: 8px; font-weight: bold;">Slot Details</div>
                ${slots.map(slot => {
                    let status, statusColor, bgColor, borderColor;
                    if (!slot.accessible) {
                        status = '🚫 BLOCKED';
                        statusColor = '#888';
                        bgColor = 'rgba(136, 136, 136, 0.1)';
                        borderColor = '#888';
                    } else if (slot.occupied) {
                        status = '● OCCUPIED';
                        statusColor = '#f44';
                        bgColor = 'rgba(255, 68, 68, 0.15)';
                        borderColor = '#f44';
                    } else {
                        status = '○ FREE';
                        statusColor = '#4f4';
                        bgColor = 'rgba(68, 255, 68, 0.1)';
                        borderColor = '#4f4';
                    }
                    return `
                    <div style="
                        background: ${bgColor};
                        padding: 8px;
                        margin-bottom: 6px;
                        border-radius: 4px;
                        border-left: 3px solid ${borderColor};
                    ">
                        <div style="display: flex; justify-content: space-between; margin-bottom: 3px;">
                            <span style="color: #ffd700;">Slot #${slot.slotIndex}</span>
                            <span style="color: ${statusColor};">
                                ${status}
                            </span>
                        </div>
                        <div style="font-size: 10px; color: #aaa;">
                            Angle: ${(slot.angleRadians * 180 / Math.PI).toFixed(1)}°
                        </div>
                        <div style="font-size: 10px; color: #aaa;">
                            Offset: (${slot.offsetX.toFixed(2)}, ${slot.offsetY.toFixed(2)})
                        </div>
                        <div style="font-size: 10px; color: #aaa;">
                            World Pos: (${slot.worldX}, ${slot.worldY})
                        </div>
                        ${!slot.accessible ? `
                        <div style="font-size: 10px; color: #f88; margin-top: 3px;">
                            ⚠️ Overlaps with adjacent resource
                        </div>
                        ` : ''}
                        ${slot.occupied ? `
                            <div style="margin-top: 5px; padding-top: 5px; border-top: 1px solid rgba(255, 255, 255, 0.2);">
                                <div style="color: #4af;">
                                    👤 Villager #${slot.occupierUnitId}
                                </div>
                                ${this.getVillagerInfo(slot.occupierUnitId, units)}
                            </div>
                        ` : ''}
                    </div>
                `}).join('')}
            </div>
        `;

        panel.innerHTML = html;
    }

    /**
     * Get villager information
     */
    getVillagerInfo(unitId, units) {
        const unit = units.find(u => u.id === unitId);
        if (!unit) {
            return '<div style="font-size: 10px; color: #f44;">Unit not found</div>';
        }

        return `
            <div style="font-size: 10px; margin-top: 3px;">
                <div>Position: (${unit.x}, ${unit.y})</div>
                <div>State: <span style="color: #4af;">${unit.gatherState}</span></div>
                ${unit.carryingAmount > 0 ? `
                    <div>Carrying: ${unit.carryingAmount}/${unit.carryCapacity} ${unit.carryingResourceType}</div>
                ` : ''}
                ${unit.movementDelayTicks > 0 ? `
                    <div style="color: #fa4;">Delay: ${unit.movementDelayTicks} ticks</div>
                ` : ''}
            </div>
        `;
    }

    /**
     * Format resource type
     */
    formatResourceType(type) {
        const types = {
            'GOLD': '⭐ Gold',
            'STONE': '🪨 Stone',
            'BERRIES': '🫐 Berries',
            'FORAGE': '🫐 Berries',
            'TREE': '🌲 Tree'
        };
        return types[type.toUpperCase()] || type;
    }
}
