package com.beacon.cli;

import com.beacon.config.Assumptions;
import com.beacon.insight.report.Action;
import com.beacon.insight.report.InsightReport;
import com.beacon.insight.report.RestockRow;
import com.beacon.insight.report.SizeCell;
import com.beacon.insight.report.SizeGapRow;
import com.beacon.journey.JourneyStage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.web.util.HtmlUtils;

/**
 * The one-page Insight Brief: headline, evidence, what to do and expected impact, as Markdown and
 * as a print-ready HTML page ("Save as PDF" in the browser).
 */
public final class InsightBriefWriter {

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH)
          .withZone(ZoneOffset.UTC);
  private static final int EVIDENCE_ROWS = 5;

  private InsightBriefWriter() {}

  public static String markdown(InsightReport r, String storeName, Assumptions a) {
    StringBuilder md = new StringBuilder();
    md.append("# Insight Brief · ").append(storeName).append("\n\n");
    md.append("Data captured ")
        .append(DATE.format(r.capturedAt()))
        .append(" from ")
        .append(r.domain())
        .append(" · snapshot ")
        .append(r.snapshotId())
        .append(" · assumptions ")
        .append(r.assumptionsVersion(), 0, 12)
        .append("\n\n");
    md.append("## Headline\n\n").append(r.headline().text()).append("\n\n");
    md.append("## Evidence\n\n");
    md.append("| Product | Sizes left | Missing core sizes |\n|---|---|---|\n");
    for (SizeGapRow g :
        r.sizeGaps().stream().filter(g -> g.missingCore() > 0).limit(EVIDENCE_ROWS).toList()) {
      md.append("| [")
          .append(escapeMd(g.title()))
          .append("](")
          .append(g.productUrl())
          .append(") | ")
          .append(g.totalSizes() - g.soldOutSizes())
          .append(" of ")
          .append(g.totalSizes())
          .append(" | ")
          .append(String.join(", ", missingCore(g.sizes())))
          .append(" |\n");
    }
    md.append("\nSizes sold out across the catalog: ")
        .append(r.kpis().sizesSoldOutPct())
        .append("% (")
        .append(count(r.kpis().soldOutVariants()))
        .append(" of ")
        .append(count(r.catalog().variants()))
        .append(" variants).\n\n");
    md.append("## Do this\n\n");
    for (Action action : r.topActions()) {
      md.append(action.rank())
          .append(". **")
          .append(escapeMd(action.title()))
          .append("** · ")
          .append(action.category());
      if (action.atRiskPerWeek() != null) {
        md.append(" · est. ")
            .append(money(action.atRiskPerWeek().amount(), action.atRiskPerWeek().currency()))
            .append("/week");
      }
      md.append("  \n   ").append(escapeMd(action.evidence())).append('\n');
    }
    md.append("\n## Expected impact (estimate)\n\n");
    List<RestockRow> top = r.restock().stream().limit(EVIDENCE_ROWS).toList();
    BigDecimal topTotal =
        top.stream()
            .map(x -> new BigDecimal(x.atRiskPerWeek().amount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    md.append("Restocking the ")
        .append(top.size())
        .append(" highest-ranked restock candidates recovers about ")
        .append(money(topTotal.toPlainString(), r.currency()))
        .append(" a week; all ")
        .append(count(r.restockTotal()))
        .append(" restock candidates together: about ")
        .append(money(r.kpis().atRiskPerWeek().amount(), r.currency()))
        .append(" a week.\n\n");
    md.append("Estimate = price × demand score × ")
        .append(a.demand().weeklyDemandBaselineUnits())
        .append(" units/week at full demand × share of sizes sold out (core sizes weigh ")
        .append(a.gapWeights().core())
        .append(
            "×). Demand comes from public stand-ins: best-seller collections, promotion, sell-out speed, full-price sell-through, recent launch. With Swym intent data (wishlist adds, back-in-stock signups) these become measured.\n\n");
    md.append("## Journey scorecard\n\n");
    for (JourneyStage s : r.journey()) {
      md.append("- ")
          .append(s.label())
          .append(": ")
          .append(s.score() == null ? "not scored" : s.score())
          .append(" (based on ")
          .append(s.checksRun())
          .append(" of ")
          .append(s.checksTotal())
          .append(" checks)\n");
    }
    md.append("\n## Data notes\n\n");
    r.dataNotes().forEach(n -> md.append("- ").append(n).append('\n'));
    return md.toString();
  }

  public static String html(InsightReport r, String storeName, Assumptions a) {
    StringBuilder h = new StringBuilder();
    h.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
        .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        .append("<title>Insight Brief · ")
        .append(e(storeName))
        .append("</title><style>")
        .append(
            "body{font-family:Inter,system-ui,-apple-system,'Segoe UI',sans-serif;color:#111827;max-width:820px;margin:32px auto;padding:0 24px;line-height:1.5}")
        .append(
            "h1{font-size:24px;margin:0 0 4px}h2{font-size:16px;margin:28px 0 8px;text-transform:uppercase;letter-spacing:.04em;color:#4338ca}")
        .append(
            ".meta{color:#6b7280;font-size:13px}.headline{font-size:20px;font-weight:600;background:#eef2ff;border-left:4px solid #4f46e5;padding:16px;border-radius:8px}")
        .append(
            "table{border-collapse:collapse;width:100%;font-size:14px}th,td{text-align:left;padding:6px 8px;border-bottom:1px solid #e5e7eb}")
        .append(
            "a{color:#4338ca}.badge{display:inline-block;font-size:11px;padding:1px 6px;border-radius:999px;background:#fef3c7;color:#92400e;margin-left:6px}")
        .append(
            "ol li{margin-bottom:8px}.small{font-size:13px;color:#4b5563}@media print{body{margin:0}a{color:#111827;text-decoration:none}}")
        .append("</style></head><body>");
    h.append("<h1>Insight Brief · ")
        .append(e(storeName))
        .append("</h1><div class=\"meta\">Data captured ")
        .append(e(DATE.format(r.capturedAt())))
        .append(" from ")
        .append(e(r.domain()))
        .append(" · snapshot ")
        .append(r.snapshotId())
        .append(" · assumptions ")
        .append(e(r.assumptionsVersion().substring(0, 12)))
        .append("</div>");
    h.append("<h2>Headline</h2><p class=\"headline\">")
        .append(e(r.headline().text()))
        .append("</p>");
    h.append(
        "<h2>Evidence</h2><table><tr><th>Product</th><th>Sizes left</th><th>Missing core sizes</th></tr>");
    for (SizeGapRow g :
        r.sizeGaps().stream().filter(g -> g.missingCore() > 0).limit(EVIDENCE_ROWS).toList()) {
      h.append("<tr><td><a href=\"")
          .append(e(g.productUrl()))
          .append("\">")
          .append(e(g.title()))
          .append("</a></td><td>")
          .append(g.totalSizes() - g.soldOutSizes())
          .append(" of ")
          .append(g.totalSizes())
          .append("</td><td>")
          .append(e(String.join(", ", missingCore(g.sizes()))))
          .append("</td></tr>");
    }
    h.append("</table><p class=\"small\">Sizes sold out across the catalog: ")
        .append(r.kpis().sizesSoldOutPct())
        .append("% (")
        .append(count(r.kpis().soldOutVariants()))
        .append(" of ")
        .append(count(r.catalog().variants()))
        .append(" variants).</p>");
    h.append("<h2>Do this</h2><ol>");
    for (Action action : r.topActions()) {
      h.append("<li><strong>")
          .append(e(action.title()))
          .append("</strong> <span class=\"small\">· ")
          .append(e(action.category()));
      if (action.atRiskPerWeek() != null) {
        h.append(" · est. ")
            .append(e(money(action.atRiskPerWeek().amount(), action.atRiskPerWeek().currency())))
            .append("/week<span class=\"badge\">Estimate</span>");
      }
      h.append("</span><br><span class=\"small\">")
          .append(e(action.evidence()))
          .append("</span></li>");
    }
    h.append("</ol><h2>Expected impact <span class=\"badge\">Estimate</span></h2><p>About ")
        .append(e(money(r.kpis().atRiskPerWeek().amount(), r.currency())))
        .append(" a week is at risk across ")
        .append(count(r.restockTotal()))
        .append(" restock candidates.</p><p class=\"small\">Estimate = price × demand score × ")
        .append(a.demand().weeklyDemandBaselineUnits())
        .append(" units/week at full demand × share of sizes sold out (core sizes weigh ")
        .append(a.gapWeights().core())
        .append(
            "×). Demand comes from public stand-ins; with Swym intent data these become measured.</p>");
    h.append(
        "<h2>Journey scorecard</h2><table><tr><th>Stage</th><th>Score</th><th>Coverage</th></tr>");
    for (JourneyStage s : r.journey()) {
      h.append("<tr><td>")
          .append(e(s.label()))
          .append("</td><td>")
          .append(s.score() == null ? "Not scored" : s.score())
          .append("</td><td>")
          .append(s.checksRun())
          .append(" of ")
          .append(s.checksTotal())
          .append(" checks</td></tr>");
    }
    h.append("</table><h2>Data notes</h2><ul class=\"small\">");
    r.dataNotes().forEach(n -> h.append("<li>").append(e(n)).append("</li>"));
    h.append("</ul></body></html>");
    return h.toString();
  }

  private static List<String> missingCore(List<SizeCell> sizes) {
    return sizes.stream().filter(c -> c.core() && !c.available()).map(SizeCell::label).toList();
  }

  /** Whole amount with thousands separators, e.g. "2,089 USD". */
  static String money(String amount, String currency) {
    BigDecimal value = new BigDecimal(amount).setScale(0, RoundingMode.HALF_UP);
    String formatted = NumberFormat.getIntegerInstance(Locale.US).format(value);
    return currency == null ? formatted + " (currency unknown)" : formatted + " " + currency;
  }

  private static String count(long value) {
    return NumberFormat.getIntegerInstance(Locale.US).format(value);
  }

  private static String e(String text) {
    return text == null ? "" : HtmlUtils.htmlEscape(text);
  }

  private static String escapeMd(String text) {
    return text == null ? "" : text.replace("|", "\\|").replace("[", "\\[").replace("]", "\\]");
  }
}
