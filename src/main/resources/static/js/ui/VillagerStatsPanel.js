/**
 * VillagerStatsPanel - Displays villager counts (idle and working on each resource)
 * Single Responsibility: Manage villager statistics display
 */
class VillagerStatsPanel {
    constructor(eventBus) {
        this.eventBus = eventBus;
        this.idleElement = document.getElementById('villager-idle');
        this.goldElement = document.getElementById('villager-gold');
        this.stoneElement = document.getElementById('villager-stone');
        this.foodElement = document.getElementById('villager-food');
        this.woodElement = document.getElementById('villager-wood');

        this.setupEventListeners();
    }

    setupEventListeners() {
        this.eventBus.on('network:villagerStatsUpdate', (stats) => {
            this.update(stats);
        });
    }

    update(stats) {
        if (this.idleElement) this.idleElement.textContent = stats.idle || 0;
        if (this.goldElement) this.goldElement.textContent = stats.gold || 0;
        if (this.stoneElement) this.stoneElement.textContent = stats.stone || 0;
        if (this.foodElement) this.foodElement.textContent = stats.food || 0;
        if (this.woodElement) this.woodElement.textContent = stats.wood || 0;
    }
}
