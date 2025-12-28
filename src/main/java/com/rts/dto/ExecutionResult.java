package com.rts.dto;

import java.util.List;
import com.rts.model.MapSnapshot;
/**
 * Execution result containing snapshots and AST
 * 
 * this is what gets returned from Mapexecutor.execute() and then wrapped in MapGenerationResponse to send back to the client
 */
public class ExecutionResult {
    
    public final List<MapSnapshot> snapshots;
    public final List<AstNode> ast;
    public ExecutionResult(List<MapSnapshot> snapshots, List<AstNode> ast){
        this.snapshots = snapshots;
        this.ast = ast;
    }
    
}
