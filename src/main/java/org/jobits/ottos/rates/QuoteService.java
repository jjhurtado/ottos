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
 * A pickup delivers nothing: the courier collects the amount. Its rate is informative and its fee, entered by hand or
 * from the rule, is income of the business that never goes through a courier's cash.
 */
public interface QuoteService {

    /**
     * @param customRate the rate entered by hand for a pickup (units of the target currency per 1 USD); null uses the
     *                   configured one. Not allowed for deliveries (RATE_NOT_ALLOWED).
     * @param customFee  the fee entered by hand for a pickup (≥ 0, in the source currency); null uses the configured
     *                   fee rule. Not allowed for deliveries (FEE_NOT_ALLOWED).
     */
    default Quote quote(ServiceType remittanceType, String sourceCurrency, String targetCurrency, BigDecimal amount,
                        BigDecimal customRate, BigDecimal customFee) {
        return quote(remittanceType, sourceCurrency, targetCurrency, amount, customRate, customFee, false);
    }

    /**
     * Like {@link #quote(ServiceType, String, String, BigDecimal, BigDecimal, BigDecimal)}; onTheHouse: a delivery the
     * business sends free of fee (docs/contracts/on-the-house.md). Rate, minimum amount and rounding apply as always;
     * the fee is 0, the total is the amount and waivedFee is the fee the rule would have charged. Only deliveries
     * (ON_THE_HOUSE_DELIVERY_ONLY), so never with a fee entered by hand (FEE_NOT_ALLOWED).
     */
    Quote quote(ServiceType remittanceType, String sourceCurrency, String targetCurrency, BigDecimal amount,
                BigDecimal customRate, BigDecimal customFee, boolean onTheHouse);

    /**
     * @param remittanceType  delivery or pickup: whose rate and fee rule were used
     * @param amount          amount sent, in the source currency
     * @param fee             fee charged, in the source currency
     * @param total           amount + fee, charged to the sender
     * @param rate            target units per source unit
     * @param rateOverridden  the rate was entered by hand (pickups) instead of the configured one
     * @param feeOverridden   the fee was entered by hand (pickups) instead of the configured rule
     * @param amountToDeliver amount handed over, in the target currency, after rounding
     * @param onTheHouse      free of fee: fee 0 and total = amount
     * @param waivedFee       on the house: the fee the rule would have charged (with its minimum and maximum); null
     *                        otherwise
     */
    record Quote(ServiceType remittanceType, String corridor, String sourceCurrency, String targetCurrency,
                 BigDecimal amount, BigDecimal fee, boolean feeOverridden, BigDecimal total, BigDecimal rate,
                 boolean rateOverridden, BigDecimal amountToDeliver, UUID exchangeRateId, UUID feeRuleId,
                 boolean onTheHouse, BigDecimal waivedFee) {
    }
}
