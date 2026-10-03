package com.beacon.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Loads the system prompt and tool definitions from {@code src/main/resources/prompts/}. */
@Component
public class PromptLoader {

  private final String systemPrompt;
  private final List<JsonNode> toolDefinitions;

  public PromptLoader() {
    this.systemPrompt = read("/prompts/system.md");
    try {
      JsonNode defs = new ObjectMapper().readTree(read("/prompts/tools.json"));
      List<JsonNode> list = new ArrayList<>();
      defs.forEach(list::add);
      this.toolDefinitions = List.copyOf(list);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public String systemPrompt() {
    return systemPrompt;
  }

  /** Tool definitions as written in tools.json: name, description, parameters. */
  public List<JsonNode> toolDefinitions() {
    return toolDefinitions;
  }

  private static String read(String path) {
    try (InputStream in = PromptLoader.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Missing prompt file " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
