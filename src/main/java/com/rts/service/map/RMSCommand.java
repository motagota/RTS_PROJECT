package com.rts.service.map;

import java.util.Map;

/**
 * RMS (Random Map Script) Command interface
 * Commands are executed to generate map features
 */
public interface RMSCommand {
    void execute(MapGenerationContext context, Map<String, Object> parameters);
    String getCommandName();
}
