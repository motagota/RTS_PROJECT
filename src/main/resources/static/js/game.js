// Game State
const API_BASE_URL = 'http://localhost:8080/api/games';
const WS_BASE_URL = 'http://localhost:8080/ws';

let gameId = null;
let playerName = null;
let stompClient = null;
let heartbeatInterval = null;
let loadingTickInterval = null;
let queueUpdateInterval = null;
let currentGame = null;
let gamePlayers = [];
let disconnectedPlayers = [];
let mapRenderer = null;
let selectedBuilding = null; // Currently selected building (Town Center, etc.)
let hoveredBuilding = null; // Building under mouse cursor
let settingRallyPoint = false; // Whether user is in rally point setting mode

// DOM Elements
const loadingScreen = document.getElementById('loadingScreen');
const gameScreen = document.getElementById('gameScreen');
const mapName = document.getElementById('mapName');
const playerLoadingList = document.getElementById('playerLoadingList');
const loadingProgress = document.getElementById('loadingProgress');
const loadingText = document.getElementById('loadingText');
const gameTip = document.getElementById('gameTip');

// Game Tips
const tips = [
    "Build your base quickly to gain an early advantage!",
    "Scout the map to find resource deposits early.",
    "Don't forget to upgrade your units for better combat effectiveness.",
    "A strong economy is the foundation of a powerful army.",
    "Use terrain to your advantage in battles.",
    "Keep your units grouped for better tactical control.",
    "Expand your base to multiple locations for resource diversity.",
    "Tech rushing can catch opponents off guard!"
];

// Initialize
window.addEventListener('load', init);

function init() {
    // Get game ID from URL
    const urlParams = new URLSearchParams(window.location.search);
    gameId = urlParams.get('id');
    playerName = sessionStorage.getItem('playerName');

    if (!gameId || !playerName) {
        alert('Invalid game session. Redirecting to lobby...');
        window.location.href = 'lobby.html';
        return;
    }

    // Show random tip
    gameTip.textContent = tips[Math.floor(Math.random() * tips.length)];

    // Connect to game
    connectToGame();
}

async function connectToGame() {
    try {
        // Load game data
        const response = await fetch(`${API_BASE_URL}/${gameId}`);
        if (!response.ok) throw new Error('Failed to load game');

        currentGame = await response.json();
        mapName.textContent = currentGame.mapName;

        // Load players
        await loadPlayers();

        // Initialize map renderer
        const gameCanvas = document.getElementById('gameCanvas');
        mapRenderer = new MapRenderer(gameCanvas);

        // Load map data in background
        loadMapData();

        // Connect WebSocket
        connectWebSocket();

        // Start heartbeat
        startHeartbeat();

        // Start loading tick to poll for game status
        startLoadingTick();

        // Update player status to LOADING
        await updatePlayerStatus('LOADING');

        // Simulate loading progress
        simulateLoading();

    } catch (error) {
        console.error('Error connecting to game:', error);
        alert('Failed to connect to game. Redirecting to lobby...');
        window.location.href = 'lobby.html';
    }
}

async function loadPlayers() {
    try {
        const response = await fetch(`${API_BASE_URL}/${gameId}/players`);
        if (!response.ok) throw new Error('Failed to load players');

        gamePlayers = await response.json();
        updatePlayerLoadingList();

        // Update resources for current player
        const myPlayer = gamePlayers.find(p => p.playerName === playerName);
        if (myPlayer && myPlayer.resources) {
            updateResourceDisplay(myPlayer.resources);
        }
    } catch (error) {
        console.error('Error loading players:', error);
    }
}

async function loadMapData() {
    try {
        console.log('Loading map data for game:', gameId);
        await mapRenderer.loadMap(gameId);
        console.log('Map data loaded successfully');
    } catch (error) {
        console.error('Error loading map data:', error);
        // Continue without map - can be loaded later
    }
}

function updatePlayerLoadingList() {
    playerLoadingList.innerHTML = '';

    gamePlayers.forEach(player => {
        if (player.playerStatus === 'BOOTED') {
            return; // Skip booted players
        }

        const isDisconnected = player.playerStatus === 'DISCONNECTED' && !player.isConnected;
        const disconnectedInfo = disconnectedPlayers.find(dp => dp.playerName === player.playerName);

        const playerDiv = document.createElement('div');
        let className = 'player-loading-item';
        if (player.playerStatus === 'READY') className += ' ready';
        if (isDisconnected) className += ' disconnected';

        playerDiv.className = className;
        playerDiv.dataset.playerName = player.playerName;

        let statusText = getStatusText(player.playerStatus);
        let gracePeriodHtml = '';

        // Calculate grace period remaining if disconnected
        if (isDisconnected && disconnectedInfo && disconnectedInfo.disconnectedAt) {
            const disconnectedAt = new Date(disconnectedInfo.disconnectedAt);
            const gracePeriodMs = (disconnectedInfo.gracePeriodSeconds || 30) * 1000;
            const expiresAt = new Date(disconnectedAt.getTime() + gracePeriodMs);
            const now = new Date();
            const remainingMs = Math.max(0, expiresAt - now);
            const remainingSec = Math.ceil(remainingMs / 1000);

            if (remainingSec > 0) {
                statusText = `Disconnected - Reconnect in ${remainingSec}s`;
                gracePeriodHtml = `<div class="grace-period-bar"><div class="grace-period-progress" style="width: ${(remainingSec / disconnectedInfo.gracePeriodSeconds) * 100}%"></div></div>`;
            } else {
                statusText = 'Grace period expired - Booting...';
            }
        }

        playerDiv.innerHTML = `
            <div class="player-color" style="background: ${player.teamColor}"></div>
            <div class="player-loading-info">
                <div class="player-loading-name">
                    ${player.playerName}${player.playerName === playerName ? ' (You)' : ''}
                    ${player.isHost ? ' - HOST' : ''}
                </div>
                <div class="player-loading-status">${statusText}</div>
                ${gracePeriodHtml}
            </div>
            <div class="heartbeat-indicator ${player.isConnected ? 'active' : ''}"></div>
        `;

        playerLoadingList.appendChild(playerDiv);
    });
}

function getStatusText(status) {
    switch (status) {
        case 'LOADING': return 'Loading...';
        case 'READY': return 'Ready!';
        case 'PLAYING': return 'In Game';
        case 'DISCONNECTED': return 'Disconnected';
        case 'BOOTED': return 'Removed';
        default: return status;
    }
}

function connectWebSocket() {
    const socket = new SockJS(WS_BASE_URL);
    stompClient = Stomp.over(socket);

    stompClient.connect({}, function(frame) {
        console.log('Connected to game WebSocket');

        // Subscribe to game updates
        stompClient.subscribe(`/topic/game/${gameId}`, function(message) {
            const update = JSON.parse(message.body);
            handleGameUpdate(update);
        });
    }, function(error) {
        console.error('WebSocket connection error:', error);
    });
}

function handleGameUpdate(update) {
    switch (update.type) {
        case 'PLAYER_STATUS':
            updatePlayerInList(update.playerName, update.status);
            break;
        case 'PLAYER_DISCONNECTED':
            if (update.players) {
                disconnectedPlayers = update.players;
                loadPlayers(); // Refresh player list
            }
            break;
        case 'PLAYERS_BOOTED':
            loadPlayers(); // Refresh to remove booted players
            break;
        case 'GAME_STATUS':
            if (update.status === 'ACTIVE') {
                transitionToGame();
            } else if (update.status === 'CANCELLED') {
                alert('Game has been cancelled due to player disconnection. Redirecting to lobby...');
                window.location.href = 'lobby.html';
            }
            break;
        case 'RESOURCES_UPDATE':
            if (update.playerName === playerName && update.resources) {
                updateResourceDisplay(update.resources);
            }
            break;
        case 'PRODUCTION_QUEUE_UPDATE':
            if (update.playerName === playerName) {
                loadProductionQueue();
            }
            break;
        case 'MAP_UPDATE':
            // Reload map when units spawn or map changes
            if (mapRenderer) {
                mapRenderer.loadMap(gameId);
            }
            break;
    }
}

function updatePlayerInList(playerName, status) {
    // Update in gamePlayers array
    const player = gamePlayers.find(p => p.playerName === playerName);
    if (player) {
        player.playerStatus = status;
        updatePlayerLoadingList();
    }
}

function updateResourceDisplay(resources) {
    document.getElementById('wood').textContent = resources.wood;
    document.getElementById('food').textContent = resources.food;
    document.getElementById('stone').textContent = resources.stone;
    document.getElementById('gold').textContent = resources.gold;
}

function startHeartbeat() {
    // Send heartbeat every 3 seconds
    heartbeatInterval = setInterval(async () => {
        try {
            await fetch(`${API_BASE_URL}/${gameId}/heartbeat`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ playerName })
            });
        } catch (error) {
            console.error('Heartbeat error:', error);
        }
    }, 3000);
}

function startLoadingTick() {
    // Poll server for game status every 1 second
    loadingTickInterval = setInterval(async () => {
        try {
            const res = await fetch(`${API_BASE_URL}/${gameId}/loading-tick`);
            const data = await res.json();

            // Update disconnected players info
            if (data.disconnectedPlayers) {
                disconnectedPlayers = data.disconnectedPlayers;
                updatePlayerLoadingList();
            }

            // Handle game status changes
            if (data.gameStatus === 'ACTIVE') {
                clearInterval(loadingTickInterval);
                transitionToGame();
            } else if (data.gameStatus === 'CANCELLED') {
                clearInterval(loadingTickInterval);
                alert('Game cancelled - disconnected players did not reconnect in time');
                window.location.href = 'lobby.html';
            }
        } catch (error) {
            console.error('Loading tick error:', error);
        }
    }, 1000);
}

async function updatePlayerStatus(status) {
    try {
        await fetch(`${API_BASE_URL}/${gameId}/player-status`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ playerName, status })
        });
    } catch (error) {
        console.error('Error updating player status:', error);
    }
}

function simulateLoading() {
    let progress = 0;
    const loadingSteps = [
        { percent: 20, text: 'Loading map assets...' },
        { percent: 40, text: 'Initializing game engine...' },
        { percent: 60, text: 'Syncing game state...' },
        { percent: 80, text: 'Waiting for all players...' },
        { percent: 100, text: 'Ready to start!' }
    ];

    let currentStep = 0;

    const loadingInterval = setInterval(() => {
        if (currentStep < loadingSteps.length) {
            const step = loadingSteps[currentStep];
            progress = step.percent;
            loadingProgress.style.width = progress + '%';
            loadingText.textContent = step.text;

            if (progress === 100) {
                setTimeout(async () => {
                    await updatePlayerStatus('READY');
                    checkIfAllReady();
                }, 500);
            }

            currentStep++;
        } else {
            clearInterval(loadingInterval);
        }
    }, 1500);
}

function checkIfAllReady() {
    // This will be handled by the game server
    // When all players are ready, server will broadcast GAME_STATUS: ACTIVE
}

function transitionToGame() {
    loadingScreen.style.display = 'none';
    gameScreen.style.display = 'flex';

    // Initialize game canvas
    initializeGame();
}

function initializeGame() {
    const canvas = document.getElementById('gameCanvas');



    // Render the map if loaded
    if (mapRenderer && mapRenderer.mapData) {      
        mapRenderer.render();
        console.log('Map rendered on main canvas');

        // Render minimap
        const miniMapCanvas = document.getElementById('miniMap');
        if (miniMapCanvas) {
            mapRenderer.renderMinimap(miniMapCanvas);
            console.log('Map rendered on minimap');
        }
    } else {
        // Draw placeholder if map not loaded
        const ctx = canvas.getContext('2d');
        ctx.fillStyle = '#1a1a1a';
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        ctx.fillStyle = '#00d4ff';
        ctx.font = '48px Arial';
        ctx.textAlign = 'center';
        ctx.fillText('Loading Map...', canvas.width / 2, canvas.height / 2);
    }

    // Update game player list in sidebar
    updateGamePlayerList();

    // Start game timer
    startGameTimer();

    // Setup input handlers for selection
    setupInputHandlers();

    // Setup production button handlers
    setupProductionButtons();

    // Start production queue updates
    startQueueUpdates();
}

/**
 * Start periodic updates of the production queue
 */
function startQueueUpdates() {
    // Load immediately
    loadProductionQueue();

    // Update every second to refresh progress bars and timers
    queueUpdateInterval = setInterval(() => {
        loadProductionQueue();
    }, 1000);
}

/**
 * Setup mouse and keyboard input handlers for game interactions
 */
function setupInputHandlers() {
    const canvas = document.getElementById('gameCanvas');

    if (!canvas) {
        console.error('Canvas element not found!');
        return;
    }

    // Mouse click handler for selecting buildings
    canvas.addEventListener('click', handleCanvasClick);

    // Mouse move handler for hover effects
    canvas.addEventListener('mousemove', handleCanvasHover);

    // Keyboard handler for hotkeys
    document.addEventListener('keydown', handleKeyPress);
}

/**
 * Setup production button click handlers
 */
function setupProductionButtons() {
    const productionButtons = document.querySelectorAll('.production-btn');

    productionButtons.forEach((button, index) => {
        // Remove any existing listeners by cloning the button
        const newButton = button.cloneNode(true);
        button.parentNode.replaceChild(newButton, button);

        // Add the click listener to the new button
        newButton.addEventListener('click', () => {
            handleProductionButtonClick(index + 1);
        });
    });
}

/**
 * Handle production button clicks
 */
function handleProductionButtonClick(action) {
    if (!selectedBuilding) {
        console.log('No building selected');
        return;
    }

    // Check if the selected building belongs to the player
    const myPlayer = gamePlayers.find(p => p.playerName === playerName);
    if (!myPlayer || selectedBuilding.playerNumber !== myPlayer.playerSlot) {
        console.log('Cannot produce from enemy building');
        return;
    }

    // Action 12 is the rally point button
    if (action === 12) {
        toggleRallyPointMode();
        return;
    }

    // For Town Center, action 1 (Q key) produces a villager
    if (selectedBuilding.type === 'HEADQUARTERS' && action === 1) {
        produceVillager();
    }
}

/**
 * Produce a villager from the selected Town Center
 */
async function produceVillager() {
    console.log('Producing villager from TC at', selectedBuilding.x, selectedBuilding.y);

    try {
        const response = await fetch(`${API_BASE_URL}/${gameId}/produce-unit`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                playerName: playerName,
                buildingX: selectedBuilding.x,
                buildingY: selectedBuilding.y,
                buildingType: selectedBuilding.type,
                unitType: 'VILLAGER'
            })
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Failed to produce villager');
        }

        const result = await response.json();
        console.log('Villager production started:', result);

        // Resources will be updated via WebSocket
        // Refresh the build queue
        await loadProductionQueue();
    } catch (error) {
        console.error('Error producing villager:', error);
        alert(error.message);
    }
}

/**
 * Load production queue from server
 */
async function loadProductionQueue() {
    try {
        const response = await fetch(`${API_BASE_URL}/${gameId}/production-queue?playerName=${encodeURIComponent(playerName)}`);
        if (!response.ok) throw new Error('Failed to load production queue');

        const queue = await response.json();

        // Update the display for selected building
        updateBuildQueueDisplay(queue);

        // Pass queue data to renderer for production indicators
        if (mapRenderer) {
            mapRenderer.setProductionQueue(queue);
            mapRenderer.render();
        }
    } catch (error) {
        console.error('Error loading production queue:', error);
    }
}

/**
 * Update the build queue UI with server data
 */
function updateBuildQueueDisplay(queue) {
    const buildQueueContainer = document.getElementById('buildQueue');

    // Filter queue to only show items for the selected building
    let filteredQueue = queue;
    if (selectedBuilding) {
        filteredQueue = queue.filter(item =>
            item.buildingX === selectedBuilding.x &&
            item.buildingY === selectedBuilding.y &&
            item.buildingType === selectedBuilding.type
        );
    } else {
        // No building selected, show nothing
        buildQueueContainer.innerHTML = '<p class="queue-placeholder">No units in queue</p>';
        return;
    }

    if (!filteredQueue || filteredQueue.length === 0) {
        buildQueueContainer.innerHTML = '<p class="queue-placeholder">No units in queue</p>';
        return;
    }

    buildQueueContainer.innerHTML = '';

    filteredQueue.forEach(item => {
        const queueItem = document.createElement('div');
        queueItem.className = 'queue-item';

        let progress = 0;
        let timeText = '';

        if (item.status === 'IN_PROGRESS' && item.startedAt && item.completesAt) {
            // Calculate progress for active production
            const now = new Date();
            const startedAt = new Date(item.startedAt);
            const completesAt = new Date(item.completesAt);
            const totalTime = completesAt - startedAt;
            const elapsed = now - startedAt;
            progress = Math.min(100, Math.max(0, (elapsed / totalTime) * 100));
            const remainingSeconds = Math.max(0, Math.ceil((completesAt - now) / 1000));
            timeText = `${remainingSeconds}s`;
        } else if (item.status === 'QUEUED') {
            // Queued items show no progress
            progress = 0;
            timeText = 'Queued';
            queueItem.classList.add('queued');
        }

        // Set CSS custom property for circular progress
        queueItem.style.setProperty('--progress', progress);

        // Get unit symbol
        const unitSymbol = item.unitType === 'VILLAGER' ? 'V' : 'S';

        queueItem.innerHTML = `
            <div class="queue-item-header">
                <span class="queue-unit-icon">${unitSymbol}</span>
            </div>
            <div class="queue-time-remaining">${timeText}</div>
        `;

        buildQueueContainer.appendChild(queueItem);
    });
}

/**
 * Handle mouse clicks on the canvas
 */
function handleCanvasClick(event) {
    if (!mapRenderer || !mapRenderer.mapData) {
        return;
    }

    const canvas = event.target;
    const rect = canvas.getBoundingClientRect();
    const clickX = event.clientX - rect.left;
    const clickY = event.clientY - rect.top;

    // Scale click coordinates if canvas is displayed at different size
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;
    const scaledClickX = clickX * scaleX;
    const scaledClickY = clickY * scaleY;

    // Calculate map offset (same as in render method)
    const width = mapRenderer.mapData.width;
    const height = mapRenderer.mapData.height;
    const tileSize = mapRenderer.tileSize;
    const offsetX = Math.max(0, (canvas.width - width * tileSize) / 2);
    const offsetY = Math.max(0, (canvas.height - height * tileSize) / 2);

    // Convert click position to tile coordinates
    const tileX = Math.floor((scaledClickX - offsetX) / tileSize);
    const tileY = Math.floor((scaledClickY - offsetY) / tileSize);

    // Check if click is within map bounds
    if (tileX < 0 || tileY < 0 || tileX >= width || tileY >= height) {
        return;
    }

    // If in rally point setting mode, set rally point and exit mode
    if (settingRallyPoint && selectedBuilding) {
        setRallyPoint(tileX, tileY);
        settingRallyPoint = false;
        canvas.style.cursor = 'default';

        // Remove active state from rally point button
        const rallyBtn = document.querySelector('.rally-point-btn');
        if (rallyBtn) rallyBtn.classList.remove('active');

        return;
    }

    // Check if a building was clicked
    const clickedBuilding = findBuildingAtPosition(tileX, tileY);

    if (clickedBuilding) {
        selectBuilding(clickedBuilding);
    } else {
        // Clicked on empty space - deselect
        deselectBuilding();
    }
}

/**
 * Handle mouse hover on the canvas
 */
function handleCanvasHover(event) {
    if (!mapRenderer || !mapRenderer.mapData) return;

    const canvas = event.target;
    const rect = canvas.getBoundingClientRect();
    const mouseX = event.clientX - rect.left;
    const mouseY = event.clientY - rect.top;

    // Scale mouse coordinates if canvas is displayed at different size
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;
    const scaledMouseX = mouseX * scaleX;
    const scaledMouseY = mouseY * scaleY;

    // Calculate map offset (same as in render method)
    const width = mapRenderer.mapData.width;
    const height = mapRenderer.mapData.height;
    const tileSize = mapRenderer.tileSize;
    const offsetX = Math.max(0, (canvas.width - width * tileSize) / 2);
    const offsetY = Math.max(0, (canvas.height - height * tileSize) / 2);

    // Convert mouse position to tile coordinates
    const tileX = Math.floor((scaledMouseX - offsetX) / tileSize);
    const tileY = Math.floor((scaledMouseY - offsetY) / tileSize);

    // Check if mouse is within map bounds
    if (tileX < 0 || tileY < 0 || tileX >= width || tileY >= height) {
        if (hoveredBuilding !== null) {
            hoveredBuilding = null;
            updateHoverState();
        }
        return;
    }

    // Check if hovering over a building
    const building = findBuildingAtPosition(tileX, tileY);
    if (building !== hoveredBuilding) {
        hoveredBuilding = building;
        updateHoverState();
    }
}

/**
 * Update the hover state and re-render
 */
function updateHoverState() {
    if (mapRenderer) {
        mapRenderer.setHoveredBuilding(hoveredBuilding);
        mapRenderer.render();

        // Update minimap as well
        const miniMapCanvas = document.getElementById('miniMap');
        if (miniMapCanvas) {
            mapRenderer.renderMinimap(miniMapCanvas);
        }
    }
}

/**
 * Handle keyboard input
 */
function handleKeyPress(event) {
    // 'h' key - select Town Center (headquarters)
    if (event.key === 'h' || event.key === 'H') {
        selectTownCenter();
        return;
    }

    // 't' key - toggle rally point setting mode
    if (event.key === 't' || event.key === 'T') {
        if (selectedBuilding) {
            settingRallyPoint = !settingRallyPoint;

            // Update UI to show rally point mode
            const canvas = document.getElementById('gameCanvas');
            if (settingRallyPoint) {
                canvas.style.cursor = 'crosshair';
                console.log('Rally point mode enabled. Click on the map to set rally point.');
            } else {
                canvas.style.cursor = 'default';
                console.log('Rally point mode disabled.');
            }
        } else {
            console.log('Select a building first before setting a rally point.');
        }
        return;
    }

    // Production hotkeys (Q, W, E, A, S, D, Z, X, C, R, F, V)
    const productionKeys = {
        'q': 1, 'Q': 1,
        'w': 2, 'W': 2,
        'e': 3, 'E': 3,
        'a': 4, 'A': 4,
        's': 5, 'S': 5,
        'd': 6, 'D': 6,
        'z': 7, 'Z': 7,
        'x': 8, 'X': 8,
        'c': 9, 'C': 9,
        'r': 10, 'R': 10,
        'f': 11, 'F': 11,
        'v': 12, 'V': 12  // Rally point
    };

    if (productionKeys[event.key]) {
        handleProductionButtonClick(productionKeys[event.key]);
    }
}

/**
 * Find building at the given tile position
 */
function findBuildingAtPosition(tileX, tileY) {
    if (!mapRenderer || !mapRenderer.mapData || !mapRenderer.mapData.buildings) {
        return null;
    }

    // Check if any building occupies this tile
    const buildings = mapRenderer.mapData.buildings;
    for (const building of buildings) {
        // Get building dimensions (default to 1x1 if not specified)
        const width = building.width || 1;
        const height = building.height || 1;

        // Check if click is within the building's area
        if (tileX >= building.x && tileX < building.x + width &&
            tileY >= building.y && tileY < building.y + height) {
            return building;
        }
    }

    return null;
}

/**
 * Select the player's Town Center using 'h' hotkey
 */
function selectTownCenter() {
    if (!mapRenderer || !mapRenderer.mapData || !mapRenderer.mapData.buildings) {
        return;
    }

    // Find the player's town center
    const myPlayer = gamePlayers.find(p => p.playerName === playerName);
    if (!myPlayer) return;

    const myPlayerNumber = myPlayer.playerSlot;
    const buildings = mapRenderer.mapData.buildings;

    // Find headquarters belonging to this player
    const townCenter = buildings.find(b =>
        b.type === 'HEADQUARTERS' && b.playerNumber === myPlayerNumber
    );

    if (townCenter) {
        selectBuilding(townCenter);
    }
}

/**
 * Select a building and update the renderer
 */
function selectBuilding(building) {
    selectedBuilding = building;
    console.log('Selected building:', building);

    // Update renderer to show selection
    if (mapRenderer) {
        mapRenderer.setSelectedBuilding(building);
        mapRenderer.render();

        // Update minimap as well
        const miniMapCanvas = document.getElementById('miniMap');
        if (miniMapCanvas) {
            mapRenderer.renderMinimap(miniMapCanvas);
        }
    }

    // Update building info panel
    updateBuildingInfo(building);

    // Update production button visibility
    updateProductionButtons(building);

    // Load build queue for this specific building
    loadProductionQueue();
}

/**
 * Update production button visibility based on selected building
 */
function updateProductionButtons(building) {
    const productionButtons = document.querySelectorAll('.production-btn');

    if (!building) {
        // No building selected - disable all buttons
        productionButtons.forEach(btn => btn.disabled = true);
        return;
    }

    // Check if this building belongs to the player
    const myPlayer = gamePlayers.find(p => p.playerName === playerName);
    const isOwned = myPlayer && building.playerNumber === myPlayer.playerSlot;

    if (isOwned && building.type === 'HEADQUARTERS') {
        // Enable villager production button (first button) and rally point button (12th button)
        productionButtons.forEach((btn, index) => {
            const action = parseInt(btn.getAttribute('data-action'));
            btn.disabled = !(action === 1 || action === 12); // Enable Villager and Rally Point
        });
    } else if (isOwned) {
        // For other owned buildings, only enable rally point button
        productionButtons.forEach((btn, index) => {
            const action = parseInt(btn.getAttribute('data-action'));
            btn.disabled = action !== 12; // Only enable Rally Point
        });
    } else {
        // Not owned - disable all buttons
        productionButtons.forEach(btn => btn.disabled = true);
    }
}

/**
 * Update the building info panel with selected building details
 */
function updateBuildingInfo(building) {
    const buildingInfoContent = document.getElementById('buildingInfoContent');

    if (!building) {
        buildingInfoContent.innerHTML = '<p class="info-placeholder">Select a building to see details</p>';
        return;
    }

    // Check if this building belongs to the player
    const myPlayer = gamePlayers.find(p => p.playerName === playerName);
    const isOwned = myPlayer && building.playerNumber === myPlayer.playerSlot;

    if (isOwned) {
        // Show full details for owned buildings
        const buildingName = building.type === 'HEADQUARTERS' ? 'Town Center' : building.type;
        buildingInfoContent.innerHTML = `
            <h3>${buildingName}</h3>
            <p><strong>Owner:</strong> You</p>
            <p><strong>Position:</strong> (${building.x}, ${building.y})</p>
            <p><strong>Status:</strong> Active</p>
        `;
    } else {
        // Show only building name for non-owned buildings
        const buildingName = building.type === 'HEADQUARTERS' ? 'Town Center' : building.type;
        const owner = gamePlayers.find(p => p.playerSlot === building.playerNumber);
        const ownerName = owner ? owner.playerName : `Player ${building.playerNumber}`;

        buildingInfoContent.innerHTML = `
            <h3>${buildingName}</h3>
            <p><strong>Owner:</strong> ${ownerName}</p>
        `;
    }
}

/**
 * Deselect current building
 */
function deselectBuilding() {
    selectedBuilding = null;
    console.log('Deselected building');

    // Reset rally point mode
    if (settingRallyPoint) {
        settingRallyPoint = false;
        const canvas = document.getElementById('gameCanvas');
        if (canvas) {
            canvas.style.cursor = 'default';
        }

        // Remove active state from rally point button
        const rallyBtn = document.querySelector('.rally-point-btn');
        if (rallyBtn) rallyBtn.classList.remove('active');
    }

    // Update renderer to clear selection
    if (mapRenderer) {
        mapRenderer.setSelectedBuilding(null);
        mapRenderer.render();

        // Update minimap as well
        const miniMapCanvas = document.getElementById('miniMap');
        if (miniMapCanvas) {
            mapRenderer.renderMinimap(miniMapCanvas);
        }
    }

    // Clear building info panel
    updateBuildingInfo(null);

    // Disable all production buttons
    updateProductionButtons(null);

    // Clear the build queue display
    const buildQueueContainer = document.getElementById('buildQueue');
    if (buildQueueContainer) {
        buildQueueContainer.innerHTML = '<p class="queue-placeholder">No units in queue</p>';
    }
}

/**
 * Toggle rally point setting mode
 */
function toggleRallyPointMode() {
    if (!selectedBuilding) {
        console.log('No building selected');
        return;
    }

    settingRallyPoint = !settingRallyPoint;

    // Update UI to show rally point mode
    const canvas = document.getElementById('gameCanvas');
    const rallyBtn = document.querySelector('.rally-point-btn');

    if (settingRallyPoint) {
        canvas.style.cursor = 'crosshair';
        if (rallyBtn) rallyBtn.classList.add('active');
        console.log('Rally point mode enabled. Click on the map to set rally point.');
    } else {
        canvas.style.cursor = 'default';
        if (rallyBtn) rallyBtn.classList.remove('active');
        console.log('Rally point mode disabled.');
    }
}

/**
 * Set rally point for selected building
 */
async function setRallyPoint(tileX, tileY) {
    if (!selectedBuilding) {
        console.error('No building selected');
        return;
    }

    try {
        // Save building coordinates for re-selection after reload
        const buildingX = selectedBuilding.x;
        const buildingY = selectedBuilding.y;
        const buildingType = selectedBuilding.type;

        const response = await fetch(`${API_BASE_URL}/${gameId}/buildings/rally-point`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                buildingX: buildingX,
                buildingY: buildingY,
                buildingType: buildingType,
                rallyPointX: tileX,
                rallyPointY: tileY
            })
        });

        if (!response.ok) {
            const errorData = await response.json();
            console.error('Server error:', errorData);
            throw new Error(errorData.error || 'Failed to set rally point');
        }

        console.log(`Rally point set to (${tileX}, ${tileY}) for building at (${buildingX}, ${buildingY})`);

        // Reload map to show updated rally point
        if (mapRenderer) {
            await mapRenderer.loadMap(gameId);

            // Find and re-select the building after map reload
            if (mapRenderer.mapData && mapRenderer.mapData.buildings) {
                const updatedBuilding = mapRenderer.mapData.buildings.find(b =>
                    b.x === buildingX && b.y === buildingY && b.type === buildingType
                );

                if (updatedBuilding) {
                    console.log('Rally point on updated building:', updatedBuilding.rallyPointX, updatedBuilding.rallyPointY);
                    selectedBuilding = updatedBuilding;
                    mapRenderer.setSelectedBuilding(updatedBuilding);
                }
            }

            mapRenderer.render();

            // Update minimap as well
            const miniMapCanvas = document.getElementById('miniMap');
            if (miniMapCanvas) {
                mapRenderer.renderMinimap(miniMapCanvas);
            }
        }
    } catch (error) {
        console.error('Error setting rally point:', error);
    }
}

function updateGamePlayerList() {
    const gamePlayerList = document.getElementById('gamePlayerList');
    gamePlayerList.innerHTML = '';

    gamePlayers.forEach(player => {
        const playerDiv = document.createElement('div');
        playerDiv.className = 'game-player-item';

        playerDiv.innerHTML = `
            <div class="game-player-color" style="background: ${player.teamColor}"></div>
            <div class="game-player-name">${player.playerName}</div>
            <div class="game-heartbeat ${player.isConnected ? 'active' : 'disconnected'}"></div>
        `;

        gamePlayerList.appendChild(playerDiv);
    });
}

let gameStartTime = Date.now();
function startGameTimer() {
    setInterval(() => {
        const elapsed = Math.floor((Date.now() - gameStartTime) / 1000);
        const minutes = Math.floor(elapsed / 60).toString().padStart(2, '0');
        const seconds = (elapsed % 60).toString().padStart(2, '0');
        document.getElementById('gameTimer').textContent = `${minutes}:${seconds}`;
    }, 1000);
}

// Cleanup on page unload
window.addEventListener('beforeunload', () => {
    if (heartbeatInterval) {
        clearInterval(heartbeatInterval);
    }
    if (loadingTickInterval) {
        clearInterval(loadingTickInterval);
    }
    if (stompClient) {
        stompClient.disconnect();
    }
});
