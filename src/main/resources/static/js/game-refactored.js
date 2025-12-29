/**
 * Main entry point for the game
 * Uses the new modular SOLID architecture
 */

let gameController = null;

// Initialize on page load
window.addEventListener('load', init);

async function init() {
    // Get game ID from URL
    const urlParams = new URLSearchParams(window.location.search);
    const gameId = urlParams.get('id');
    const playerName = sessionStorage.getItem('playerName');

    if (!gameId || !playerName) {
        alert('Invalid game session. Redirecting to lobby...');
        window.location.href = 'lobby.html';
        return;
    }

    try {
        // Show loading screen
        showLoadingScreen();

        // Create and initialize game controller
        gameController = new GameController(gameId, playerName);
        await gameController.init();

        // Hide loading screen and show game
        hideLoadingScreen();
    } catch (error) {
        console.error('Failed to initialize game:', error);
        alert('Failed to connect to game. Redirecting to lobby...');
        window.location.href = 'lobby.html';
    }
}

function showLoadingScreen() {
    const loadingScreen = document.getElementById('loadingScreen');
    const gameScreen = document.getElementById('gameScreen');

    if (loadingScreen) loadingScreen.style.display = 'flex';
    if (gameScreen) gameScreen.style.display = 'none';
}

function hideLoadingScreen() {
    const loadingScreen = document.getElementById('loadingScreen');
    const gameScreen = document.getElementById('gameScreen');

    if (loadingScreen) loadingScreen.style.display = 'none';
    if (gameScreen) gameScreen.style.display = 'flex';
}

// Cleanup on page unload
window.addEventListener('beforeunload', () => {
    if (gameController) {
        gameController.cleanup();
    }
});
