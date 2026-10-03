package com.beacon.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.beacon.snapshot.SnapshotService;
import com.beacon.store.StoreRepository;
import com.beacon.testsupport.DemoStores;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * The golden question set against the rule-based router, on real recorded data: each question must
 * reach the expected tool, and every answer must pass the number check.
 */
@SpringBootTest
@ActiveProfiles("test")
class GoldenQuestionsTest {

  @Autowired ChatService chat;
  @Autowired NumberValidator validator;
  @Autowired StoreRepository stores;
  @Autowired SnapshotService snapshots;
  @MockBean LlmProvider llm;

  long steveMadden;
  long reebok;

  @BeforeEach
  void setUp() {
    when(llm.isReachable()).thenReturn(false);
    steveMadden = DemoStores.steveMadden(stores, snapshots);
    reebok = DemoStores.reebok(stores, snapshots);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "Which products should I restock first?|get_restock_priorities|CALORA BLACK LEATHER",
        "What should I restock first?|get_restock_priorities|estimate",
        "Which boots should I restock?|get_restock_priorities|Restock these first",
        "Which sizes are missing in boots?|get_size_gaps|products are missing core sizes",
        "What core sizes are missing?|get_size_gaps|789 products are missing core sizes",
        "What sold out since yesterday?|get_recent_changes|after the next check",
        "What's trending?|get_recent_changes|few days of snapshots",
        "Compare me with Reebok|compare_stores|www.reebok.com",
        "How do I compare with other stores?|compare_stores|www.stevemadden.com",
        "Which sold-out products are still promoted?|get_promoted_sold_outs|still promoted",
        "Is anything on my home page sold out?|get_promoted_sold_outs|JavaScript",
        "Do I have compare-at price issues?|get_pricing_insights|1342 products have a compare-at price",
        "What's my free shipping threshold?|get_pricing_insights|Free shipping starts at",
        "How many products have few images?|get_catalog_health|14 of 2512 products",
        "Is my alt text okay?|get_catalog_health|0 of 30 sampled products",
        "Where am I losing shoppers?|get_journey_friction|weakest stage",
        "How good is my search?|get_journey_friction|Journey score",
        "What's my returns window?|get_journey_friction|Journey score",
        "Do I have a wishlist?|get_journey_friction|Journey score",
        "Find women's shoes under $100 in size 9|search_products|products match",
        "Show me discounted dresses|search_products|match",
        "List products in stock under $50|search_products|products match",
        "Give me an overview|get_store_overview|789 products are missing core sizes",
        "How is my store doing?|get_store_overview|journey score",
      })
  void routedQuestion_expectedToolAndVerifiedNumbers(
      String question, String tool, String expected) {
    ChatResponse response = chat.ask(steveMadden, question, List.of());

    assertThat(response.provider()).isEqualTo(ChatProvider.RULES);
    assertThat(response.toolsUsed()).containsExactly(tool);
    assertThat(response.answer()).containsIgnoringCase(expected);
    assertThat(response.validatorFallback()).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({
    "What's my conversion rate?",
    "How much revenue did I make last week?",
    "How many units are left of GERONIMO?",
    "How many customers bought boots?",
    "What's my traffic like?",
    "What's my profit margin?",
  })
  void outOfScopeQuestion_honestRefusalNoNumbers(String question) {
    ChatResponse response = chat.ask(steveMadden, question, List.of());

    assertThat(response.provider()).isEqualTo(ChatProvider.SCOPE);
    assertThat(response.answer()).isEqualTo(ScopeGuard.REFUSAL);
    assertThat(response.answer()).doesNotContainPattern("\\d");
    assertThat(response.toolsUsed()).isEmpty();
  }

  @org.junit.jupiter.api.Test
  void robotsBlockedCheck_saysNotCheckedOnThisStore() {
    ChatResponse response = chat.ask(reebok, "How good is my search?", List.of());

    assertThat(response.answer()).contains("not checked on this store; its robots.txt blocks it");
  }
}
