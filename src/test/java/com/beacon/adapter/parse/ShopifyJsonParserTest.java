package com.beacon.adapter.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.beacon.common.BeaconException;
import com.beacon.testsupport.Fixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class ShopifyJsonParserTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final ShopifyJsonParser parser = new ShopifyJsonParser(mapper);

  @Test
  void parseProducts_realPage_allProductsRead() {
    var page = parser.parseProducts(Fixtures.recorded("stevemadden", "products-page-001.gz"));

    assertThat(page.products()).hasSize(250);
    assertThat(page.skipped()).isZero();
    assertThat(page.products()).allSatisfy(p -> assertThat(p.descriptionLength()).isNotNegative());
  }

  @Test
  void parseProducts_oneBrokenProduct_skippedNotFatal() throws Exception {
    ObjectNode page =
        (ObjectNode) mapper.readTree(Fixtures.recorded("reebok", "products-page-001.gz"));
    ((ObjectNode) ((ArrayNode) page.get("products")).get(0)).remove("id");

    var parsed = parser.parseProducts(mapper.writeValueAsString(page));

    assertThat(parsed.skipped()).isEqualTo(1);
    assertThat(parsed.products()).hasSize(249);
  }

  @Test
  void parseProducts_htmlInsteadOfJson_parseFailed() {
    assertThatThrownBy(
            () -> parser.parseProducts(Fixtures.recorded("meshki", "products-page-001.gz")))
        .isInstanceOf(BeaconException.class);
    assertThat(parser.isProductFeed(Fixtures.recorded("meshki", "products-page-001.gz"))).isFalse();
  }

  @Test
  void parseAltText_productJson_countsImages() {
    var count =
        parser.parseAltText(
            Fixtures.recorded("stevemadden", "product-json-lanti-black-leather.gz"));

    assertThat(count.images()).isPositive();
    assertThat(count.withAlt()).isBetween(0, count.images());
  }

  @Test
  void parseSearch_realSuggestResponse() {
    var probe =
        parser.parseSearch(Fixtures.recorded("stevemadden", "search-1.gz"), "women's shoes");

    assertThat(probe.resultCount()).isPositive();
    assertThat(probe.relevantCount()).isBetween(0, probe.resultCount());
    assertThat(probe.topTitles()).hasSizeLessThanOrEqualTo(3);
  }
}
