package org.jobits.ottos.rates.management;

import org.jobits.ottos.rates.domain.ExchangeRate;
import org.jobits.ottos.rates.domain.FeeRule;
import org.jobits.ottos.rates.domain.FeeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Sets exchange rates and fee rules. Every change is a new row that takes effect immediately. */
public interface RateAdministrationService {

    List<CorridorView> corridors();

    List<RateView> rateHistory(String corridorCode);

    RateView setRate(String corridorCode, BigDecimal rate, UUID actorId);

    List<FeeRuleView> feeRuleHistory(String corridorCode);

    FeeRuleView setFeeRule(String corridorCode, FeeType type, BigDecimal value, BigDecimal minFee,
                                  BigDecimal maxFee, UUID actorId);

    record CorridorView(String code, String sourceCurrency, String targetCurrency, BigDecimal deliveryRounding,
                 boolean active, RateView currentRate, FeeRuleView currentFeeRule) {
    }

    record RateView(UUID id, BigDecimal rate, Instant validFrom, UUID createdBy) {

        static RateView of(ExchangeRate r) {
            return new RateView(r.getId(), r.getRate(), r.getValidFrom(), r.getCreatedBy());
        }
    }

    record FeeRuleView(UUID id, FeeType type, BigDecimal value, BigDecimal minFee, BigDecimal maxFee,
                Instant validFrom, UUID createdBy) {

        static FeeRuleView of(FeeRule f) {
            return new FeeRuleView(f.getId(), f.getType(), f.getValue(), f.getMinFee(), f.getMaxFee(),
                    f.getValidFrom(), f.getCreatedBy());
        }
    }
}
