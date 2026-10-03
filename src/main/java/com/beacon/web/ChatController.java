package com.beacon.web;

import com.beacon.chat.ChatResponse;
import com.beacon.chat.ChatService;
import com.beacon.web.dto.ChatRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Ask Beacon", description = "Plain-English questions answered from verified data")
public class ChatController {

  private static final Logger log = LoggerFactory.getLogger(ChatController.class);

  private final ChatService chat;

  public ChatController(ChatService chat) {
    this.chat = chat;
  }

  @PostMapping
  @Operation(summary = "Ask a question about a store")
  public ChatResponse ask(@Valid @RequestBody ChatRequest request) {
    log.info("[API] POST /api/chat store={}", request.storeId());
    List<ChatService.Turn> history =
        request.history() == null
            ? List.of()
            : request.history().stream()
                .map(t -> new ChatService.Turn(t.role(), t.content()))
                .toList();
    return chat.ask(request.storeId(), request.message(), history);
  }
}
