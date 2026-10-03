package com.beacon.chat;

import com.fasterxml.jackson.databind.JsonNode;

/** A tool's output as JSON, the only source of numbers an answer may use. */
public record ToolResult(String toolName, JsonNode data) {}
