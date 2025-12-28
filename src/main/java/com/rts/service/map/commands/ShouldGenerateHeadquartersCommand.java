package com.rts.service.map.commands;

import com.rts.service.map.MapGenerationContext;
import com.rts.service.map.RMSCommand;

import java.util.Map;

/**
 * RMS Command to set whether headquarters should be generated for players
 */
public class ShouldGenerateHeadquartersCommand implements RMSCommand {

    @Override
    public void execute(MapGenerationContext context, Map<String, Object> parameters) {
        boolean shouldGenerate = true; // Default to true

        if (parameters != null && parameters.containsKey("enabled")) {
            Object enabledValue = parameters.get("enabled");
            if (enabledValue instanceof Boolean) {
                shouldGenerate = (Boolean) enabledValue;
            } else if (enabledValue instanceof String) {
                shouldGenerate = Boolean.parseBoolean((String) enabledValue);
            }
        }

        context.setShouldGenerateHeadquarters(shouldGenerate);

        System.out.println("Should generate headquarters: " + shouldGenerate);
    }

    @Override
    public String getCommandName() {
        return "should_generate_headquarters";
    }
}
