package com.beacon.chat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Any OpenAI-format chat model (Ollama, vLLM, Groq, OpenAI, Claude via a compatible gateway). The
 * same code serves all of them; only base URL, model and key change.
 */
public interface LlmProvider {

  /**
   * @param tools tool definitions in OpenAI function format
   * @throws LlmUnavailableException on connection failure, timeout or an unusable reply
   */
  LlmReply complete(List<ChatMessage> messages, List<JsonNode> tools);

  /** Cheap reachability check, cached briefly. */
  boolean isReachable();

  String provider();

  String model();

  String baseUrl();
}
