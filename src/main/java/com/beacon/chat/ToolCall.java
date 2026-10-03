package com.beacon.chat;

/**
 * A tool the model asked to run.
 *
 * @param arguments JSON object as text, exactly as the model sent it
 */
public record ToolCall(String id, String name, String arguments) {}
