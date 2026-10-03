package com.beacon.chat;

import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Questions public storefront data can't answer get an honest refusal instead of a guess:
 * conversion rate, traffic, sales or revenue, units in stock, customers, profit.
 */
@Component
public class ScopeGuard {

  public static final String REFUSAL =
      "Public data can't show that. It needs your store's sales data or Swym's intent data.";

  private static final Pattern OUT_OF_SCOPE =
      Pattern.compile(
          "\\bconversion\\b|\\bconvert(ing|s)?\\b.*\\brate\\b|\\btraffic\\b|\\bvisitors?\\b|\\bvisits\\b|\\bsessions?\\b"
              + "|\\bpage ?views\\b|\\brevenue\\b|\\bsales\\b|\\bturnover\\b|\\bhow (much|many) (did|do|have) (i|we) sell"
              + "|\\bunits? (left|in stock|remaining|on hand)\\b|\\bhow many (units|pairs|items|pieces) (are )?(left|in stock)"
              + "|\\binventory (level|count)s?\\b|\\bstock (level|count)s?\\b|\\bquantit(y|ies) (left|in stock|on hand)\\b"
              + "|\\bhow many (customers|shoppers|buyers|orders)\\b|\\b(new|repeat|returning) customers\\b"
              + "|\\bcustomer (count|numbers|data|lifetime value|retention)\\b|\\bprofits?\\b|\\bmargins?\\b|\\baov\\b|\\baverage order value\\b"
              + "|\\bcart abandonment\\b|\\bbounce rate\\b",
          Pattern.CASE_INSENSITIVE);

  /** The refusal text when the question is out of scope. */
  public Optional<String> check(String question) {
    return OUT_OF_SCOPE.matcher(question).find() ? Optional.of(REFUSAL) : Optional.empty();
  }
}
