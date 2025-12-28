package com.rts.dto;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

@Data
public class AstNode {
    
    /**
     * command type
     * Examples: "create_player_lands",  "create_land", "create_terrain
     */
    private String type;

    /**
     * Command attributes as key-value pairs
     * Examples:
     * - "terrain_type" -> "GRASS"
     * - "land_percent" -> 30
     * - "base_size" -> 10
     * - "number_of_tiles" ->? 500
     */
    private Map<String, Object> attributes;

    public AstNode(String type){
        this.type = type;
        this.attributes = new HashMap<>();

    }

    public void addAttribute(String key, Object value) {
        if( attributes == null){
            attributes = new HashMap<>();
        }

        // Safety check to prevent null keys
        if (key == null) {
            System.err.println("WARNING: Attempted to add null key to AstNode of type: " + type);
            System.err.println("Stack trace:");
            new Exception().printStackTrace();
            return;
        }

        attributes.put(key,value);
    }

    public Object getAttribute(String key) {
        return attributes != null ?  attributes.get(key) : null;
    }

    public Boolean getAttributeAsBoolean(String key) {

        Object value = getAttribute(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }

        return null;
    }

    public Integer getAttributeAsInt(String key) {
        Object value = getAttribute(key);
        if ( value instanceof Number ) {
           return ((Number) value).intValue();

        }
        return null;
    }

    
}
