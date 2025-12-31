package com.rts.controller;


import com.rts.dto.MapGenerationRequest;
import com.rts.service.AsyncMapGenerationService;
import com.rts.service.StreamingMapExecutor;
import com.rts.service.map.RMSParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class StreamingMapController {

    private final RMSParser rmsParser;
    private final AsyncMapGenerationService asyncMapGenerationService;
    private final StreamingMapExecutor streamingMapExecutor;


    /* HTTP endpoint to initiate streaming
     * Returns session ID that client should subscribe to
     */
    @PostMapping("/api/maps/generate/stream")
    public ResponseEntity<?> initiateStreaming(@RequestBody MapGenerationRequest request) {

        String sessionId = UUID.randomUUID().toString();

        log.info("Initiating streaming map generation: sessionId={}, mapSize={}, seed={}",
                sessionId, request.getMapSize(), request.getSeed());

        asyncMapGenerationService.generateMapAsync(sessionId, request);

        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "subscribeUrl", "/topic/map-generation/" + sessionId,
                "message", "Map generation started. Subscribe to receive updates."
        ));
    }

    /**
     * WebSocket endpoint to receive "next step" commands in step-by-step mode
     */
    @MessageMapping("/map-generation/{sessionId}/next-step")
    public void handleNextStep(@DestinationVariable String sessionId) {
        log.info("Received next-step command for session: {}", sessionId);
        streamingMapExecutor.nextStep(sessionId);
    }
}
