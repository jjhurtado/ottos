package org.jobits.ottos.remittances;

/** Why a remittance synced from the mobile app needs Sales to check it. The frontend translates the code. */
public enum ReviewReason {
    /** Delivered by a courier it was assigned to at that time, after it had been reassigned to someone else. */
    DELIVERED_AFTER_REASSIGNMENT,
    /** Delivered offline with a PIN that does not match. */
    PIN_MISMATCH,
    /** Someone else reported delivering it after it was already completed. */
    DUPLICATE_DELIVERY,
    /** Delivered more than 7 days before it was synced. */
    LATE_SYNC
}
