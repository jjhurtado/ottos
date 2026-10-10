package org.jobits.ottos.rates.management;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.rates.domain.Corridor;
import org.jobits.ottos.rates.domain.CorridorRepository;
import org.jobits.ottos.rates.domain.ExchangeRate;
import org.jobits.ottos.rates.domain.ExchangeRateRepository;
import org.jobits.ottos.rates.domain.FeeRule;
import org.jobits.ottos.rates.domain.FeeRuleRepository;
import org.jobits.ottos.rates.domain.FeeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Implementation of {@link RateAdministrationService}. */
@Service
class RateAdministrationServiceImpl implements RateAdministrationService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CorridorRepository corridors;
    private final ExchangeRateRepository rates;
    private final FeeRuleRepository feeRules;
    private final Clock clock;

    RateAdministrationServiceImpl(CorridorRepository corridors, ExchangeRateRepository rates, FeeRuleRepository feeRules,
                       Clock clock) {
        this.corridors = corridors;
        this.rates = rates;
        this.feeRules = feeRules;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CorridorView> corridors() {
        Instant now = clock.instant();
        return corridors.findAllByOrderByCode().stream().map(c -> new CorridorView(
                c.getCode(), c.getSourceCurrency(), c.getTargetCurrency(), c.getDeliveryRounding(), c.isActive(),
                rates.findFirstByCorridorCodeAndValidFromLessThanEqualOrderByValidFromDesc(c.getCode(), now)
                        .map(RateView::of).orElse(null),
                feeRules.findFirstByCorridorCodeAndValidFromLessThanEqualOrderByValidFromDesc(c.getCode(), now)
                        .map(FeeRuleView::of).orElse(null)
        )).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RateView> rateHistory(String corridorCode) {
        find(corridorCode);
        return rates.findByCorridorCodeOrderByValidFromDesc(corridorCode).stream().map(RateView::of).toList();
    }

    @Override
    @Transactional
    public RateView setRate(String corridorCode, BigDecimal rate, UUID actorId) {
        Corridor corridor = find(corridorCode);
        if (corridor.isSameCurrency() && rate.compareTo(BigDecimal.ONE) != 0) {
            throw ApiException.badRequest("FIXED_RATE", "The rate of " + corridorCode + " is always 1");
        }
        return RateView.of(rates.save(new ExchangeRate(corridorCode, rate, clock.instant(), actorId)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeeRuleView> feeRuleHistory(String corridorCode) {
        find(corridorCode);
        return feeRules.findByCorridorCodeOrderByValidFromDesc(corridorCode).stream().map(FeeRuleView::of).toList();
    }

    @Override
    @Transactional
    public FeeRuleView setFeeRule(String corridorCode, FeeType type, BigDecimal value, BigDecimal minFee,
                                  BigDecimal maxFee, UUID actorId) {
        find(corridorCode);
        if (type == FeeType.PERCENTAGE && value.compareTo(HUNDRED) > 0) {
            throw ApiException.badRequest("FEE_PERCENTAGE_TOO_HIGH", "A percentage fee cannot exceed 100");
        }
        if (minFee != null && maxFee != null && minFee.compareTo(maxFee) > 0) {
            throw ApiException.badRequest("INVALID_FEE_RANGE", "minFee cannot be greater than maxFee");
        }
        FeeRule rule = new FeeRule(corridorCode, type, value, minFee, maxFee, clock.instant(), actorId);
        return FeeRuleView.of(feeRules.save(rule));
    }

    private Corridor find(String code) {
        return corridors.findById(code)
                .orElseThrow(() -> ApiException.notFound("CORRIDOR_NOT_FOUND", "Corridor not found"));
    }
}
