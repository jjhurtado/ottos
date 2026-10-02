package org.jobits.ottos.rates.domain;

public enum FeeType {
    /** The fee is a percent of the amount sent. */
    PERCENTAGE,
    /** The fee is a fixed amount in the source currency. */
    FIXED
}
