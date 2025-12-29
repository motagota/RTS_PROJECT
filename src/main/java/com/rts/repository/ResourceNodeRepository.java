package com.rts.repository;

import com.rts.model.ResourceNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceNodeRepository extends JpaRepository<ResourceNode, Long> {

    /**
     * Find all resource nodes for a specific game
     */
    List<ResourceNode> findByGameId(Long gameId);

    /**
     * Find resource node at specific coordinates
     */
    Optional<ResourceNode> findByGameIdAndXAndY(Long gameId, int x, int y);

    /**
     * Find all non-depleted resource nodes for a game
     */
    List<ResourceNode> findByGameIdAndDepletedFalse(Long gameId);

    /**
     * Delete all resource nodes for a game
     */
    void deleteByGameId(Long gameId);

    /**
     * Count non-depleted resources by type
     */
    @Query("SELECT COUNT(r) FROM ResourceNode r WHERE r.game.id = :gameId AND r.type = :type AND r.depleted = false")
    long countNonDepletedByType(Long gameId, ResourceNode.ResourceType type);
}
