package com.beacon.chat.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class PricingInsightsTool implements ChatTool {

  @Override
  public String name() {
    return "get_pricing_insights";
  }

  @Override
  public Object run(JsonNode args, ToolContext ctx) {
    return ctx.report().pricing();
  }
}
