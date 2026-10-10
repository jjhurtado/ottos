package org.jobits.ottos.remittances;

/** Why a delivery or pickup could not be done. The frontend translates the code; OTHER needs a note. */
public enum IncidentReason {
    /** Nobody was at the address. */
    NOT_HOME,
    /** The address does not exist or is wrong. */
    WRONG_ADDRESS,
    /** The beneficiary does not answer the phone. */
    UNREACHABLE,
    /** The beneficiary refused the delivery or the pickup. */
    REFUSED,
    /** Anything else, explained in the note. */
    OTHER
}
