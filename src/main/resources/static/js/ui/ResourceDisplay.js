/**
 * ResourceDisplay - Updates resource display UI
 * Single Responsibility: Manage resource display
 */
class ResourceDisplay {
    constructor(eventBus) {
        this.eventBus = eventBus;
        this.woodElement = document.getElementById('wood');
        this.foodElement = document.getElementById('food');
        this.stoneElement = document.getElementById('stone');
        this.goldElement = document.getElementById('gold');

        this.setupEventListeners();
    }

    setupEventListeners() {
        this.eventBus.on('network:resourcesUpdate', (resources) => {
            this.update(resources);
        });
    }

    update(resources) {
        if (this.woodElement) this.woodElement.textContent = resources.wood || 0;
        if (this.foodElement) this.foodElement.textContent = resources.food || 0;
        if (this.stoneElement) this.stoneElement.textContent = resources.stone || 0;
        if (this.goldElement) this.goldElement.textContent = resources.gold || 0;
    }
}
