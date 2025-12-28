const API_BASE_URL = 'http://localhost:8080/api/lobbies';

let currentPlayerName = '';
let selectedLobby = null;

// DOM Elements
const searchInput = document.getElementById('searchInput');
const searchBtn = document.getElementById('searchBtn');
const refreshBtn = document.getElementById('refreshBtn');
const lobbyList = document.getElementById('lobbyList');
const createLobbyBtn = document.getElementById('createLobbyBtn');
const playerNameDisplay = document.getElementById('playerNameDisplay');

// Modal Elements
const modal = document.getElementById('joinModal');
const closeModal = document.querySelector('.close');
const joinLobbyForm = document.getElementById('joinLobbyForm');
const joinPlayerNameInput = document.getElementById('joinPlayerName');
const joinPasswordInput = document.getElementById('joinPassword');
const joinPasswordGroup = document.getElementById('joinPasswordGroup');
const modalLobbyInfo = document.getElementById('modalLobbyInfo');
const joinMessage = document.getElementById('joinMessage');

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    loadLobbies();
    setupEventListeners();
});

function setupEventListeners() {
    searchBtn.addEventListener('click', handleSearch);
    refreshBtn.addEventListener('click', loadLobbies);
    createLobbyBtn.addEventListener('click', () => {
        window.location.href = 'create-lobby.html';
    });
    closeModal.addEventListener('click', () => modal.style.display = 'none');
    joinLobbyForm.addEventListener('submit', handleJoinLobby);

    window.addEventListener('click', (e) => {
        if (e.target === modal) {
            modal.style.display = 'none';
        }
    });

    searchInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            handleSearch();
        }
    });
}

async function loadLobbies() {
    try {
        lobbyList.innerHTML = '<div class="loading">Loading lobbies...</div>';

        const response = await fetch(API_BASE_URL);
        if (!response.ok) throw new Error('Failed to load lobbies');

        const lobbies = await response.json();
        displayLobbies(lobbies);
    } catch (error) {
        console.error('Error loading lobbies:', error);
        lobbyList.innerHTML = '<div class="error">Failed to load lobbies. Please try again.</div>';
    }
}

async function handleSearch() {
    const query = searchInput.value.trim();
    if (!query) {
        loadLobbies();
        return;
    }

    try {
        lobbyList.innerHTML = '<div class="loading">Searching...</div>';

        const response = await fetch(`${API_BASE_URL}/search?query=${encodeURIComponent(query)}`);
        if (!response.ok) throw new Error('Search failed');

        const lobbies = await response.json();
        displayLobbies(lobbies);
    } catch (error) {
        console.error('Error searching lobbies:', error);
        lobbyList.innerHTML = '<div class="error">Search failed. Please try again.</div>';
    }
}

function displayLobbies(lobbies) {
    if (lobbies.length === 0) {
        lobbyList.innerHTML = '<div class="empty-state">No lobbies found. Create one to get started!</div>';
        return;
    }

    lobbyList.innerHTML = lobbies.map(lobby => createLobbyItem(lobby)).join('');

    // Add click handlers
    document.querySelectorAll('.lobby-item').forEach(item => {
        item.addEventListener('click', () => {
            const lobbyId = item.dataset.lobbyId;
            const lobby = lobbies.find(l => l.id == lobbyId);
            if (lobby) {
                openJoinModal(lobby);
            }
        });
    });
}

function createLobbyItem(lobby) {
    const isFull = lobby.currentPlayers >= lobby.maxPlayers;
    const statusClass = isFull ? 'status-full' : 'status-waiting';
    const statusText = isFull ? 'FULL' : 'WAITING';
    const lockIcon = lobby.isPrivate ? '🔒 ' : '';

    return `
        <div class="lobby-item" data-lobby-id="${lobby.id}">
            <div class="lobby-header">
                <div class="lobby-name">${lockIcon}${lobby.name}</div>
                <div class="lobby-status ${statusClass}">${statusText}</div>
            </div>
            <div class="lobby-details">
                <div class="lobby-detail">
                    <strong>Host:</strong> ${lobby.hostPlayer}
                </div>
                <div class="lobby-detail">
                    <strong>Players:</strong> ${lobby.currentPlayers}/${lobby.maxPlayers}
                </div>
                <div class="lobby-detail">
                    <strong>Map:</strong> ${lobby.mapName}
                </div>
                <div class="lobby-detail">
                    <strong>Created:</strong> ${formatTime(lobby.createdAt)}
                </div>
            </div>
        </div>
    `;
}

function formatTime(timestamp) {
    const date = new Date(timestamp);
    const now = new Date();
    const diff = Math.floor((now - date) / 1000 / 60); // minutes

    if (diff < 1) return 'Just now';
    if (diff < 60) return `${diff}m ago`;
    if (diff < 1440) return `${Math.floor(diff / 60)}h ago`;
    return date.toLocaleDateString();
}

function openJoinModal(lobby) {
    selectedLobby = lobby;

    const isFull = lobby.currentPlayers >= lobby.maxPlayers;
    const lockIcon = lobby.isPrivate ? '🔒 ' : '';

    modalLobbyInfo.innerHTML = `
        <h3>${lockIcon}${lobby.name}</h3>
        <p><strong>Host:</strong> ${lobby.hostPlayer}</p>
        <p><strong>Players:</strong> ${lobby.currentPlayers}/${lobby.maxPlayers}</p>
        <p><strong>Map:</strong> ${lobby.mapName}</p>
        ${isFull ? '<p class="error">This lobby is full!</p>' : ''}
    `;

    joinPasswordGroup.style.display = lobby.isPrivate ? 'block' : 'none';
    joinPasswordInput.required = lobby.isPrivate;

    // Disable join button if full
    const submitBtn = joinLobbyForm.querySelector('button[type="submit"]');
    submitBtn.disabled = isFull;

    modal.style.display = 'block';
    joinMessage.style.display = 'none';
}

async function handleJoinLobby(e) {
    e.preventDefault();

    if (!selectedLobby) return;

    const playerName = joinPlayerNameInput.value.trim();

    if (!playerName) {
        showMessage(joinMessage, 'Please enter your name', 'error');
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/${selectedLobby.id}/join`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ playerName })
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Failed to join lobby');
        }

        const lobby = await response.json();
        showMessage(joinMessage, `Successfully joined "${lobby.name}"! Redirecting...`, 'success');

        // Store player info
        sessionStorage.setItem('playerName', playerName);
        sessionStorage.setItem('isHost', 'false');

        // Redirect to lobby room
        setTimeout(() => {
            window.location.href = `lobby-room.html?id=${lobby.id}`;
        }, 1000);

    } catch (error) {
        console.error('Error joining lobby:', error);
        showMessage(joinMessage, error.message, 'error');
    }
}

function showMessage(element, message, type) {
    element.textContent = message;
    element.className = `message ${type}`;
    element.style.display = 'block';
}

// Auto-refresh lobbies every 5 seconds
setInterval(loadLobbies, 5000);