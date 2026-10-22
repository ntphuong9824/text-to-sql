package com.fsoft.training.conversation;

import com.fsoft.training.auth.CurrentUser;
import com.fsoft.training.conversation.ConversationDtos.ChatRequest;
import com.fsoft.training.conversation.ConversationDtos.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ConversationService service;

    public ChatController(ConversationService service) {
        this.service = service;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, Authentication authentication) {
        return service.ask(CurrentUser.id(authentication), request.conversationId(), request.question());
    }
}
