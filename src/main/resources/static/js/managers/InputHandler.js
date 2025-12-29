/**
 * InputHandler - Handles all mouse and keyboard input
 * Single Responsibility: Process user input and emit appropriate events
 * Dependency Inversion: Depends on GameState and EventBus abstractions
 */
class InputHandler {
    constructor(gameState, eventBus, canvas) {
        this.gameState = gameState;
        this.eventBus = eventBus;
        this.canvas = canvas;

        // Bind methods to maintain 'this' context
        this.handleClick = this.handleClick.bind(this);
        this.handleDoubleClick = this.handleDoubleClick.bind(this);
        this.handleRightClick = this.handleRightClick.bind(this);
        this.handleMouseMove = this.handleMouseMove.bind(this);
        this.handleMouseDown = this.handleMouseDown.bind(this);
        this.handleMouseUp = this.handleMouseUp.bind(this);
        this.handleMouseLeave = this.handleMouseLeave.bind(this);
        this.handleKeyPress = this.handleKeyPress.bind(this);

        // Throttling
        this.lastHoverTime = 0;
        this.hoverThrottleMs = 60; // ~16 FPS

        this.setupEventListeners();
    }

    /**
     * Set up all event listeners
     */
    setupEventListeners() {
        // Mouse events
        this.canvas.addEventListener('click', this.handleClick);
        this.canvas.addEventListener('dblclick', this.handleDoubleClick);
        this.canvas.addEventListener('contextmenu', this.handleRightClick);
        this.canvas.addEventListener('mousemove', this.handleMouseMove);
        this.canvas.addEventListener('mousedown', this.handleMouseDown);
        this.canvas.addEventListener('mouseup', this.handleMouseUp);
        this.canvas.addEventListener('mouseleave', this.handleMouseLeave);

        // Keyboard events
        document.addEventListener('keydown', this.handleKeyPress);
    }

    /**
     * Remove all event listeners
     */
    cleanup() {
        this.canvas.removeEventListener('click', this.handleClick);
        this.canvas.removeEventListener('dblclick', this.handleDoubleClick);
        this.canvas.removeEventListener('contextmenu', this.handleRightClick);
        this.canvas.removeEventListener('mousemove', this.handleMouseMove);
        this.canvas.removeEventListener('mousedown', this.handleMouseDown);
        this.canvas.removeEventListener('mouseup', this.handleMouseUp);
        this.canvas.removeEventListener('mouseleave', this.handleMouseLeave);
        document.removeEventListener('keydown', this.handleKeyPress);
    }

    /**
     * Convert screen coordinates to tile coordinates
     */
    screenToTile(screenX, screenY) {
        const mapRenderer = this.gameState.getMapRenderer();
        if (!mapRenderer || !mapRenderer.mapData) {
            return null;
        }

        return CoordinateConverter.screenToTile(
            screenX,
            screenY,
            this.canvas,
            mapRenderer.tileSize,
            mapRenderer.mapData.width,
            mapRenderer.mapData.height
        );
    }

    /**
     * Get scaled canvas coordinates from mouse event
     */
    getScaledCoordinates(event) {
        const rect = this.canvas.getBoundingClientRect();
        const clickX = event.clientX - rect.left;
        const clickY = event.clientY - rect.top;

        const scaleX = this.canvas.width / rect.width;
        const scaleY = this.canvas.height / rect.height;

        return {
            x: clickX * scaleX,
            y: clickY * scaleY
        };
    }

    /**
     * Handle single click
     */
    handleClick(event) {
        // Ignore click event if a drag selection just occurred
        if (this.gameState.dragSelectionOccurred) {
            this.gameState.dragSelectionOccurred = false;
            return;
        }

        // Ignore if double-click was just handled
        if (this.gameState.doubleClickHandled) {
            this.gameState.doubleClickHandled = false;
            return;
        }

        // event.detail === 2 means this is part of a double-click sequence
        if (event.detail === 2) {
            return;
        }

        const coords = this.screenToTile(event.clientX, event.clientY);
        if (!coords || !coords.isValid) {
            return;
        }

        // Emit click event with tile coordinates
        this.eventBus.emit('input:click', {
            tileX: coords.tileX,
            tileY: coords.tileY,
            shiftKey: event.shiftKey,
            ctrlKey: event.ctrlKey
        });
    }

    /**
     * Handle double-click
     */
    handleDoubleClick(event) {
        // Set flag to prevent the second click from the double-click from being processed
        this.gameState.doubleClickHandled = true;

        const coords = this.screenToTile(event.clientX, event.clientY);
        if (!coords || !coords.isValid) {
            return;
        }

        this.eventBus.emit('input:doubleClick', {
            tileX: coords.tileX,
            tileY: coords.tileY
        });
    }

    /**
     * Handle right-click
     */
    handleRightClick(event) {
        event.preventDefault(); // Prevent context menu

        const coords = this.screenToTile(event.clientX, event.clientY);
        if (!coords || !coords.isValid) {
            return;
        }

        this.eventBus.emit('input:rightClick', {
            tileX: coords.tileX,
            tileY: coords.tileY
        });
    }

    /**
     * Handle mouse move (hover and drag)
     */
    handleMouseMove(event) {
        const mapRenderer = this.gameState.getMapRenderer();
        if (!mapRenderer || !mapRenderer.mapData) {
            return;
        }

        const scaled = this.getScaledCoordinates(event);

        // Update drag selection if dragging
        if (this.gameState.isDragging) {
            this.gameState.updateDragCurrent({ x: scaled.x, y: scaled.y });
            return;
        }

        // Throttle hover events
        const now = Date.now();
        if (now - this.lastHoverTime < this.hoverThrottleMs) {
            return;
        }
        this.lastHoverTime = now;

        // Convert to tile coordinates for hover
        const coords = this.screenToTile(event.clientX, event.clientY);
        if (!coords || !coords.isValid) {
            this.eventBus.emit('input:hover', { tileX: null, tileY: null });
            return;
        }

        this.eventBus.emit('input:hover', {
            tileX: coords.tileX,
            tileY: coords.tileY
        });
    }

    /**
     * Handle mouse down (start drag)
     */
    handleMouseDown(event) {
        // Only handle left mouse button
        if (event.button !== 0) {
            return;
        }

        const mapRenderer = this.gameState.getMapRenderer();
        if (!mapRenderer || !mapRenderer.mapData) {
            return;
        }

        const scaled = this.getScaledCoordinates(event);

        // Start drag
        this.gameState.setDragState(true, { x: scaled.x, y: scaled.y }, { x: scaled.x, y: scaled.y });
    }

    /**
     * Handle mouse up (end drag)
     */
    handleMouseUp(event) {
        // Only handle left mouse button
        if (event.button !== 0) {
            return;
        }

        if (!this.gameState.isDragging) {
            return;
        }

        const mapRenderer = this.gameState.getMapRenderer();
        if (!mapRenderer || !mapRenderer.mapData) {
            this.gameState.setDragState(false);
            return;
        }

        const scaled = this.getScaledCoordinates(event);

        // Calculate drag distance
        const dragDistance = Math.sqrt(
            Math.pow(scaled.x - this.gameState.dragStart.x, 2) +
            Math.pow(scaled.y - this.gameState.dragStart.y, 2)
        );

        // If drag distance is less than 5 pixels, treat as click
        if (dragDistance < 5) {
            this.gameState.setDragState(false);
            this.gameState.dragSelectionOccurred = false;
            return;
        }

        // Set flag to prevent click event from firing
        this.gameState.dragSelectionOccurred = true;

        // Convert to tile coordinates
        const width = mapRenderer.mapData.width;
        const height = mapRenderer.mapData.height;
        const tileSize = mapRenderer.tileSize;
        const offset = CoordinateConverter.getMapOffset(
            this.canvas,
            tileSize,
            width,
            height
        );

        const minX = Math.min(this.gameState.dragStart.x, scaled.x);
        const maxX = Math.max(this.gameState.dragStart.x, scaled.x);
        const minY = Math.min(this.gameState.dragStart.y, scaled.y);
        const maxY = Math.max(this.gameState.dragStart.y, scaled.y);

        const minTileX = Math.floor((minX - offset.offsetX) / tileSize);
        const maxTileX = Math.floor((maxX - offset.offsetX) / tileSize);
        const minTileY = Math.floor((minY - offset.offsetY) / tileSize);
        const maxTileY = Math.floor((maxY - offset.offsetY) / tileSize);

        // Emit drag end event with tile rectangle
        this.eventBus.emit('input:dragEnd', {
            minX: minTileX,
            maxX: maxTileX,
            minY: minTileY,
            maxY: maxTileY,
            shiftKey: event.shiftKey
        });

        // Clear drag state
        this.gameState.setDragState(false);
    }

    /**
     * Handle mouse leaving canvas
     */
    handleMouseLeave(event) {
        if (this.gameState.isDragging) {
            this.gameState.setDragState(false);
        }
    }

    /**
     * Handle keyboard input
     */
    handleKeyPress(event) {
        const key = event.key.toLowerCase();

        // 'h' key - select Town Center (headquarters)
        if (key === 'h') {
            this.eventBus.emit('hotkey:selectTownCenter');
            return;
        }

        // 't' key - toggle rally point setting mode
        if (key === 't') {
            this.eventBus.emit('hotkey:toggleRallyPoint');
            return;
        }

        // Production hotkeys (Q, W, E, A, S, D, Z, X, C, R, F, V)
        const productionKeys = {
            'q': 1, 'w': 2, 'e': 3,
            'a': 4, 's': 5, 'd': 6,
            'z': 7, 'x': 8, 'c': 9,
            'r': 10, 'f': 11, 'v': 12
        };

        if (productionKeys[key]) {
            this.eventBus.emit('hotkey:production', {
                slot: productionKeys[key]
            });
            return;
        }
    }
}
