const API_BASE_URL = 'http://localhost:8080/api/lobbies';

let lobbyId = null;
let isHost = false;
let playerName = '';
let currentLobby = null;
let isReady = false;
let stompClient = null;

// DOM Elements
const lobbyTitle = document.getElementById('lobbyTitle');
const playerCount = document.getElementById('playerCount');
const maxPlayersSpan = document.getElementById('maxPlayers');
const playerSlots = document.getElementById('playerSlots');
const leaveBtn = document.getElementById('leaveBtn');
const hostControls = document.getElementById('hostControls');
const guestMessage = document.getElementById('guestMessage');
const startGameBtn = document.getElementById('startGameBtn');
const mapSelect = document.getElementById('mapSelect');
const startingResources = document.getElementById('startingResources');
const gameSpeed = document.getElementById('gameSpeed');
const mapPreview = document.getElementById('mapPreview');
const playerButtons = document.querySelectorAll('.player-btn');
const chatMessages = document.getElementById('chatMessages');
const chatInput = document.getElementById('chatInput');
const sendBtn = document.getElementById('sendBtn');
const addAIBtn = document.getElementById('addAIBtn');
const aiDifficulty = document.getElementById('aiDifficulty');

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    // Get lobby ID from URL
    const urlParams = new URLSearchParams(window.location.search);
    lobbyId = urlParams.get('id');

    if (!lobbyId) {
        alert('No lobby ID provided');
        window.location.href = 'lobby.html';
        return;
    }

    // Get player info from session storage
    playerName = sessionStorage.getItem('playerName') || 'Player';
    isHost = sessionStorage.getItem('isHost') === 'true';

    setupEventListeners();
    connectWebSocket();
    loadLobby();

    // Poll for updates every 2 seconds
    setInterval(loadLobby, 2000);
});

function connectWebSocket() {
    const socket = new SockJS('http://localhost:8080/ws');
    stompClient = Stomp.over(socket);

    stompClient.connect({}, function(frame) {
        console.log('Connected: ' + frame);

        // Subscribe to lobby-specific chat
        stompClient.subscribe(`/topic/lobby/${lobbyId}`, function(message) {
            const chatMessage = JSON.parse(message.body);
            displayChatMessage(chatMessage);
        });

        // Send join notification
        sendChatMessage('', 'JOIN');
    }, function(error) {
        console.error('WebSocket connection error:', error);
        addSystemMessage('Chat connection failed. Retrying...');
        setTimeout(connectWebSocket, 5000);
    });
}

function disconnectWebSocket() {
    if (stompClient !== null) {
        sendChatMessage('', 'LEAVE');
        stompClient.disconnect();
    }
}

function setupEventListeners() {
    leaveBtn.addEventListener('click', async () => {
        if (confirm('Are you sure you want to leave the lobby?')) {
            await leaveLobby();
        }
    });

    if (isHost) {
        // Show host controls
        hostControls.style.display = 'block';
        guestMessage.style.display = 'none';

        startGameBtn.addEventListener('click', startGame);

        playerButtons.forEach(btn => {
            btn.addEventListener('click', () => updateMaxPlayers(parseInt(btn.dataset.players)));
        });

        // Config change listeners
        mapSelect.addEventListener('change', handleConfigChange);
        startingResources.addEventListener('change', handleConfigChange);
        gameSpeed.addEventListener('change', handleConfigChange);

        mapSelect.addEventListener('change', (e) => {
            mapPreview.querySelector('.preview-placeholder').textContent = e.target.value;
        });

        // AI player controls
        addAIBtn.addEventListener('click', addAIPlayer);
    } else {
        // Show guest message
        hostControls.style.display = 'none';
        guestMessage.style.display = 'block';
    }

    sendBtn.addEventListener('click', sendMessage);
    chatInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            sendMessage();
        }
    });
}

async function loadLobby() {
    try {
        const response = await fetch(`${API_BASE_URL}/${lobbyId}`);
        if (!response.ok) throw new Error('Failed to load lobby');

        currentLobby = await response.json();
        updateUI();
    } catch (error) {
        console.error('Error loading lobby:', error);
        addSystemMessage('Failed to load lobby data');
    }
}

function updateUI() {
    if (!currentLobby) return;

    // Update title
    lobbyTitle.textContent = currentLobby.name;

    // Update player count
    const playersList = currentLobby.players || [];
    playerCount.textContent = playersList.length;
    maxPlayersSpan.textContent = currentLobby.maxPlayers;

    // Check our ready status
    const ourPlayer = playersList.find(p => p.name === playerName);
    if (ourPlayer) {
        isReady = ourPlayer.isReady;
    //    updateReadyButton();
    }

    // Update player slots
    updatePlayerSlots();

    // Update config display
    if (isHost) {
        mapSelect.value = currentLobby.mapName;
        playerButtons.forEach(btn => {
            btn.classList.toggle('active', parseInt(btn.dataset.players) === currentLobby.maxPlayers);
        });
    }

    // Check if can start game
    updateStartButton();
}

function updatePlayerSlots() {
    if (!currentLobby) return;

    playerSlots.innerHTML = '';
    const playersList = currentLobby.players || [];

    for (let i = 0; i < currentLobby.maxPlayers; i++) {
        const slot = document.createElement('div');
        const player = playersList[i];

        if (player) {
            // Occupied slot
            const aiClass = player.isAI ? 'ai-player' : '';
            slot.className = `player-slot occupied ${player.isReady ? 'ready' : ''} ${aiClass}`;

            const isCurrentPlayer = player.name === playerName;

            // AI players show remove button for host, regular players show ready button for themselves
            let actionButtonHtml = '';
            if (player.isAI && isHost) {
                actionButtonHtml = `<button class="remove-ai-btn" data-player-id="${player.id}">✕ Remove</button>`;
            } else if (isCurrentPlayer && !player.isAI) {
                actionButtonHtml = `<button class="player-ready-btn ${player.isReady ? 'ready' : ''}" data-player="${player.name}">
                    ${player.isReady ? '✓ Ready' : 'Ready'}
                   </button>`;
            }

            const badges = [];
            if (player.isHost) badges.push('<span class="player-badge">HOST</span>');
            if (player.isAI) badges.push(`<span class="ai-badge">AI (${player.aiDifficulty})</span>`);

            slot.innerHTML = `
                <div class="slot-number">${i + 1}</div>
                <div class="player-info">
                    <div class="player-name-row">
                        <span class="player-name">${player.name}${isCurrentPlayer ? ' (You)' : ''}</span>
                        ${badges.join('')}
                    </div>
                    ${actionButtonHtml}
                </div>
                ${player.isReady ? '<div class="ready-indicator">✓</div>' : ''}
            `;

            // Add click listeners
            if (player.isAI && isHost) {
                const removeBtn = slot.querySelector('.remove-ai-btn');
                if (removeBtn) {
                    removeBtn.addEventListener('click', () => removeAIPlayer(player.id));
                }
            } else if (isCurrentPlayer && !player.isAI) {
                const readyButton = slot.querySelector('.player-ready-btn');
                if (readyButton) {
                    readyButton.addEventListener('click', () => toggleReady());
                }
            }
        } else {
            // Empty slot
            slot.className = 'player-slot empty';
            slot.innerHTML = `
                <div class="slot-number">${i + 1}</div>
                <div class="player-info">
                    <div class="player-name">Waiting for player...</div>
                    <div class="player-status">Empty slot</div>
                </div>
            `;
        }

        playerSlots.appendChild(slot);
    }
}

async function toggleReady() {
    try {
        const response = await fetch(`${API_BASE_URL}/${lobbyId}/ready`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ playerName })
        });

        if (!response.ok) throw new Error('Failed to toggle ready');

        const lobby = await response.json();
        currentLobby = lobby;

        const ourPlayer = lobby.players.find(p => p.name === playerName);
        if (ourPlayer) {
            isReady = ourPlayer.isReady;
            updatePlayerSlots();
            updateStartButton();
            // Message will be sent via WebSocket from server
        }

    } catch (error) {
        console.error('Error toggling ready:', error);
        addSystemMessage('Failed to update ready status');
    }
}

function updateStartButton() {
    if (!isHost) return;

    const playersList = currentLobby?.players || [];
    const maxPlayers = currentLobby?.maxPlayers || 4;

    // Check if all slots are filled
    const allSlotsFilled = playersList.length === maxPlayers;

    // Check if all players are ready
    const allPlayersReady = playersList.length > 0 && playersList.every(p => p.isReady);

    // Can only start if all slots filled AND all players ready
    const canStart = allSlotsFilled && allPlayersReady;

    startGameBtn.disabled = !canStart;

    if (canStart) {
        document.querySelector('.start-hint').textContent = 'All players ready! Click to start!';
    } else if (!allSlotsFilled) {
        document.querySelector('.start-hint').textContent = `Waiting for players... (${playersList.length}/${maxPlayers})`;
    } else {
        document.querySelector('.start-hint').textContent = 'All players must be ready to start';
    }
}

async function handleConfigChange() {
    if (!isHost) return;

    const mapName = mapSelect.value;

    try {
        const response = await fetch(`${API_BASE_URL}/${lobbyId}/config`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ mapName })
        });

        if (!response.ok) throw new Error('Failed to update config');

        const lobby = await response.json();
        currentLobby = lobby;

        // Reset our ready status
        isReady = false;
        updatePlayerSlots();
        updateStartButton();
        // Message will be sent via WebSocket from server

    } catch (error) {
        console.error('Error updating config:', error);
        addSystemMessage('Failed to update configuration');
    }
}

async function updateMaxPlayers(newMax) {
    if (!isHost) return;

    try {
        const response = await fetch(`${API_BASE_URL}/${lobbyId}/config`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ maxPlayers: newMax })
        });

        if (!response.ok) throw new Error('Failed to update max players');

        const lobby = await response.json();
        currentLobby = lobby;

        playerButtons.forEach(btn => {
            btn.classList.toggle('active', parseInt(btn.dataset.players) === newMax);
        });

        // Reset our ready status
        isReady = false;
        updatePlayerSlots();
        updateStartButton();
        // Message will be sent via WebSocket from server

    } catch (error) {
        console.error('Error updating max players:', error);
        addSystemMessage('Failed to update max players');
    }
}

async function startGame() {
    if (!isHost) return;

    addSystemMessage('Starting game...');

    try {
        const response = await fetch(`http://localhost:8080/api/games/start/${lobbyId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        if (!response.ok) throw new Error('Failed to start game');

        const game = await response.json();
        console.log('Game started:', game);

        // The WebSocket will handle the redirect via GAME_START message

    } catch (error) {
        console.error('Error starting game:', error);
        addSystemMessage('Failed to start game. Please try again.');
    }
}

function sendMessage() {
    const message = chatInput.value.trim();
    if (!message) return;

    sendChatMessage(message, 'CHAT');
    chatInput.value = '';
}

function sendChatMessage(content, type) {
    if (stompClient && stompClient.connected) {
        const chatMessage = {
            sender: playerName,
            content: content,
            type: type,
            lobbyId: lobbyId
        };
        stompClient.send(`/app/chat/${lobbyId}`, {}, JSON.stringify(chatMessage));
    }
}

function displayChatMessage(chatMessage) {
    if (chatMessage.type === 'CHAT') {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'chat-message';
        messageDiv.innerHTML = `<span class="sender">${chatMessage.sender}:</span> ${chatMessage.content}`;
        chatMessages.appendChild(messageDiv);
        chatMessages.scrollTop = chatMessages.scrollHeight;
    } else if (chatMessage.type === 'JOIN') {
        addSystemMessage(`${chatMessage.sender} joined the lobby`);
    } else if (chatMessage.type === 'LEAVE') {
        addSystemMessage(`${chatMessage.sender} left the lobby`);
    } else if (chatMessage.type === 'SYSTEM') {
        // Check for special system messages
        if (chatMessage.content === 'LOBBY_CLOSED') {
            alert('The host has closed the lobby. You will be redirected to the lobby list.');
            disconnectWebSocket();
            window.location.href = 'lobby.html';
        } else if (chatMessage.content.startsWith('GAME_START:')) {
            // Extract game ID and redirect to game page
            const gameId = chatMessage.content.split(':')[1];
            addSystemMessage('Game is starting! Redirecting...');
            setTimeout(() => {
                disconnectWebSocket();
                window.location.href = `game.html?id=${gameId}`;
            }, 1000);
        } else {
            addSystemMessage(chatMessage.content);
        }
    }
}

function addSystemMessage(message) {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'system-message';
    messageDiv.textContent = message;
    chatMessages.appendChild(messageDiv);
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

async function leaveLobby() {
    try {
        const response = await fetch(`${API_BASE_URL}/${lobbyId}/leave`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ playerName })
        });

        if (!response.ok) throw new Error('Failed to leave lobby');

        const result = await response.json();

        disconnectWebSocket();

        if (result.lobbyDeleted) {
            // Host closed the lobby
            alert('Lobby closed successfully.');
        }

        // Redirect back to lobby list
        window.location.href = 'lobby.html';

    } catch (error) {
        console.error('Error leaving lobby:', error);
        alert('Failed to leave lobby. Redirecting anyway...');
        disconnectWebSocket();
        window.location.href = 'lobby.html';
    }
}

// AI Player Functions
async function addAIPlayer() {
    if (!isHost) return;

    const difficulty = aiDifficulty.value;

    try {
        const response = await fetch(`${API_BASE_URL}/${lobbyId}/add-ai-player`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ difficulty })
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Failed to add AI player');
        }

        const lobby = await response.json();
        currentLobby = lobby;
        updatePlayerSlots();
        updateStartButton();
        // WebSocket message will be sent from server

    } catch (error) {
        console.error('Error adding AI player:', error);
        addSystemMessage('Failed to add AI player: ' + error.message);
    }
}

async function removeAIPlayer(playerId) {
    if (!isHost) return;

    try {
        const response = await fetch(`${API_BASE_URL}/${lobbyId}/ai-player/${playerId}`, {
            method: 'DELETE'
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Failed to remove AI player');
        }

        // Reload lobby to get updated player list
        await loadLobby();
        // WebSocket message will be sent from server

    } catch (error) {
        console.error('Error removing AI player:', error);
        addSystemMessage('Failed to remove AI player: ' + error.message);
    }
}
