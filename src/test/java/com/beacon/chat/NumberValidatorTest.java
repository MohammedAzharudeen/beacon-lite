package com.beacon.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class NumberValidatorTest {

  private final NumberValidator validator = new NumberValidator();
  private final ObjectMapper mapper = new ObjectMapper();

  private ToolResult result(String json) throws Exception {
    return new ToolResult("t", mapper.readTree(json));
  }

  @Test
  void currencyRoundedToWhole_ok() throws Exception {
    var v =
        validator.check(
            "About $2,089 a week is at risk.", List.of(result("{\"amount\":\"2089.48\"}")));
    assertThat(v.ok()).isTrue();
    assertThat(v.verified()).isEqualTo(1);
  }

  @Test
  void percentWithinLastDigit_ok_otherwiseFails() throws Exception {
    ToolResult r = result("{\"pct\":33.9}");
    assertThat(validator.check("33.9% sold out", List.of(r)).ok()).isTrue();
    assertThat(validator.check("34% sold out", List.of(r)).ok()).isTrue();
    assertThat(validator.check("36% sold out", List.of(r)).ok()).isFalse();
  }

  @Test
  void sizeLabels_notTreatedAsQuantities() throws Exception {
    var v =
        validator.check(
            "Sizes 9, 9.5 and 10 are sold out; 4 products affected.",
            List.of(result("{\"count\":4}")));
    assertThat(v.ok()).isTrue();
  }

  @Test
  void numbersInProductTitles_neitherFactsNorChecked() throws Exception {
    ToolResult r = result("{\"title\":\"Club C 85 Vintage\",\"count\":2}");
    assertThat(validator.check("Club C 85 Vintage has 2 sizes left.", List.of(r)).ok()).isTrue();
    assertThat(validator.check("85 sizes are sold out.", List.of(r)).ok()).isFalse();
  }

  @Test
  void datesMustMatchExactly() throws Exception {
    ToolResult r = result("{\"start\":\"2026-10-03T08:00:00Z\"}");
    assertThat(validator.check("Between 2026-10-03T08:00:00Z and now.", List.of(r)).ok()).isTrue();
    assertThat(validator.check("On 2026-10-04 it sold out.", List.of(r)).ok()).isFalse();
  }

  @Test
  void listNumbering_ignored() throws Exception {
    assertThat(validator.check("1. First\n2. Second", List.of(result("{}"))).ok()).isTrue();
  }
}
