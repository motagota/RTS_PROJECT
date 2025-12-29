/**
 * CoordinateConverter - Utility for converting between screen and tile coordinates
 * Single Responsibility: Handle coordinate transformations
 */
class CoordinateConverter {
    /**
     * Convert screen coordinates to tile coordinates
     * @param {number} screenX - Screen X coordinate
     * @param {number} screenY - Screen Y coordinate
     * @param {HTMLCanvasElement} canvas - Canvas element
     * @param {number} tileSize - Size of tiles in pixels
     * @param {number} mapWidth - Map width in tiles
     * @param {number} mapHeight - Map height in tiles
     * @returns {{tileX: number, tileY: number, isValid: boolean}}
     */
    static screenToTile(screenX, screenY, canvas, tileSize, mapWidth, mapHeight) {
        const rect = canvas.getBoundingClientRect();
        const clickX = screenX - rect.left;
        const clickY = screenY - rect.top;

        // Scale click coordinates if canvas is displayed at different size
        const scaleX = canvas.width / rect.width;
        const scaleY = canvas.height / rect.height;
        const scaledClickX = clickX * scaleX;
        const scaledClickY = clickY * scaleY;

        // Calculate map offset (centering)
        const offsetX = Math.max(0, (canvas.width - mapWidth * tileSize) / 2);
        const offsetY = Math.max(0, (canvas.height - mapHeight * tileSize) / 2);

        // Convert to tile coordinates
        const tileX = Math.floor((scaledClickX - offsetX) / tileSize);
        const tileY = Math.floor((scaledClickY - offsetY) / tileSize);

        // Check if within bounds
        const isValid = tileX >= 0 && tileY >= 0 && tileX < mapWidth && tileY < mapHeight;

        return { tileX, tileY, isValid };
    }

    /**
     * Convert tile coordinates to screen coordinates (center of tile)
     * @param {number} tileX - Tile X coordinate
     * @param {number} tileY - Tile Y coordinate
     * @param {HTMLCanvasElement} canvas - Canvas element
     * @param {number} tileSize - Size of tiles in pixels
     * @param {number} mapWidth - Map width in tiles
     * @param {number} mapHeight - Map height in tiles
     * @returns {{screenX: number, screenY: number}}
     */
    static tileToScreen(tileX, tileY, canvas, tileSize, mapWidth, mapHeight) {
        const offsetX = Math.max(0, (canvas.width - mapWidth * tileSize) / 2);
        const offsetY = Math.max(0, (canvas.height - mapHeight * tileSize) / 2);

        const screenX = offsetX + tileX * tileSize + tileSize / 2;
        const screenY = offsetY + tileY * tileSize + tileSize / 2;

        return { screenX, screenY };
    }

    /**
     * Get map rendering offset
     * @param {HTMLCanvasElement} canvas - Canvas element
     * @param {number} tileSize - Size of tiles in pixels
     * @param {number} mapWidth - Map width in tiles
     * @param {number} mapHeight - Map height in tiles
     * @returns {{offsetX: number, offsetY: number}}
     */
    static getMapOffset(canvas, tileSize, mapWidth, mapHeight) {
        const offsetX = Math.max(0, (canvas.width - mapWidth * tileSize) / 2);
        const offsetY = Math.max(0, (canvas.height - mapHeight * tileSize) / 2);
        return { offsetX, offsetY };
    }
}
