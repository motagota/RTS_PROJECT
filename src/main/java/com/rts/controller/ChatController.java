package com.rts.controller;

import com.rts.model.ChatMessage;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    @MessageMapping("/chat/{lobbyId}")
    @SendTo("/topic/lobby/{lobbyId}")
    public ChatMessage sendMessage(@DestinationVariable Long lobbyId, ChatMessage message) {
        message.setLobbyId(lobbyId);
        return message;
    }
}
