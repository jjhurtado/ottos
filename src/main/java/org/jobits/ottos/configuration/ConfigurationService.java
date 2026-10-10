package org.jobits.ottos.configuration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Configurable values of the business, managed from one place: the settings (minimum amount, delivery days) and, for
 * each corridor, its exchange rate and fee rule. Rates and fee rules are never edited: each change is a new row that
 * applies from now on. Other modules read the values here; the rates module calculates quotes with them.
 */
public interface ConfigurationService {

    /** Remittances are always sent in USD, so the minimum amount is in USD. */
    String MINIMUM_AMOUNT_CURRENCY = "USD";

    /** Everything at once, for the configuration screen. */
    ConfigurationView get();

    SettingsView settings();

    /** Changes the given settings; a null value is left as it is. */
    SettingsView updateSettings(BigDecimal minimumAmount, Integer defaultDeliveryDays, UUID actorId);

    /** Smallest amount a remittance can send, in {@link #MINIMUM_AMOUNT_CURRENCY}; zero means no minimum. */
    BigDecimal minimumAmount();

    /** Days from registration to the expected delivery date of a remittance. */
    int defaultDeliveryDays();

    /** Corridors with the exchange rate and fee rule in force now. */
    List<CorridorView> corridors();

    /** The active corridor between two currencies, with the rate and fee rule in force now (either may be null). */
    Optional<CorridorView> activeCorridor(String sourceCurrency, String targetCurrency);

    /** Exchange-rate history of a corridor, newest first. */
    List<RateView> rateHistory(String corridorCode);

    /** Sets a new exchange rate for a corridor; it applies from now on. */
    RateView setRate(String corridorCode, BigDecimal rate, UUID actorId);

    /** Fee-rule history of a corridor, newest first. */
    List<FeeRuleView> feeRuleHistory(String corridorCode);

    /** Sets a new fee rule for a corridor; it applies from now on. */
    FeeRuleView setFeeRule(String corridorCode, FeeType type, BigDecimal value, BigDecimal minFee, BigDecimal maxFee,
                           UUID actorId);

    record ConfigurationView(SettingsView settings, List<CorridorView> corridors) {
    }

    record SettingsView(BigDecimal minimumAmount, String minimumAmountCurrency, int defaultDeliveryDays) {
    }

    /**
     * @param deliveryRounding the amount to deliver is rounded down to a multiple of this (50 CUP; 0.01 = no rounding)
     */
    record CorridorView(String code, String sourceCurrency, String targetCurrency, BigDecimal deliveryRounding,
                        boolean active, RateView currentRate, FeeRuleView currentFeeRule) {
    }

    /** @param rate units of the target currency per unit of the source currency */
    record RateView(UUID id, BigDecimal rate, Instant validFrom, UUID createdBy) {
    }

    /**
     * @param value  a percent of the amount (PERCENTAGE) or the fee itself (FIXED), in the source currency
     * @param minFee the fee is never below this, if set
     * @param maxFee the fee is never above this, if set
     */
    record FeeRuleView(UUID id, FeeType type, BigDecimal value, BigDecimal minFee, BigDecimal maxFee,
                       Instant validFrom, UUID createdBy) {
    }
}
