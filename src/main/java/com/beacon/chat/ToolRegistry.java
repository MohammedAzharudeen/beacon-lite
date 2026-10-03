package com.beacon.chat;

import com.beacon.chat.tools.ChatTool;
import com.beacon.chat.tools.ToolContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The 10 read-only tools. The store id comes from the request, never from the model; arguments are
 * parsed defensively.
 */
@Component
public class ToolRegistry {

  private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

  private final Map<String, ChatTool> tools = new LinkedHashMap<>();
  private final List<JsonNode> definitions;
  private final ObjectMapper mapper;

  public ToolRegistry(List<ChatTool> tools, PromptLoader prompts, ObjectMapper mapper) {
    tools.forEach(t -> this.tools.put(t.name(), t));
    this.definitions = prompts.toolDefinitions();
    this.mapper = mapper;
    for (JsonNode def : definitions) {
      if (!this.tools.containsKey(def.path("name").asText())) {
        throw new IllegalStateException("No implementation for tool " + def.path("name").asText());
      }
    }
  }

  public List<JsonNode> definitions() {
    return definitions;
  }

  public boolean has(String name) {
    return tools.containsKey(name);
  }

  /** Runs a tool by name with JSON-text arguments. Unknown tools return an error object. */
  public ToolResult run(String name, String argumentsJson, ToolContext context) {
    ChatTool tool = tools.get(name);
    if (tool == null) {
      return new ToolResult(name, mapper.createObjectNode().put("error", "Unknown tool " + name));
    }
    JsonNode args;
    try {
      args =
          argumentsJson == null || argumentsJson.isBlank()
              ? mapper.createObjectNode()
              : mapper.readTree(argumentsJson);
      if (!args.isObject()) {
        args = mapper.createObjectNode();
      }
    } catch (JsonProcessingException e) {
      args = mapper.createObjectNode();
    }
    log.info("[CHAT_TOOL] store={} tool={} args={}", context.storeId(), name, args);
    return new ToolResult(name, mapper.valueToTree(tool.run(args, context)));
  }
}
