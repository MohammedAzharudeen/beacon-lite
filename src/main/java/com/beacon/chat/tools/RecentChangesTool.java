package com.beacon.chat.tools;

import com.beacon.diff.ChangeType;
import com.beacon.diff.ChangeView;
import com.beacon.diff.ChangesService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RecentChangesTool implements ChatTool {

  private static final int MAX = 15;

  private final ChangesService changes;

  public RecentChangesTool(ChangesService changes) {
    this.changes = changes;
  }

  @Override
  public String name() {
    return "get_recent_changes";
  }

  @Override
  public Object run(JsonNode args, ToolContext ctx) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (ctx.report().kpis().changedSinceLastCheck() == null) {
      out.put("status", "NOT_AVAILABLE");
      out.put(
          "reason",
          "Only one check so far; changes are available after the next check (about 6 hours later)");
      return out;
    }
    ChangeType type = parse(args.path("type").asText(null));
    List<ChangeView> recent = changes.recent(ctx.storeId(), type, MAX * 4).events();
    // Only the latest snapshot window: "since the last check"
    List<ChangeView> latest =
        recent.isEmpty()
            ? List.of()
            : recent.stream().filter(e -> e.windowEnd().equals(recent.get(0).windowEnd())).toList();
    out.put(
        "window",
        latest.isEmpty()
            ? null
            : Map.of("start", latest.get(0).windowStart(), "end", latest.get(0).windowEnd()));
    out.put("counts", ctx.report().kpis().changedSinceLastCheck());
    out.put(
        "changes",
        latest.stream()
            .limit(MAX)
            .map(
                e -> {
                  Map<String, Object> row = new LinkedHashMap<>();
                  row.put("type", e.type());
                  row.put("title", ChatTool.text(e.productTitle()));
                  row.put("size", e.size());
                  row.put("from", e.oldValue());
                  row.put("to", e.newValue());
                  return row;
                })
            .toList());
    return out;
  }

  private static ChangeType parse(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return ChangeType.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
