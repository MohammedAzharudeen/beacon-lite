package com.beacon.size;

import static org.assertj.core.api.Assertions.assertThat;

import com.beacon.adapter.model.OptionData;
import com.beacon.adapter.model.ProductData;
import com.beacon.adapter.model.VariantData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SizeParserTest {

  @ParameterizedTest
  @CsvSource({
    "7, NUMERIC",
    "10.5, NUMERIC",
    "SZ 4, NUMERIC",
    "1.0C, NUMERIC",
    "8.0T, NUMERIC",
    "5.0W, WIDE",
    "11W, WIDE",
    "M 7.5 / W 9, DUAL",
    "XS, LETTER",
    "2XL, LETTER",
    "XLRG, LETTER",
    "SMALL, LETTER",
    "MED, LETTER",
    "ONESZ, ONE_SIZE",
    "One Size, ONE_SIZE",
    "O/S, ONE_SIZE",
    "S/M, COMBO",
    "9-11, COMBO",
    "30/32, COMBO",
    "0.5 oz / 15 ml, VOLUME",
    "3 piece set, SET",
    "Full/Queen, BEDDING",
    "$25, UNKNOWN",
    "S/M-L/XL, UNKNOWN"
  })
  void parse_realStoreValues(String label, SizeKind kind) {
    assertThat(SizeParser.parse(label).kind()).isEqualTo(kind);
  }

  @Test
  void parse_2xlAndXxl_sortEqual() {
    assertThat(SizeParser.parse("2XL").sortKey()).isEqualTo(SizeParser.parse("XXL").sortKey());
  }

  @Test
  void analyse_steveMaddenFeedOrder_sortedBySize() {
    SizeRun run = new SizeAnalyzer(50).analyse(product("Size", "11", "12", "10.5", "11.5", "13"));

    assertThat(run.sizes())
        .extracting(SizeEntry::label)
        .containsExactly("10.5", "11", "11.5", "12", "13");
  }

  @Test
  void analyse_middleHalfIsCore() {
    SizeRun run =
        new SizeAnalyzer(50)
            .analyse(
                product(
                    "Size", "5", "5.5", "6", "6.5", "7", "7.5", "8", "8.5", "9", "9.5", "10",
                    "10.5", "11", "11.5", "12", "13", "14"));

    assertThat(run.sizes())
        .filteredOn(SizeEntry::core)
        .extracting(SizeEntry::label)
        .containsExactly("7", "7.5", "8", "8.5", "9", "9.5", "10", "10.5", "11");
    assertThat(run.weighted()).isTrue();
  }

  @Test
  void analyse_regularAndWide_separateRunsEachWithCore() {
    SizeRun run =
        new SizeAnalyzer(50)
            .analyse(product("Size", "6", "7", "8", "9", "6.0W", "7.0W", "8.0W", "9.0W"));

    assertThat(run.sizes())
        .extracting(SizeEntry::label)
        .containsExactly("6", "7", "8", "9", "6.0W", "7.0W", "8.0W", "9.0W");
    assertThat(run.sizes())
        .filteredOn(SizeEntry::core)
        .extracting(SizeEntry::label)
        .containsExactly("7", "8", "7.0W", "8.0W");
  }

  @Test
  void analyse_oneSize_noCoreGap() {
    SizeRun run = new SizeAnalyzer(50).analyse(product("Size", "ONE SIZE"));

    assertThat(run.hasOnlyOneSize()).isTrue();
    assertThat(run.sizes()).noneMatch(SizeEntry::core);
  }

  @Test
  void analyse_noSizeOption_skipped() {
    assertThat(new SizeAnalyzer(50).analyse(product("Color", "Black")).status())
        .isEqualTo(SizeRun.Status.NO_SIZE_OPTION);
  }

  @Test
  void analyse_unknownValue_notGuessed() {
    assertThat(new SizeAnalyzer(50).analyse(product("Size", "$25", "$50")).status())
        .isEqualTo(SizeRun.Status.UNRECOGNISED);
  }

  @Test
  void analyse_volume_unweighted() {
    SizeRun run = new SizeAnalyzer(50).analyse(product("Size", "1 oz", "2 oz"));

    assertThat(run.weighted()).isFalse();
    assertThat(run.sizes()).noneMatch(SizeEntry::core);
  }

  private static ProductData product(String optionName, String... values) {
    List<VariantData> variants = new ArrayList<>();
    long id = 1;
    for (String v : values) {
      variants.add(new VariantData(id++, v, v, null, null, null, true, BigDecimal.TEN, null));
    }
    return new ProductData(
        1,
        "p",
        "Product",
        "Shoes",
        null,
        List.of(),
        List.of(),
        0,
        null,
        List.of(new OptionData(optionName, 1, List.of(values))),
        variants);
  }
}
