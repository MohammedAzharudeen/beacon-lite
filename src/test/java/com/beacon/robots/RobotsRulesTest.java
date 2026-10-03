package com.beacon.robots;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RobotsRulesTest {

  private static final String TOKEN = "BeaconLite";

  @ParameterizedTest
  @CsvSource({
    "stevemadden, /policies/refund-policy, true",
    "stevemadden, /search?q=boots, true",
    "reebok, /policies/refund-policy, false",
    "reebok, /search?q=shoes, false",
    "reebok, /collections/shoes+men, false",
    "meshki, /policies/refund-policy, false",
    "meshki, /search/suggest.json?q=dress, false",
  })
  void decide_recordedStoreFile_matchesStoreRules(String store, String path, boolean allowed) {
    assertThat(fixture(store).decide(path).allowed()).isEqualTo(allowed);
  }

  @ParameterizedTest
  @ValueSource(strings = {"stevemadden", "reebok", "meshki"})
  void decide_bestSellingSort_disallowedOnAllStores(String store) {
    RobotsDecision decision = fixture(store).decide("/collections/all?sort_by=best-selling");

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.rule()).contains("sort_by");
  }

  @ParameterizedTest
  @ValueSource(strings = {"stevemadden", "reebok", "meshki"})
  void decide_productsFeed_allowedOnAllStores(String store) {
    RobotsRules rules = fixture(store);

    assertThat(rules.decide("/products.json?limit=250&page=1").allowed()).isTrue();
    assertThat(rules.decide("/products/some-shoe.js").allowed()).isTrue();
    assertThat(rules.decide("/collections.json").allowed()).isTrue();
  }

  @Test
  void decide_longerAllowBeatsShorterDisallow() {
    RobotsRules rules = RobotsRules.parse("User-agent: *\nDisallow: /a\nAllow: /a/b\n", TOKEN);

    assertThat(rules.decide("/a/b/c").allowed()).isTrue();
    assertThat(rules.decide("/a/x").allowed()).isFalse();
  }

  @Test
  void decide_equalLengthTie_allowWins() {
    RobotsRules rules = RobotsRules.parse("User-agent: *\nDisallow: /page\nAllow: /page\n", TOKEN);

    assertThat(rules.decide("/page").allowed()).isTrue();
  }

  @Test
  void decide_dollarAnchor_matchesOnlyExactEnd() {
    RobotsRules rules = RobotsRules.parse("User-agent: *\nDisallow: /*.json$\n", TOKEN);

    assertThat(rules.decide("/cart.json").allowed()).isFalse();
    assertThat(rules.decide("/cart.json?x=1").allowed()).isTrue();
  }

  @Test
  void parse_ownGroupPresent_ignoresStarGroup() {
    String text = "User-agent: *\nDisallow: /\n\nUser-agent: beaconlite\nDisallow: /private\n";
    RobotsRules rules = RobotsRules.parse(text, TOKEN);

    assertThat(rules.decide("/products.json").allowed()).isTrue();
    assertThat(rules.decide("/private/x").allowed()).isFalse();
  }

  @Test
  void parse_starGroupsSplitAcrossFile_areMerged() {
    String text =
        "User-agent: *\nDisallow: /a\n\nUser-agent: other\nDisallow: /b\n\n"
            + "User-agent: *\nDisallow: /c\n";
    RobotsRules rules = RobotsRules.parse(text, TOKEN);

    assertThat(rules.decide("/a").allowed()).isFalse();
    assertThat(rules.decide("/b").allowed()).isTrue();
    assertThat(rules.decide("/c").allowed()).isFalse();
  }

  @Test
  void parse_emptyDisallow_allowsEverything() {
    assertThat(RobotsRules.parse("User-agent: *\nDisallow:\n", TOKEN).decide("/x").allowed())
        .isTrue();
  }

  @Test
  void disallowAll_stillAllowsRobotsFile() {
    RobotsRules rules = RobotsRules.disallowAll();

    assertThat(rules.decide("/products.json").allowed()).isFalse();
    assertThat(rules.decide("/robots.txt").allowed()).isTrue();
  }

  private static RobotsRules fixture(String store) {
    String path = "/fixtures/" + store + "/robots.txt";
    try (InputStream in = RobotsRulesTest.class.getResourceAsStream(path)) {
      assertThat(in).as(path).isNotNull();
      return RobotsRules.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), TOKEN);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
