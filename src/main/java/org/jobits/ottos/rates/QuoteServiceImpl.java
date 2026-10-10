package org.jobits.ottos.rates;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.configuration.ConfigurationService;
import org.jobits.ottos.configuration.ConfigurationService.CorridorView;
import org.jobits.ottos.configuration.ConfigurationService.FeeRuleView;
import org.jobits.ottos.configuration.ConfigurationService.RateView;
import org.jobits.ottos.configuration.ServiceType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Implementation of {@link QuoteService}. */
@Service
class QuoteServiceImpl implements QuoteService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ConfigurationService configuration;

    QuoteServiceImpl(ConfigurationService configuration) {
        this.configuration = configuration;
    }

    @Override
    @Transactional(readOnly = true)
    public Quote quote(ServiceType remittanceType, String sourceCurrency, String targetCurrency, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Amount must be positive with at most 2 decimals");
        }
        CorridorView corridor = configuration.activeCorridor(sourceCurrency, targetCurrency)
                .orElseThrow(() -> ApiException.badRequest("NO_ACTIVE_CORRIDOR",
                        "No active corridor " + sourceCurrency + "-" + targetCurrency));
        BigDecimal sent = amount.setScale(2, RoundingMode.UNNECESSARY);
        requireMinimum(sent, corridor.sourceCurrency());
        RateView rate = corridor.rate(remittanceType);
        if (rate == null) {
            throw ApiException.conflict("NO_EXCHANGE_RATE",
                    "No " + remittanceType + " exchange rate set for " + corridor.code());
        }
        FeeRuleView feeRule = corridor.feeRule(remittanceType);
        if (feeRule == null) {
            throw ApiException.conflict("NO_FEE_RULE", "No " + remittanceType + " fee rule set for " + corridor.code());
        }

        BigDecimal fee = feeFor(feeRule, sent);
        BigDecimal toDeliver = roundForDelivery(sent.multiply(rate.rate()), corridor.deliveryRounding());
        if (toDeliver.signum() <= 0) {
            throw ApiException.badRequest("AMOUNT_TOO_SMALL",
                    "Amount too small: it rounds down to 0 " + corridor.targetCurrency() + " to deliver");
        }
        return new Quote(remittanceType, corridor.code(), corridor.sourceCurrency(), corridor.targetCurrency(),
                sent, fee, sent.add(fee), rate.rate(), toDeliver, rate.id(), feeRule.id());
    }

    /** The configured minimum is in USD and applies to every corridor sending USD; zero means no minimum. */
    private void requireMinimum(BigDecimal amount, String currency) {
        if (!ConfigurationService.MINIMUM_AMOUNT_CURRENCY.equals(currency)) {
            return;
        }
        BigDecimal minimum = configuration.minimumAmount();
        if (amount.compareTo(minimum) < 0) {
            throw ApiException.badRequest("AMOUNT_BELOW_MINIMUM",
                            "The minimum amount is " + minimum.toPlainString() + " " + currency)
                    .with("minimumAmount", minimum)
                    .with("currency", currency);
        }
    }

    /** Fee for an amount in the source currency, rounded to cents and kept within [minFee, maxFee]. */
    private static BigDecimal feeFor(FeeRuleView rule, BigDecimal amount) {
        BigDecimal fee = switch (rule.type()) {
            case PERCENTAGE -> amount.multiply(rule.value()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            case FIXED -> rule.value().setScale(2, RoundingMode.HALF_UP);
        };
        if (rule.minFee() != null && fee.compareTo(rule.minFee()) < 0) {
            fee = rule.minFee().setScale(2, RoundingMode.HALF_UP);
        }
        if (rule.maxFee() != null && fee.compareTo(rule.maxFee()) > 0) {
            fee = rule.maxFee().setScale(2, RoundingMode.HALF_UP);
        }
        return fee;
    }

    /** Rounds an amount in the target currency down to a multiple of the delivery rounding (e.g. 50 CUP). */
    private static BigDecimal roundForDelivery(BigDecimal amount, BigDecimal deliveryRounding) {
        return amount.divide(deliveryRounding, 0, RoundingMode.FLOOR)
                .multiply(deliveryRounding)
                .setScale(2, RoundingMode.UNNECESSARY);
    }
}
