package com.beacon.adapter.parse;

import static org.assertj.core.api.Assertions.assertThat;

import com.beacon.testsupport.Fixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JsonLdProductParserTest {

  private final JsonLdProductParser parser = new JsonLdProductParser(new ObjectMapper());

  @Test
  void parse_reebokProductPage_offersWithPriceAndAvailability() {
    String url = "https://www.reebok.com/products/reebok-premier-road-ultra-shoes-black-metallic";
    var product =
        parser.parse(
            Fixtures.recorded(
                "reebok", "product-page-reebok-premier-road-ultra-shoes-black-metallic.html.gz"),
            url);

    assertThat(product).isPresent();
    assertThat(product.get().variants()).isNotEmpty();
    assertThat(product.get().variants()).allSatisfy(v -> assertThat(v.price()).isPositive());
    assertThat(product.get().fullySoldOut()).isTrue();
    assertThat(product.get().externalId()).isEqualTo(JsonLdProductParser.stableId(url));
  }

  @Test
  void parse_pageWithoutProduct_empty() {
    assertThat(parser.parse(Fixtures.recorded("meshki", "home.html.gz"), "https://x/")).isEmpty();
  }
}
