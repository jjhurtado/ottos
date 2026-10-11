package org.jobits.ottos.rates;

import org.jobits.ottos.configuration.ServiceType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Calculates what a remittance costs and what gets delivered, with the configuration in force now for its kind
 * (delivery or pickup, each with its own rate and fee rule):
 * <pre>
 * amount           ≥ configured minimum amount (USD), otherwise AMOUNT_BELOW_MINIMUM
 * fee              = fee rule applied to the amount (e.g. 10 %, at least 10 USD)
 * total            = amount + fee                     (charged to the sender)
 * amount to deliver = amount × rate, rounded down to the corridor's delivery rounding (e.g. 50 CUP)
 * </pre>
 */
public interface QuoteService {

    /**
     * @param customRate the rate entered by hand for a pickup (units of the target currency per 1 USD); null uses the
     *                   configured one. Not allowed for deliveries (RATE_NOT_ALLOWED).
     */
    Quote quote(ServiceType remittanceType, String sourceCurrency, String targetCurrency, BigDecimal amount,
                BigDecimal customRate);

    /**
     * @param remittanceType  delivery or pickup: whose rate and fee rule were used
     * @param amount          amount sent, in the source currency
     * @param fee             fee charged, in the source currency
     * @param total           amount + fee, charged to the sender
     * @param rate            target units per source unit
     * @param rateOverridden  the rate was entered by hand (pickups) instead of the configured one
     * @param amountToDeliver amount handed over, in the target currency, after rounding
     */
    record Quote(ServiceType remittanceType, String corridor, String sourceCurrency, String targetCurrency,
                 BigDecimal amount, BigDecimal fee, BigDecimal total, BigDecimal rate, boolean rateOverridden,
                 BigDecimal amountToDeliver, UUID exchangeRateId, UUID feeRuleId) {
    }
}
