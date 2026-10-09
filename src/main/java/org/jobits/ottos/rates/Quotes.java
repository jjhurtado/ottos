package org.jobits.ottos.rates;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.rates.domain.Corridor;
import org.jobits.ottos.rates.domain.CorridorRepository;
import org.jobits.ottos.rates.domain.ExchangeRate;
import org.jobits.ottos.rates.domain.ExchangeRateRepository;
import org.jobits.ottos.rates.domain.FeeRule;
import org.jobits.ottos.rates.domain.FeeRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Calculates what a remittance costs and what gets delivered, with the rate and fee rule in force now:
 * <pre>
 * fee              = fee rule applied to the amount (e.g. 10 %, at least 10 USD)
 * total            = amount + fee                     (charged to the sender)
 * amount to deliver = amount × rate, rounded down to the corridor's delivery rounding (e.g. 50 CUP)
 * </pre>
 */
@Service
public class Quotes {

    private final CorridorRepository corridors;
    private final ExchangeRateRepository rates;
    private final FeeRuleRepository feeRules;
    private final Clock clock;

    Quotes(CorridorRepository corridors, ExchangeRateRepository rates, FeeRuleRepository feeRules, Clock clock) {
        this.corridors = corridors;
        this.rates = rates;
        this.feeRules = feeRules;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Quote quote(String sourceCurrency, String targetCurrency, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Amount must be positive with at most 2 decimals");
        }
        Corridor corridor = corridors.findBySourceCurrencyAndTargetCurrencyAndActiveTrue(sourceCurrency, targetCurrency)
                .orElseThrow(() -> ApiException.badRequest("NO_ACTIVE_CORRIDOR",
                        "No active corridor " + sourceCurrency + "-" + targetCurrency));
        Instant now = clock.instant();
        ExchangeRate rate = rates.findFirstByCorridorCodeAndValidFromLessThanEqualOrderByValidFromDesc(corridor.getCode(), now)
                .orElseThrow(() -> ApiException.conflict("NO_EXCHANGE_RATE",
                        "No exchange rate set for " + corridor.getCode()));
        FeeRule feeRule = feeRules.findFirstByCorridorCodeAndValidFromLessThanEqualOrderByValidFromDesc(corridor.getCode(), now)
                .orElseThrow(() -> ApiException.conflict("NO_FEE_RULE",
                        "No fee rule set for " + corridor.getCode()));

        BigDecimal sent = amount.setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal fee = feeRule.feeFor(sent);
        BigDecimal toDeliver = corridor.roundForDelivery(sent.multiply(rate.getRate()));
        if (toDeliver.signum() <= 0) {
            throw ApiException.badRequest("AMOUNT_TOO_SMALL",
                    "Amount too small: it rounds down to 0 " + corridor.getTargetCurrency() + " to deliver");
        }
        return new Quote(corridor.getCode(), corridor.getSourceCurrency(), corridor.getTargetCurrency(),
                sent, fee, sent.add(fee), rate.getRate(), toDeliver, rate.getId(), feeRule.getId());
    }

    /**
     * @param amount          amount sent, in the source currency
     * @param fee             fee charged, in the source currency
     * @param total           amount + fee, charged to the sender
     * @param rate            target units per source unit
     * @param amountToDeliver amount handed over, in the target currency, after rounding
     */
    public record Quote(String corridor, String sourceCurrency, String targetCurrency, BigDecimal amount,
                        BigDecimal fee, BigDecimal total, BigDecimal rate, BigDecimal amountToDeliver,
                        UUID exchangeRateId, UUID feeRuleId) {
    }
}
