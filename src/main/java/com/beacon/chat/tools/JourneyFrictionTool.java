package com.beacon.chat.tools;

import com.beacon.journey.JourneyStage;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JourneyFrictionTool implements ChatTool {

  @Override
  public String name() {
    return "get_journey_friction";
  }

  @Override
  public Object run(JsonNode args, ToolContext ctx) {
    String stage = args.path("stage").asText("");
    List<JourneyStage> stages =
        ctx.report().journey().stream()
            .filter(s -> stage.isBlank() || s.stage().name().equalsIgnoreCase(stage))
            .toList();
    JourneyStage weakest =
        ctx.report().journey().stream()
            .filter(s -> s.score() != null)
            .min((a, b) -> Integer.compare(a.score(), b.score()))
            .orElse(null);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("overallJourneyScore", ctx.report().journeyScore());
    out.put("weakestStage", weakest == null ? null : weakest.label());
    out.put("stages", stages);
    return out;
  }
}
