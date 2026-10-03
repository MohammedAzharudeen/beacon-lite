package com.beacon.insight;

import com.beacon.insight.report.InsightReport;
import com.beacon.journey.JourneyCheck;
import com.beacon.journey.JourneyStage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Store comparison. Journey scores are recomputed using only checks that ran on every compared
 * store, so a check skipped on one store (e.g. blocked by robots.txt) never changes the others'
 * comparison score.
 */
@Service
public class BenchmarkService {

  /** One store's row in the comparison. */
  public record StoreMetrics(
      long storeId,
      String domain,
      String currency,
      double sizesSoldOutPct,
      String atRiskPerWeek,
      boolean atRiskIsEstimate,
      Integer comparableJourneyScore,
      int compareAtEqualsPrice,
      Boolean wishlistApp,
      Boolean notifyMe,
      int preOrderExcluded,
      Integer returnsDays,
      String searchTest) {}

  /** The comparison and the checks its journey scores are based on. */
  public record Benchmark(List<StoreMetrics> stores, List<String> commonChecks) {}

  public Benchmark compare(List<InsightReport> reports) {
    Set<String> common = null;
    for (InsightReport r : reports) {
      Set<String> ran = new HashSet<>();
      r.journey()
          .forEach(
              s -> s.checks().stream().filter(JourneyCheck::ran).forEach(c -> ran.add(c.key())));
      if (common == null) {
        common = ran;
      } else {
        common.retainAll(ran);
      }
    }
    Set<String> commonChecks = common == null ? Set.of() : common;
    List<StoreMetrics> rows = new ArrayList<>();
    for (InsightReport r : reports) {
      rows.add(
          new StoreMetrics(
              r.storeId(),
              r.domain(),
              r.currency(),
              r.kpis().sizesSoldOutPct(),
              r.kpis().atRiskPerWeek().amount(),
              true,
              comparableScore(r, commonChecks),
              r.pricing().compareAtEqualsPrice(),
              checkPassed(r, "WISHLIST_APP"),
              checkPassed(r, "NOTIFY_ME"),
              r.exclusions().stream()
                  .filter(e -> e.reason() == ExclusionReason.PRE_ORDER)
                  .mapToInt(e -> e.count())
                  .sum(),
              returnsDays(r),
              check(r, "SEARCH_TEST").map(c -> c.status().name()).orElse("NOT_AVAILABLE")));
    }
    return new Benchmark(rows, commonChecks.stream().sorted().toList());
  }

  /** Mean of stage means, using only the given checks. */
  static Integer comparableScore(InsightReport report, Set<String> checks) {
    List<Double> stageScores = new ArrayList<>();
    for (JourneyStage stage : report.journey()) {
      List<Integer> scores =
          stage.checks().stream()
              .filter(c -> c.ran() && checks.contains(c.key()))
              .map(JourneyCheck::score)
              .toList();
      if (!scores.isEmpty()) {
        stageScores.add(scores.stream().mapToInt(Integer::intValue).average().orElse(0));
      }
    }
    return stageScores.isEmpty()
        ? null
        : (int) Math.round(stageScores.stream().mapToDouble(d -> d).average().orElse(0));
  }

  private static Boolean checkPassed(InsightReport r, String key) {
    return check(r, key).filter(JourneyCheck::ran).map(c -> c.score() > 0).orElse(null);
  }

  private static Integer returnsDays(InsightReport r) {
    return check(r, "RETURNS_WINDOW")
        .filter(JourneyCheck::ran)
        .map(
            c -> {
              String digits = c.evidence().replaceFirst("^(\\d+)-day.*", "$1");
              return digits.chars().allMatch(Character::isDigit) ? Integer.valueOf(digits) : null;
            })
        .orElse(null);
  }

  private static Optional<JourneyCheck> check(InsightReport r, String key) {
    return r.journey().stream()
        .flatMap(s -> s.checks().stream())
        .filter(c -> c.key().equals(key))
        .findFirst();
  }
}
