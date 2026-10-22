package com.fsoft.training.conversation;

import com.fsoft.training.auth.CurrentUser;
import com.fsoft.training.conversation.ConversationDtos.ConversationDetail;
import com.fsoft.training.conversation.ConversationDtos.ConversationSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ConversationSummary> list(Authentication authentication) {
        return service.list(CurrentUser.id(authentication));
    }

    @GetMapping("/{id}")
    public ConversationDetail get(@PathVariable UUID id, Authentication authentication) {
        return service.get(CurrentUser.id(authentication), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
        service.delete(CurrentUser.id(authentication), id);
        return ResponseEntity.noContent().build();
    }
}
