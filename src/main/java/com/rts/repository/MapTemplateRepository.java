package com.rts.repository;

import com.rts.model.MapTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MapTemplateRepository extends JpaRepository<MapTemplate, Long> {
    Optional<MapTemplate> findByName(String name);
}
