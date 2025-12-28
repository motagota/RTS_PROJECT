package com.rts.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.rts.model.MapSnapshot;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MapGenerationResponse {

    private List<MapSnapshot> snapshots;

    private List<AstNode> ast;

    private boolean success = true;

    private String message; 
    
    public MapGenerationResponse(List<MapSnapshot> snapshots, List<AstNode> ast) {
        this.snapshots = snapshots;
        this.ast = ast;
        this.success = true;
        this.message = null;
    }

    public static MapGenerationResponse error( String errorMessage){
        MapGenerationResponse response = new MapGenerationResponse();
        response.setSuccess(false);
        response.setMessage(errorMessage);
        response.setAst(List.of());
        return response;
    }
}
