package com.rts.service.map;


import com.rts.config.BinaryWebSocketHandler;
import com.rts.model.MapGrid;
import com.rts.model.Terrain;
import com.rts.utils.BinaryMapEncoder;
import com.rts.utils.RNG;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for land generation with WebSocket streaming updates.
 *
 * Extends AbstractLandGenerator and overrides callback methods to send
 * real-time updates via WebSocket to connected clients.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingLandGenerator extends AbstractLandGenerator {

    private final BinaryWebSocketHandler webSocketHandler;

    // Streaming state for current generation
    private String currentSessionId;
    private int currentUpdateInterval;
    private List<TilePlacement> accumulatedTiles;

    /**
     * Grow land with WebSocket streaming updates.
     *
     * @param grid the map grid to modify
     * @param rng random number generator
     * @param originX starting x coordinate
     * @param originY starting y coordinate
     * @param targetTiles number of tiles to place
     * @param terrainType terrain type to place
     * @param landId land identifier
     * @param owner optional player owner
     * @param provenance (unused, kept for compatibility)
     * @param sessionId WebSocket session ID for streaming updates
     * @param updateInterval how often to send updates (every N iterations)
     * @return number of tiles actually placed
     */
    public int growLandStreaming(MapGrid grid, RNG rng,
                                 int originX, int originY,
                                 int targetTiles, Terrain terrainType,
                                 int landId, Integer owner, int provenance,
                                 String sessionId, int updateInterval) {

        // Store streaming context
        this.currentSessionId = sessionId;
        this.currentUpdateInterval = updateInterval;
        this.accumulatedTiles = new ArrayList<>();

        try {
            // Delegate to base class algorithm
            return super.growLand(grid, rng, originX, originY, targetTiles, terrainType, landId, owner);
        } finally {
            // Clean up streaming context
            this.currentSessionId = null;
            this.currentUpdateInterval = 0;
            this.accumulatedTiles = null;
        }
    }

    // ========== Callback Overrides for Streaming ==========

    @Override
    protected void onStart(int placedTiles, int targetTiles, int landId, Integer owner,
                          int originX, int originY, Terrain terrainType) {
        if (currentSessionId != null) {
            sendUpdate(currentSessionId, new StreamUpdate("start",
                    placedTiles, targetTiles, landId, owner,
                    List.of(new TileUpdate(originX, originY, terrainType, landId, owner)),
                    List.of(new int[]{originX, originY})
            ));
        }
    }

    @Override
    protected void onProgress(int placedTiles, int targetTiles, int landId, Integer owner,
                             int iterations, List<TilePlacement> newTiles, List<Point> frontier) {
        if (currentSessionId != null) {
            // Accumulate tiles since last update
            accumulatedTiles.addAll(newTiles);

            // Send update at specified interval or when complete
            if (iterations % currentUpdateInterval == 0 || placedTiles >= targetTiles) {
                if (!accumulatedTiles.isEmpty()) {
                    List<TileUpdate> tileUpdates = accumulatedTiles.stream()
                            .map(tp -> new TileUpdate(tp.x, tp.y, tp.terrainType, tp.landId, tp.owner))
                            .collect(Collectors.toList());

                    List<int[]> frontierPoints = frontier.stream()
                            .map(p -> new int[]{p.x, p.y})
                            .collect(Collectors.toList());

                    sendUpdate(currentSessionId, new StreamUpdate("progress",
                            placedTiles, targetTiles, landId, owner,
                            tileUpdates, frontierPoints
                    ));

                    // Clear accumulated tiles after sending
                    accumulatedTiles.clear();
                }
            }
        }
    }

    @Override
    protected void onComplete(int placedTiles, int targetTiles, int landId, Integer owner, int iterations) {
        if (currentSessionId != null) {
            sendUpdate(currentSessionId, new StreamUpdate("complete",
                    placedTiles, targetTiles, landId, owner,
                    List.of(), List.of()
            ));
        }
    }

    // ========== Helper Methods ==========

    private void sendUpdate(String sessionId, StreamUpdate update) {
        if (sessionId != null && webSocketHandler != null) {
            log.debug("Sending binary update to session {}: type={}, tiles={}/{}",
                     sessionId, update.type, update.placedtiles, update.targetTiles);

            // Encode to binary format
            byte[] binaryData = BinaryMapEncoder.encodeStreamUpdate(update);

            // Send as binary message via native WebSocket
            webSocketHandler.sendBinaryMessage(sessionId, binaryData);

            log.debug("Sent {} bytes (was {} tiles)", binaryData.length, update.tiles.size());
        } else {
            log.warn("Cannot send update - sessionId={}, webSocketHandler={}", sessionId, webSocketHandler);
        }
    }

    public static class StreamUpdate{

        public final String type;
        public final int placedtiles;
        public final int targetTiles;
        public final int landId;
        public final Integer owner;
        public final List<TileUpdate> tiles;
        public final List<int[]> frontier;

        public StreamUpdate(String type, int placedTiles, int targetTiles,
                            int landId,Integer owner,
                            List<TileUpdate> tiles, List<int[]> frontier){
            this.type = type;
            this.placedtiles = placedTiles;
            this.targetTiles = targetTiles;
            this.landId = landId;
            this.owner = owner;
            this.tiles = tiles;
            this.frontier = frontier;

        }
    }

    public static class TileUpdate{
        public final int x;
        public final int y;
        public final Terrain terrainType;
        public final int landId;
        public final Integer owner;
        public final String object;

        public TileUpdate(int x, int y, Terrain terrainType, int landId, Integer owner, String object ){
            this.x = x;
            this.y = y;
            this.terrainType = terrainType;
            this.landId = landId;
            this.owner = owner;
            this.object= object;
        }

        public TileUpdate(int x, int y, Terrain terrainType, int landId, Integer owner){
            this(x,y,terrainType,landId,owner,null);
        }
    }
}
