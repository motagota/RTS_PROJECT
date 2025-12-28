package com.rts.repository;

import com.rts.model.Lobby;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LobbyRepository extends JpaRepository<Lobby, Long> {

    List<Lobby> findByStatus(String status);

    List<Lobby> findByIsPrivateFalseAndStatus(String status);

    Optional<Lobby> findByName(String name);

    List<Lobby> findByNameContainingIgnoreCase(String name);
}