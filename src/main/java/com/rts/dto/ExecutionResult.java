package com.rts.dto;

import java.util.List;
import com.rts.model.MapSnapshot;
/**
 * Execution result containing snapshots and AST
 * 
 */
public class ExecutionResult {
    
    public final List<MapSnapshot> snapshots;
    public final List<AstNode> ast;
    public ExecutionResult(List<MapSnapshot> snapshots, List<AstNode> ast){
        this.snapshots = snapshots;
        this.ast = ast;
    }
    
}
