package com.rts.controller;

import java.util.List;

import com.rts.dto.AstNode;
import com.rts.dto.ExecutionResult;
import com.rts.dto.MapGenerationRequest;
import com.rts.dto.MapGenerationResponse;
import com.rts.service.MapExecutor;
import com.rts.service.map.RMSParser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/mapsGenerator")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MapGeneratorController {
    private final RMSParser rmsParser;
    private final MapExecutor mapExecutor;
    
    @PostMapping("/generate")
    public ResponseEntity<MapGenerationResponse> generateMap(
        @Valid @RequestBody MapGenerationRequest request ){
        log.info("Generating map with size={}, seed={}", request.getMapSize(), request.getSeed());

        try{
            List<AstNode> ast = rmsParser.parse(request.getRmsScript());
            log.debug("Parsed {} AST nodes", ast.size());

            ExecutionResult result = mapExecutor.execute(
                    ast,
                    request.getMapSize(),
                    request.getSeed()
            );

            MapGenerationResponse mapGenerationResponse = new MapGenerationResponse(
                    result.snapshots,
                    result.ast
            );

            log.info("Map generated successfully with {} steps", result.snapshots.size());
            return ResponseEntity.ok(mapGenerationResponse);
        }
        catch(Exception e){
            log.error("Error generating map: {}",e.getMessage(),e);
            return ResponseEntity.badRequest()
            .body(MapGenerationResponse.error("Error generating map: "+e.getMessage()));

        }
    }
}
