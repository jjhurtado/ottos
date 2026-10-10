package org.jobits.ottos.configuration;

/**
 * The kind of remittance an exchange rate or fee rule applies to; each corridor has its own rate and fee for each.
 * Mirrors the remittances module's RemittanceType, which configuration cannot depend on.
 */
public enum ServiceType {
    /** The courier hands cash to the beneficiary. */
    DELIVERY,
    /** The courier collects cash from the beneficiary. */
    PICKUP
}
