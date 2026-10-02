package org.jobits.ottos.payments.domain;

public enum AccountType {
    /** The business cash box. */
    BUSINESS,
    /** Cash carried by a courier. */
    COURIER,
    /** The outside world: senders and beneficiaries. */
    EXTERNAL
}
