const API_BASE_URL = 'http://localhost:8080/api/lobbies';
const MAPS_API_URL = 'http://localhost:8080/api/maps';

// DOM Elements
const quickCreateForm = document.getElementById('quickCreateForm');
const hostNameInput = document.getElementById('hostName');
const lobbyNameInput = document.getElementById('lobbyName');
const mapSelect = document.getElementById('mapSelect');
const mapDescription = document.getElementById('mapDescription');
const maxPlayersSelect = document.getElementById('maxPlayers');
const createMessage = document.getElementById('createMessage');
const backBtn = document.getElementById('backBtn');
const cancelBtn = document.getElementById('cancelBtn');

let availableMaps = [];
let selectedMap = null;

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    setupEventListeners();
    loadMaps();
});

async function loadMaps() {
    try {
        const response = await fetch(`${MAPS_API_URL}/templates`);
        if (!response.ok) throw new Error('Failed to load maps');

        availableMaps = await response.json();

        // Populate map select
        mapSelect.innerHTML = '<option value="">Select a map...</option>';
        availableMaps.forEach(map => {
            const option = document.createElement('option');
            option.value = map.name;
            option.textContent = `${map.displayName} (${map.mapSize})`;
            option.dataset.map = JSON.stringify(map);
            if (map.isDefault) {
                option.selected = true;
            }
            mapSelect.appendChild(option);
        });

        // Trigger initial selection
        if (mapSelect.value) {
            updateMapInfo();
        }
    } catch (error) {
        console.error('Error loading maps:', error);
        mapSelect.innerHTML = '<option value="">Error loading maps</option>';
    }
}

function updateMapInfo() {
    const selectedOption = mapSelect.options[mapSelect.selectedIndex];
    if (selectedOption && selectedOption.dataset.map) {
        selectedMap = JSON.parse(selectedOption.dataset.map);
        mapDescription.textContent = `${selectedMap.description} | Players: ${selectedMap.minPlayers}-${selectedMap.maxPlayers} | Terrain: ${selectedMap.baseTerrain}`;
        mapDescription.style.display = 'block';

        // Update max players options based on map
        updateMaxPlayersOptions();
    } else {
        mapDescription.style.display = 'none';
        selectedMap = null;
    }
}

function updateMaxPlayersOptions() {
    if (!selectedMap) return;

    const currentValue = parseInt(maxPlayersSelect.value);
    maxPlayersSelect.innerHTML = '';

    for (let i = selectedMap.minPlayers; i <= selectedMap.maxPlayers; i++) {
        const option = document.createElement('option');
        option.value = i;
        option.textContent = `${i} Players`;

        if (i === currentValue || (i === 4 && currentValue < selectedMap.minPlayers)) {
            option.selected = true;
        }

        maxPlayersSelect.appendChild(option);
    }
}

function setupEventListeners() {
    quickCreateForm.addEventListener('submit', handleQuickCreate);

    mapSelect.addEventListener('change', updateMapInfo);

    backBtn.addEventListener('click', () => {
        window.location.href = 'lobby.html';
    });

    cancelBtn.addEventListener('click', () => {
        window.location.href = 'lobby.html';
    });
}

async function handleQuickCreate(e) {
    e.preventDefault();

    const hostName = hostNameInput.value.trim();
    const lobbyName = lobbyNameInput.value.trim();
    const selectedMapName = mapSelect.value;
    const maxPlayers = parseInt(maxPlayersSelect.value);

    if (!hostName || !lobbyName || !selectedMapName) {
        showMessage('Please fill in all fields', 'error');
        return;
    }

    const lobbyData = {
        name: lobbyName,
        hostPlayer: hostName,
        maxPlayers: maxPlayers,
        mapName: selectedMapName, // Use template name (e.g., "arabia") not display name
        isPrivate: false,
        password: null
    };

    try {
        const response = await fetch(API_BASE_URL, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(lobbyData)
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Failed to create lobby');
        }

        const lobby = await response.json();
        showMessage('Lobby created! Redirecting...', 'success');

        // Store host info in session storage
        sessionStorage.setItem('playerName', hostName);
        sessionStorage.setItem('isHost', 'true');

        // Redirect to lobby room after 1 second
        setTimeout(() => {
            window.location.href = `lobby-room.html?id=${lobby.id}`;
        }, 1000);

    } catch (error) {
        console.error('Error creating lobby:', error);
        showMessage(error.message, 'error');
    }
}

function showMessage(message, type) {
    createMessage.textContent = message;
    createMessage.className = `message ${type}`;
    createMessage.style.display = 'block';

    if (type === 'error') {
        setTimeout(() => {
            createMessage.style.display = 'none';
        }, 5000);
    }
}
