package com.rts.repository;

import com.rts.model.GeneratedMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GeneratedMapRepository extends JpaRepository<GeneratedMap, Long> {
    Optional<GeneratedMap> findByGameId(Long gameId);
}
