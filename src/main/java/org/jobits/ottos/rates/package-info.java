/**
 * Rates and fees: corridors (currency pairs such as USD-CUP), exchange-rate history, fee rules
 * (percentage with min/max, or fixed) and the quote calculator.
 * <p>
 * Public API: {@link org.jobits.ottos.rates.QuoteService}. Everything in sub-packages is internal.
 * Phase 1.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Rates and fees")
package org.jobits.ottos.rates;
