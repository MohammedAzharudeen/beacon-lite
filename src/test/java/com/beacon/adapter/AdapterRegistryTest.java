package com.beacon.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.beacon.common.BeaconException;
import com.beacon.common.ErrorCode;
import com.beacon.config.Assumptions;
import com.beacon.config.AssumptionsLoader;
import com.beacon.config.BeaconProperties;
import com.beacon.testsupport.RecordedStoreServer;
import com.beacon.testsupport.TestStores;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdapterRegistryTest {

  private final Assumptions assumptions = AssumptionsLoader.packaged();
  private final ObjectMapper mapper = new ObjectMapper();
  private final AdapterRegistry registry =
      new AdapterRegistry(
          new ShopifyAdapter(mapper, assumptions, Clock.systemUTC()),
          new GenericAdapter(mapper, assumptions, Clock.systemUTC()),
          new ReplayAdapter(Map.of()),
          BeaconProperties.defaults(),
          assumptions);

  @Test
  void detect_steveMadden_shopify() {
    try (RecordedStoreServer server = server("stevemadden")) {
      assertThat(registry.detect(TestStores.session(server.baseUri())).platform())
          .isEqualTo(Platform.SHOPIFY);
    }
  }

  @Test
  void detect_reebok_shopify() {
    try (RecordedStoreServer server = server("reebok")) {
      assertThat(registry.detect(TestStores.session(server.baseUri())).platform())
          .isEqualTo(Platform.SHOPIFY);
    }
  }

  @Test
  void detect_botProtection_storeBlocksAutomation() {
    try (RecordedStoreServer server = server("meshki")) {
      assertThatThrownBy(() -> registry.detect(TestStores.session(server.baseUri())))
          .isInstanceOf(BeaconException.class)
          .extracting(e -> ((BeaconException) e).code())
          .isEqualTo(ErrorCode.STORE_BLOCKS_AUTOMATION);
    }
  }

  @Test
  void forPlatform_returnsMatchingAdapter() {
    assertThat(registry.forPlatform(Platform.SHOPIFY).platform()).isEqualTo(Platform.SHOPIFY);
    assertThat(registry.forPlatform(Platform.GENERIC).platform()).isEqualTo(Platform.GENERIC);
  }

  private static RecordedStoreServer server(String store) {
    return RecordedStoreServer.start(Path.of("src/test/resources/fixtures", store, "recording"));
  }
}
