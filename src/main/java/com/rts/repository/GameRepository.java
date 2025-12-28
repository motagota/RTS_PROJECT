package com.rts.repository;

import com.rts.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {
    Optional<Game> findByLobbyId(Long lobbyId);
    Optional<Game> findByStatus(String status);
}
