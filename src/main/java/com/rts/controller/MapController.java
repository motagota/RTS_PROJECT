package com.rts.controller;

import com.rts.model.GeneratedMap;
import com.rts.model.MapTemplate;
import com.rts.service.MapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/maps")
@CrossOrigin(origins = "*")
public class MapController {

    @Autowired
    private MapService mapService;

    @GetMapping("/templates")
    public ResponseEntity<List<MapTemplate>> getAllMapTemplates() {
        return ResponseEntity.ok(mapService.getAllMapTemplates());
    }

    @GetMapping("/templates/{name}")
    public ResponseEntity<?> getMapTemplate(@PathVariable String name) {
        return mapService.getMapTemplateByName(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateMap(@RequestBody Map<String, Object> request) {
        try {
            String templateName = (String) request.get("templateName");
            Integer playerCount = (Integer) request.get("playerCount");
            Long gameId = ((Number) request.get("gameId")).longValue();

            GeneratedMap generatedMap = mapService.generateMap(templateName, playerCount, gameId);
            return ResponseEntity.ok(generatedMap);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to generate map: " + e.getMessage()));
        }
    }

    @GetMapping("/game/{gameId}")
    public ResponseEntity<?> getMapByGameId(@PathVariable Long gameId) {
        try {
            return mapService.getGeneratedMapByGameId(gameId)
                    .map(generatedMap -> {
                        try {
                            // Decompress terrain data before sending to client
                            String decompressedTerrain = mapService.decompressAndDeserializeMapGrid(generatedMap.getTerrainData());

                            // Create response with decompressed data
                            Map<String, Object> response = new HashMap<>();
                            response.put("id", generatedMap.getId());
                            response.put("gameId", generatedMap.getGameId());
                            response.put("mapTemplateName", generatedMap.getMapTemplateName());
                            response.put("width", generatedMap.getWidth());
                            response.put("height", generatedMap.getHeight());
                            response.put("playerCount", generatedMap.getPlayerCount());
                            response.put("terrainData", decompressedTerrain);
                            response.put("playerStarts", generatedMap.getPlayerStarts());
                            response.put("buildings", generatedMap.getBuildings());
                            response.put("units", generatedMap.getUnits());
                            response.put("generatedAt", generatedMap.getGeneratedAt());

                            return ResponseEntity.ok(response);
                        } catch (Exception e) {
                            return ResponseEntity.internalServerError()
                                    .body(Map.of("error", "Failed to decompress map data: " + e.getMessage()));
                        }
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve map: " + e.getMessage()));
        }
    }
}
