package org.jobits.ottos.configuration;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.configuration.domain.Corridor;
import org.jobits.ottos.configuration.domain.CorridorRepository;
import org.jobits.ottos.configuration.domain.ExchangeRate;
import org.jobits.ottos.configuration.domain.ExchangeRateRepository;
import org.jobits.ottos.configuration.domain.FeeRule;
import org.jobits.ottos.configuration.domain.FeeRuleRepository;
import org.jobits.ottos.configuration.domain.Setting;
import org.jobits.ottos.configuration.domain.SettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Implementation of {@link ConfigurationService}. */
@Service
class ConfigurationServiceImpl implements ConfigurationService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final SettingRepository settings;
    private final CorridorRepository corridors;
    private final ExchangeRateRepository rates;
    private final FeeRuleRepository feeRules;
    private final Clock clock;

    ConfigurationServiceImpl(SettingRepository settings, CorridorRepository corridors, ExchangeRateRepository rates,
                             FeeRuleRepository feeRules, Clock clock) {
        this.settings = settings;
        this.corridors = corridors;
        this.rates = rates;
        this.feeRules = feeRules;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- settings

    @Override
    @Transactional(readOnly = true)
    public ConfigurationView get() {
        return new ConfigurationView(settings(), corridors());
    }

    @Override
    @Transactional(readOnly = true)
    public SettingsView settings() {
        return new SettingsView(minimumAmount(), MINIMUM_AMOUNT_CURRENCY, defaultDeliveryDays());
    }

    @Override
    @Transactional
    public SettingsView updateSettings(BigDecimal minimumAmount, Integer defaultDeliveryDays, UUID actorId) {
        Instant now = clock.instant();
        if (minimumAmount != null) {
            setting(Setting.MINIMUM_AMOUNT).change(minimumAmount.setScale(2).toPlainString(), now, actorId);
        }
        if (defaultDeliveryDays != null) {
            setting(Setting.DEFAULT_DELIVERY_DAYS).change(defaultDeliveryDays.toString(), now, actorId);
        }
        return settings();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal minimumAmount() {
        return new BigDecimal(setting(Setting.MINIMUM_AMOUNT).getValue().trim()).setScale(2);
    }

    @Override
    @Transactional(readOnly = true)
    public int defaultDeliveryDays() {
        return Integer.parseInt(setting(Setting.DEFAULT_DELIVERY_DAYS).getValue().trim());
    }

    // ---------------------------------------------------------------- corridors, rates and fee rules

    @Override
    @Transactional(readOnly = true)
    public List<CorridorView> corridors() {
        Instant now = clock.instant();
        return corridors.findAllByOrderByCode().stream().map(c -> view(c, now)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CorridorView> activeCorridor(String sourceCurrency, String targetCurrency) {
        return corridors.findBySourceCurrencyAndTargetCurrencyAndActiveTrue(sourceCurrency, targetCurrency)
                .map(c -> view(c, clock.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RateView> rateHistory(String corridorCode) {
        find(corridorCode);
        return rates.findByCorridorCodeOrderByValidFromDesc(corridorCode).stream()
                .map(ConfigurationServiceImpl::view).toList();
    }

    @Override
    @Transactional
    public RateView setRate(String corridorCode, BigDecimal rate, UUID actorId) {
        Corridor corridor = find(corridorCode);
        if (corridor.isSameCurrency() && rate.compareTo(BigDecimal.ONE) != 0) {
            throw ApiException.badRequest("FIXED_RATE", "The rate of " + corridorCode + " is always 1");
        }
        return view(rates.save(new ExchangeRate(corridorCode, rate, clock.instant(), actorId)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeeRuleView> feeRuleHistory(String corridorCode) {
        find(corridorCode);
        return feeRules.findByCorridorCodeOrderByValidFromDesc(corridorCode).stream()
                .map(ConfigurationServiceImpl::view).toList();
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
        return view(feeRules.save(rule));
    }

    // ---------------------------------------------------------------- helpers

    private Setting setting(String key) {
        return settings.findById(key)
                .orElseThrow(() -> new IllegalStateException("Setting " + key + " missing; check the Flyway migrations"));
    }

    private Corridor find(String code) {
        return corridors.findById(code)
                .orElseThrow(() -> ApiException.notFound("CORRIDOR_NOT_FOUND", "Corridor not found"));
    }

    private CorridorView view(Corridor c, Instant now) {
        return new CorridorView(c.getCode(), c.getSourceCurrency(), c.getTargetCurrency(), c.getDeliveryRounding(),
                c.isActive(),
                rates.findFirstByCorridorCodeAndValidFromLessThanEqualOrderByValidFromDesc(c.getCode(), now)
                        .map(ConfigurationServiceImpl::view).orElse(null),
                feeRules.findFirstByCorridorCodeAndValidFromLessThanEqualOrderByValidFromDesc(c.getCode(), now)
                        .map(ConfigurationServiceImpl::view).orElse(null));
    }

    private static RateView view(ExchangeRate r) {
        return new RateView(r.getId(), r.getRate(), r.getValidFrom(), r.getCreatedBy());
    }

    private static FeeRuleView view(FeeRule f) {
        return new FeeRuleView(f.getId(), f.getType(), f.getValue(), f.getMinFee(), f.getMaxFee(), f.getValidFrom(),
                f.getCreatedBy());
    }
}
