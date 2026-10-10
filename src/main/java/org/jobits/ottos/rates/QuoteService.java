package org.jobits.ottos.rates;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Calculates what a remittance costs and what gets delivered, with the rate and fee rule in force now:
 * <pre>
 * fee              = fee rule applied to the amount (e.g. 10 %, at least 10 USD)
 * total            = amount + fee                     (charged to the sender)
 * amount to deliver = amount × rate, rounded down to the corridor's delivery rounding (e.g. 50 CUP)
 * </pre>
 */
public interface QuoteService {

    Quote quote(String sourceCurrency, String targetCurrency, BigDecimal amount);

    /**
     * @param amount          amount sent, in the source currency
     * @param fee             fee charged, in the source currency
     * @param total           amount + fee, charged to the sender
     * @param rate            target units per source unit
     * @param amountToDeliver amount handed over, in the target currency, after rounding
     */
    record Quote(String corridor, String sourceCurrency, String targetCurrency, BigDecimal amount,
          BigDecimal fee, BigDecimal total, BigDecimal rate, BigDecimal amountToDeliver,
          UUID exchangeRateId, UUID feeRuleId) {
    }
}
