package com.rts.service.map.commands;

import com.rts.model.Building;
import com.rts.service.map.MapGenerationContext;
import com.rts.service.map.RMSCommand;

import java.util.Map;

/**
 * RMS Command to place headquarters buildings at player start positions
 * Headquarters are shaped like an 'H' using 4 cells
 */
public class PlaceHeadquartersCommand implements RMSCommand {

    @Override
    public void execute(MapGenerationContext context, Map<String, Object> parameters) {
        if (!context.shouldGenerateHeadquarters()) {
            System.out.println("Headquarters generation is disabled, skipping...");
            return;
        }

        System.out.println("Placing headquarters for " + context.getPlayerStarts().size() + " players");

        for (MapGenerationContext.PlayerStartPosition start : context.getPlayerStarts()) {
            placeHeadquartersAt(context, start.x, start.y, start.playerNumber);
        }

        System.out.println("Placed " + context.getBuildings().size() + " headquarters buildings");
    }

    @Override
    public String getCommandName() {
        return "place_headquarters";
    }

    /**
     * Place a headquarters building at the specified position
     * The headquarters is a 2x2 building represented by a single Building object
     * at the top-left corner. The frontend will render it as a 2x2 area.
     *
     *   X X
     *   X X
     *
     * Where X = building cells covering a 2x2 area
     * The building position (x,y) represents the top-left corner
     */
    private void placeHeadquartersAt(MapGenerationContext context, int centerX, int centerY, int playerNumber) {
        // Place a single 2x2 headquarters building
        // Position it at the top-left of where we want the 2x2 to be
        // Offset by -1, -1 to center it around the start position
        int buildingX = centerX - 1;
        int buildingY = centerY - 1;

        context.addBuilding(new Building(buildingX, buildingY, Building.BuildingType.TOWN_CENTER, playerNumber));

        System.out.println("Placed headquarters for player " + playerNumber + " at (" + buildingX + "," + buildingY + ") covering 2x2 area");
    }
}
