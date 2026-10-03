package com.beacon.chat;

/** Who produced a chat answer: the model, the rule-based router, or the scope guard. */
public enum ChatProvider {
  LLM,
  RULES,
  SCOPE
}
