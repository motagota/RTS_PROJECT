package com.rts.repository;

import com.rts.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByLobbyId(Long lobbyId);

    Optional<Player> findByLobbyIdAndName(Long lobbyId, String name);
}
