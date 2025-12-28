package com.rts.model;

public enum Terrain {
    NONE,
    GRASS,
    WATER,
    DESERT,
    DIRT,
    FOREST,
    SNOW,
    BEACH,
    ROCK,
    GOLD,
    STONE,
    ROAD;


    public String getColor(){
        return switch(this){
            case GRASS ->   "#ff0000";
            case WATER ->   "#00ff00";
            case DESERT ->  "#ff0000";
            case DIRT ->    "#00ff00";
            case FOREST ->  "#00ff00";
            case SNOW ->    "#00ff00";
            case BEACH ->   "#00ff00";
            case ROCK ->    "#00ff00";
            case GOLD ->    "#00ff00";
            case STONE ->   "#00ff00";
            case ROAD ->    "#00ff00";
            case NONE ->    "#ffffff";
        };

    }

    public boolean isWalkable(){
        return this!=WATER;
    }

    public boolean isResource(){
        return this == GOLD || this == STONE;
    }

    public int getCost(){
        return switch (this){
            case ROAD->1;
            case GRASS, DIRT, DESERT, SNOW,BEACH->2;
            case FOREST->4;
            case ROCK->6;
            case GOLD, STONE->8;
            case WATER ->999;
            case NONE ->999;
        };
    }
    /**
     * parse terrain from string case-insensitve
     * @param name
     * @return
     */
    public static Terrain fromString(String name)
    {
        if ( name == null ) return NONE;

        try{
            return Terrain.valueOf(name.toUpperCase());
        } catch ( IllegalArgumentException e ){
            return switch ( name.toUpperCase()){
                case "NONE" -> NONE;
                case "GRASS" -> GRASS;
                case "WATER" -> WATER;
                case "DESERT" -> DESERT;
                case "DIRT" -> DIRT;
                case "FOREST" -> FOREST;
                case "SNOW" -> SNOW;
                case "BEACH" -> BEACH;
                case "ROCK" -> ROCK;
                case "GOLD" -> GOLD;
                case "STONE" -> STONE;
                case "ROAD" -> ROAD;
                default -> {
                    System.err.println("Invalid Terrain Name: " + name);
                    yield NONE;
                }

            };
        }
    }

    public int getTerrainId(){
        return switch(this){
            case GRASS -> 0;
            case WATER -> 1;
            case BEACH -> 2;
            case DIRT -> 3;
            case DESERT -> 9;
            case FOREST -> 10;
            case GOLD -> 11;
            case STONE -> 12;
            case SNOW ->26;
            case ROCK -> 38;
            case ROAD -> 24;
            case NONE -> 999;
        };
    }

    public boolean canBeReplacedBy(Terrain newTerrain) {
        if (this.isResource()){
            return false;
        }

        if( this == WATER){
            return newTerrain == BEACH;
        }

        return true;
    }

    public String getDisplayName(){
        return switch (this){
            case GRASS -> "Grass";
            case WATER -> "Water";
            case DESERT -> "Desert";
            case DIRT -> "Dirt";
            case FOREST -> "Forest";
            case SNOW -> "Snow";
            case BEACH -> "Beach";
            case ROCK -> "Rock";
            case GOLD -> "Gold";
            case STONE -> "Stone";
            case ROAD -> "Road";
            case NONE -> "None";
        };
    }
}
