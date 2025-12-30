package com.rts.repository;

import com.rts.model.GamePlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GamePlayerRepository extends JpaRepository<GamePlayer, Long> {
    List<GamePlayer> findByGameId(Long gameId);
    Optional<GamePlayer> findByGameIdAndPlayerName(Long gameId, String playerName);
    Optional<GamePlayer> findByGameIdAndPlayerSlot(Long gameId, Integer playerSlot);
}
