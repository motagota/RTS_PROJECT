package com.rts.repository;

import com.rts.model.ProductionQueueItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductionQueueRepository extends JpaRepository<ProductionQueueItem, Long> {
    List<ProductionQueueItem> findByGameIdAndPlayerNameAndStatus(Long gameId, String playerName, String status);
    List<ProductionQueueItem> findByGameIdAndStatus(Long gameId, String status);
}
