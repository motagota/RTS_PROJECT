package com.rts.utils;

import com.rts.model.Terrain;
import com.rts.service.StreamingMapExecutor;
import com.rts.service.map.StreamingLandGenerator;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for encoding map updates into binary format for efficient WebSocket transmission.
 *
 * Binary format reduces message size by ~80% compared to JSON, preventing buffer overflow issues.
 */
public class BinaryMapEncoder {

    // Message type identifiers
    public static final byte MSG_STEP_UPDATE = 1;
    public static final byte MSG_BASE_TERRAIN = 2;
    public static final byte MSG_TILE_UPDATE = 3;

    // Terrain type encoding (1 byte per terrain)
    private static byte encodeTerrainType(Terrain terrain) {
        return switch (terrain) {
            case WATER -> 0;
            case GRASS -> 1;
            case DESERT -> 2;
            case DIRT -> 3;
            case FOREST -> 4;
            case SNOW -> 5;
            case BEACH -> 6;
            case ROCK -> 7;
            case GOLD -> 8;
            case STONE -> 9;
            case ROAD -> 10;
            case NONE -> 11;
        };
    }

    private static byte encodeObjectType(String object){
        if (object==null || object.isEmpty()){
            return 0;
        }

        return switch (object.toUpperCase()){
            case "GOLD"-> 1;
            case "STONE"->2;
            case "TREE" ->3;
            case "DEER"->4;
            case "BOAR"->5;
            case "SHEEP"->6;
            case "RELIC" ->7;
            case "TOWN_CENTER"->8;
            case "HOUSE"->9;
            case "BARRACKS"->10;
            case "BERRIES", "FORAGE", "BERRY_BUSH" ->11;
            default -> 99;
        };
    }

    /**
     * Encode a step update message.
     * Format: [type:1][currentStep:4][totalSteps:4][status:1][messageLen:2][message:var]
     */
    public static byte[] encodeStepUpdate(StreamingMapExecutor.StepUpdate update) {
        byte[] messageBytes = update.message.getBytes(StandardCharsets.UTF_8);

        // Calculate total size
        int size = 1 + 4 + 4 + 1 + 2 + messageBytes.length;
        ByteBuffer buffer = ByteBuffer.allocate(size);

        buffer.put(MSG_STEP_UPDATE);
        buffer.putInt(update.currentStep);
        buffer.putInt(update.totalSteps);
        buffer.put(encodeStatus(update.status));
        buffer.putShort((short) messageBytes.length);
        buffer.put(messageBytes);

        return buffer.array();
    }

    /**
     * Encode base terrain update message.
     * Format: [type:1][totalCells:4][tileCount:4][[x:2][y:2][terrain:1][object:1]]*
     */
    public static byte[] encodeBaseTerrainUpdate(StreamingMapExecutor.BaseTerrainUpdate update) {
        // Header: 1 + 4 + 4 = 9 bytes
        // Each tile: 2 + 2 + 1 +1= 86 bytes
        int size = 9 + (update.tiles.size() * 6);
        ByteBuffer buffer = ByteBuffer.allocate(size);

        buffer.put(MSG_BASE_TERRAIN);
        buffer.putInt(update.totalCells);
        buffer.putInt(update.tiles.size());

        for (StreamingLandGenerator.TileUpdate tile : update.tiles) {
            buffer.putShort((short) tile.x);
            buffer.putShort((short) tile.y);
            buffer.put(encodeTerrainType(tile.terrainType));
            buffer.put(encodeObjectType(tile.object));
        }

        return buffer.array();
    }

    /**
     * Encode streaming land generator update.
     * Format: [type:1][updateType:1][placedTiles:4][targetTiles:4][landId:4][owner:4][tileCount:4][[x:2][y:2][terrain:1][object:1][landId:4][owner:4]]*
     */
    public static byte[] encodeStreamUpdate(StreamingLandGenerator.StreamUpdate update) {
        // Header: 1 + 1 + 4 + 4 + 4 + 4 + 4 = 22 bytes
        // Each tile: 2 + 2 + 1 + 1 + 4 + 4 = 14 bytes
        int size = 22 + (update.tiles.size() * 14);
        ByteBuffer buffer = ByteBuffer.allocate(size);

        buffer.put(MSG_TILE_UPDATE);
        buffer.put(encodeUpdateType(update.type));
        buffer.putInt(update.placedtiles);
        buffer.putInt(update.targetTiles);
        buffer.putInt(update.landId);
        buffer.putInt(update.owner != null ? update.owner : -1);
        buffer.putInt(update.tiles.size());

        for (StreamingLandGenerator.TileUpdate tile : update.tiles) {
            buffer.putShort((short) tile.x);
            buffer.putShort((short) tile.y);
            buffer.put(encodeTerrainType(tile.terrainType));
            buffer.put(encodeObjectType(tile.object));
            buffer.putInt(tile.landId);
            buffer.putInt(tile.owner != null ? tile.owner : -1);
        }

        return buffer.array();
    }

    private static byte encodeStatus(String status) {
        return switch (status) {
            case "init" -> 0;
            case "step_start" -> 1;
            case "step_complete" -> 2;
            case "step_skip" -> 3;
            case "step_error" -> 4;
            case "complete" -> 5;
            default -> -1;
        };
    }

    private static byte encodeUpdateType(String type) {
        return switch (type) {
            case "start" -> 0;
            case "progress" -> 1;
            case "complete" -> 2;
            default -> -1;
        };
    }
}
