package com.beacon.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PathEncoderTest {

  @Test
  void quote_matchesPythonQuote() {
    assertThat(PathEncoder.quote("women's shoes")).isEqualTo("women%27s%20shoes");
    assertThat(PathEncoder.quote("t-shirts")).isEqualTo("t-shirts");
    assertThat(PathEncoder.quote("a/b&c")).isEqualTo("a/b%26c");
    assertThat(PathEncoder.quote("é")).isEqualTo("%C3%A9");
  }
}
