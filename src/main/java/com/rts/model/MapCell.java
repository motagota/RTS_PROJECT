package com.rts.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
 * Represents a single tile/cell on the map
 * 
 * Example JSON:
 * {
 * "x":10,
 * "y":15}
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MapCell{

    /**
     * X coordinate ( 0 to width -1 )
     */
    private int x;

    /**
     * Y Coordinate ( 0 to height - 1)
     */
    private int y;

    /**
     * Terrain type at this cell
     * Determines visual appearance, walkability and pathfind cost
     */
    private Terrain terrain;

    /**
     * Elevation/height of this cell
     *
     * - 0 = sea level ( and is the default height
     * - positive values = hills/ elevated terrian
     * - Negative values = water dept
     *
     * used for:
     * - visual rendering ( hill shading)
     * - Gameplay ( high ground advantage)
     * - Water depth indicator
     *
     */
    private int elevation;

    /**
     * Land ID - which land mass this cell belongs to
     * - 0 - no land ( base terrain , not part of any created lands)
     * - 1-8 = Player starting lands ( matches player ID)
     * -9+ neutral lands ( forests , islands etc )
     *
     * used for:
     * - grouping tiles into contiguous land masses
     * - Pathfinding ( connecting lands with roads)
     * - Game logic ( check if palyer owns this land)
     */
    private Integer landId;

    /**
     * Plater owner (1-8 for palyer starting areas, null for neutral)
     *
     * - null = nutral/unowned terrain
     * - 1-8 = owned by a specific player
     *
     * Note: owner is seperate from landId because:
     * - A neutral land ( like a forest ) can have a landId but not owner
     * - Owner determines control/vision, landId determines connectivity
     */
    private Integer owner;

    /**
     * Object placed on this cell ( e.g "GOLD","STONE","TREE")
     *
     * - null = no object
     * - "GOLD = gold mine resource.
     * - "STONE" = stone min resource
     * - "TREE"  = forrest/tree resource
     *
     * objects are placed on top of terrai, they dont replace it.
     * For example: GOLD min on GRASS terrain or TREE on DIRT
     */
    private String object;

    /**
     * Provenance -- which RMS command created/modified this cell
     * used for debuggging and step-by-step visulisation.
     * Shows the history of how this cell got to its current state.
     *
     * Example:
     * -"inital" = base terrain setup
     * - "land:0" = created by first create_land commadn
     * - "create_plater_lands" = player starting area
     * - "create_terrain:2"= painted by 3rd create_Terrain command
     * - "create_object:5" = object placed by 6th command
     *
     */
    private String provenance;

    public MapCell(int x, int y) {

        this.x = x;
        this.y = y;
        rest();

    }


    public MapCell copy(){
        return new MapCell(
                this.x,
                this.y,
                this.terrain,
                this.elevation,
                this.landId,
                this.owner,
                this.object,
                this.provenance);
    }

    public boolean isWalkable(){
        return terrain != null && terrain.isWalkable();
    }

    public int getMovementCost(){
        return terrain!=null ? terrain.getCost():999;
    }

    public boolean hasObject(){
        return object != null && !object.isEmpty();
    }

    public boolean isOwned(){
        return owner != null;
    }

    public boolean isPartOfLand(){
        return landId >0;
    }

    public String getDisplayColor(){
        if ( terrain == null){
            return "#000000";
        }
        return terrain.getColor();
    }

    public String getDescription(){
        StringBuilder sb = new StringBuilder();

        if( terrain != null){
            sb.append(terrain.getDisplayName());
        }

        if( elevation != 0 ){
            sb.append(" [elevation: ]").append(elevation).append("]");
        }

        if(isOwned()){
            sb.append(" [Player ").append(getOwner()).append("]");
        }

        if ( hasObject() ){
            sb.append(" [Object: ").append(getObject()).append("]");
        }
        if( landId >0){
            sb.append(" [Land: ").append(getLandId()).append("]");
        }

        return sb.toString();
    }

    public void rest(){
        this.terrain = null;
        this.elevation = 0;
        this.landId = 0;
        this.owner = null;
        this.object = null;
        this.provenance = "reset";
    }

    @Override
    public String toString(){
        return "MapCell("+x+","+y+":"+
                (terrain!=null ? terrain.name() : "null+")+
                (hasObject() ? "+" + object : "")+ ")";
    }



}