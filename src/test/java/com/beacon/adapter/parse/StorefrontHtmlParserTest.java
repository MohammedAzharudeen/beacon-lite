package com.beacon.adapter.parse;

import static org.assertj.core.api.Assertions.assertThat;

import com.beacon.config.AssumptionsLoader;
import com.beacon.testsupport.Fixtures;
import org.junit.jupiter.api.Test;

class StorefrontHtmlParserTest {

  private final StorefrontHtmlParser parser =
      new StorefrontHtmlParser(AssumptionsLoader.packaged().detection());

  @Test
  void home_steveMadden_linksAppsAndCurrency() {
    String html = Fixtures.recorded("stevemadden", "home.html.gz");

    assertThat(parser.currency(html)).isEqualTo("USD");
    assertThat(parser.productHandles(html)).hasSize(2);
    assertThat(parser.apps(html)).contains("SWYM", "KLAVIYO");
    assertThat(parser.scriptCount(html)).isGreaterThan(100);
    assertThat(parser.looksLikeShopify(html)).isTrue();
  }

  @Test
  void freeShipping_steveMaddenAnnouncement_readsAmount() {
    var offer = parser.freeShipping(parser.text(Fixtures.recorded("stevemadden", "home.html.gz")));

    assertThat(offer).isNotNull();
    assertThat(offer.threshold()).isEqualByComparingTo("75");
    assertThat(offer.text()).containsIgnoringCase("free shipping");
  }

  @Test
  void returnDays_reebokFooterPage_thirtyDays() {
    String text = parser.text(Fixtures.recorded("reebok", "page-returns-exchanges.html.gz"));

    assertThat(parser.returnDays(text)).isEqualTo(30);
  }

  @Test
  void returnDays_noDayCount_null() {
    assertThat(parser.returnDays("Returns are accepted. Contact us.")).isNull();
  }

  @Test
  void freeShipping_withoutAmount_keepsTextNoThreshold() {
    var offer = parser.freeShipping("Members get free shipping on everything.");

    assertThat(offer).isNotNull();
    assertThat(offer.threshold()).isNull();
  }

  @Test
  void productPage_steveMaddenSoldOut_notifyMeAndSizeGuide() {
    String html = Fixtures.recorded("stevemadden", "product-page-mona-bone-leather.html.gz");

    assertThat(parser.hasNotifyMeText(html)).isTrue();
    assertThat(parser.hasSizeGuide(html)).isTrue();
  }

  @Test
  void policyPageHandles_reebok_returnsAndShipping() {
    String html = Fixtures.recorded("reebok", "home.html.gz");

    assertThat(parser.policyPageHandles(html))
        .containsExactly("shipping-delivery", "returns-exchanges");
  }
}
