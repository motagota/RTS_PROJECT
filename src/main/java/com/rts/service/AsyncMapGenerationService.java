package com.rts.service;

import com.rts.dto.AstNode;
import com.rts.dto.MapGenerationRequest;
import com.rts.model.MapGrid;
import com.rts.model.Terrain;
import com.rts.service.map.RMSParser;
import com.rts.service.map.StreamingLandGenerator;
import com.rts.utils.GridInitializer;
import com.rts.utils.RNG;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncMapGenerationService {

    private final StreamingLandGenerator streamingLandGenerator;
    private final StreamingMapExecutor streamingMapExecutor;
    private final RMSParser rmsParser;

    @Async
    public void generateMapAsync(String sessionId, MapGenerationRequest request) {
        try {
            log.info("Starting async map generation for session: {} on thread: {}",
                    sessionId, Thread.currentThread().getName());

            // Give client time to subscribe
            Thread.sleep(500);

            int mapSize = request.getMapSize();
            long seed = request.getSeed();
            int updateInterval = request.getUpdateInterval() != null ? request.getUpdateInterval() : 10;
            int playerCount = request.getPlayerCount();

            // Check if RMS script is provided
            String rmsScript = request.getRmsScript();

            if (rmsScript != null && !rmsScript.trim().isEmpty()) {
                // RMS script mode - parse and execute with streaming
                log.info("Executing RMS script for session: {} with {} players", sessionId, playerCount);

                List<AstNode> ast = rmsParser.parse(rmsScript);
                log.info("Parsed {} RMS commands", ast.size());

                boolean stepByStep = request.getStepByStep() != null && request.getStepByStep();
                streamingMapExecutor.executeStreaming(ast, mapSize, seed, sessionId, updateInterval, stepByStep, playerCount);

            } else {
                // Simple mode - just generate a single land mass
                log.info("Simple land generation mode");

                RNG rng = new RNG(seed);
                MapGrid grid = GridInitializer.createWaterGrid(mapSize);

                int centerX = mapSize / 2;
                int centerY = mapSize / 2;
                int targetTiles = (int) (mapSize * mapSize * 0.3); // 30% land coverage

                log.info("Starting land generation: center=({},{}), targetTiles={}", centerX, centerY, targetTiles);

                int tilesGenerated = streamingLandGenerator.growLandStreaming(
                        grid, rng, centerX, centerY, targetTiles,
                        Terrain.GRASS, 1, null, 0,
                        sessionId, updateInterval
                );

                log.info("Completed simple generation: {} tiles", tilesGenerated);
            }

            log.info("Completed async map generation for session: {}", sessionId);

        } catch (Exception e) {
            log.error("Error during async map generation for session: {}", sessionId, e);
        }
    }
}
