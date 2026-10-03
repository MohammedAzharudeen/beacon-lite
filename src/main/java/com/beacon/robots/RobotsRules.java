package com.beacon.robots;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Parsed robots.txt rules for one user agent, following RFC 9309.
 *
 * <ul>
 *   <li>All groups naming our product token are merged; if none exist, all {@code *} groups are
 *       merged (real files repeat {@code User-agent: *}, e.g. Reebok).
 *   <li>Blank lines and comments never end a group (Meshki has blank lines inside its group).
 *   <li>{@code *} matches any characters and a trailing {@code $} anchors the end; other
 *       characters, including {@code [}, match literally.
 *   <li>The longest matching rule wins; on a tie, Allow wins. {@code /robots.txt} is always
 *       allowed.
 * </ul>
 */
public final class RobotsRules {

  private static final RobotsRules ALLOW_ALL = new RobotsRules(List.of(), List.of());
  private static final RobotsRules DISALLOW_ALL =
      new RobotsRules(List.of(new Rule(false, "/", Pattern.compile("/"))), List.of());

  private final List<Rule> rules;
  private final List<String> sitemaps;

  private RobotsRules(List<Rule> rules, List<String> sitemaps) {
    this.rules = List.copyOf(rules);
    this.sitemaps = List.copyOf(sitemaps);
  }

  /** Sitemap URLs listed in the file (they apply to every user agent). */
  public List<String> sitemaps() {
    return sitemaps;
  }

  /** Used when robots.txt doesn't exist (4xx): everything is allowed. */
  public static RobotsRules allowAll() {
    return ALLOW_ALL;
  }

  /** Used when robots.txt can't be read (5xx, timeout): nothing is fetched. */
  public static RobotsRules disallowAll() {
    return DISALLOW_ALL;
  }

  /**
   * Parses a robots.txt file for the given product token (e.g. {@code BeaconLite}).
   *
   * @param text the file content
   * @param productToken our user-agent product token, matched case-insensitively
   */
  public static RobotsRules parse(String text, String productToken) {
    String token = productToken.toLowerCase(Locale.ROOT);
    List<Rule> ownRules = new ArrayList<>();
    List<Rule> starRules = new ArrayList<>();
    List<String> sitemaps = new ArrayList<>();
    boolean ownFound = false;

    List<String> currentAgents = new ArrayList<>();
    boolean lastWasAgent = false;
    for (String rawLine : text.split("\\r?\\n|\\r")) {
      String line = stripComment(rawLine).trim();
      int colon = line.indexOf(':');
      if (line.isEmpty() || colon < 0) {
        continue;
      }
      String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
      String value = line.substring(colon + 1).trim();
      if (key.equals("user-agent")) {
        if (!lastWasAgent) {
          currentAgents = new ArrayList<>();
        }
        currentAgents.add(value.toLowerCase(Locale.ROOT));
        lastWasAgent = true;
        continue;
      }
      if (key.equals("sitemap")) {
        // Sitemap lines are global and don't affect groups; keep the raw URL (it may contain '#')
        String url = rawLine.substring(rawLine.indexOf(':') + 1).trim();
        if (!url.isEmpty()) {
          sitemaps.add(url);
        }
        continue;
      }
      lastWasAgent = false;
      boolean isAllow = key.equals("allow");
      if (!isAllow && !key.equals("disallow")) {
        continue; // Sitemap, Crawl-delay and unknown keys don't affect access
      }
      if (value.isEmpty()) {
        continue; // "Disallow:" with no path allows everything
      }
      Rule rule = Rule.of(isAllow, value);
      if (currentAgents.contains(token)) {
        ownRules.add(rule);
        ownFound = true;
      }
      if (currentAgents.contains("*")) {
        starRules.add(rule);
      }
    }
    return new RobotsRules(ownFound ? ownRules : starRules, sitemaps);
  }

  /**
   * Decides whether a path (with query string) may be fetched.
   *
   * @param pathAndQuery e.g. {@code /products.json?limit=250&page=2}
   */
  public RobotsDecision decide(String pathAndQuery) {
    String path = pathAndQuery.isEmpty() ? "/" : pathAndQuery;
    if (path.equals("/robots.txt")) {
      return RobotsDecision.allowedByDefault();
    }
    Rule best = null;
    for (Rule rule : rules) {
      if (rule.matches(path) && (best == null || rule.beats(best))) {
        best = rule;
      }
    }
    return best == null
        ? RobotsDecision.allowedByDefault()
        : new RobotsDecision(best.allow(), best.display());
  }

  private static String stripComment(String line) {
    int hash = line.indexOf('#');
    return hash < 0 ? line : line.substring(0, hash);
  }

  private record Rule(boolean allow, String value, Pattern pattern) {

    static Rule of(boolean allow, String value) {
      return new Rule(allow, value, compile(value));
    }

    boolean matches(String path) {
      return pattern.matcher(path).lookingAt();
    }

    boolean beats(Rule other) {
      if (value.length() != other.value.length()) {
        return value.length() > other.value.length();
      }
      return allow && !other.allow;
    }

    String display() {
      return (allow ? "Allow: " : "Disallow: ") + value;
    }

    private static Pattern compile(String value) {
      boolean anchored = value.endsWith("$");
      String body = anchored ? value.substring(0, value.length() - 1) : value;
      StringBuilder regex = new StringBuilder();
      for (String part : body.split("\\*", -1)) {
        if (!regex.isEmpty()) {
          regex.append(".*");
        }
        regex.append(Pattern.quote(part));
      }
      if (anchored) {
        regex.append('$');
      }
      return Pattern.compile(regex.toString());
    }
  }
}
