package org.jobits.ottos.remittances;

/**
 * What happened to a synced action. A rejection is usually thrown as an ApiException (nothing is saved); this
 * carries REJECTED only when the rejection itself changes the remittance (a duplicate delivery flags it).
 *
 * @param remittance       the remittance as the user sees it now; null if rejected
 * @param flaggedForReview the action left the remittance pending review
 */
public record SyncOutcome(Status status, String code, String message, RemittanceView remittance,
                          boolean flaggedForReview) {

    public enum Status {
        APPLIED,
        ALREADY_APPLIED,
        REJECTED
    }

    static SyncOutcome applied(RemittanceView remittance, boolean flagged) {
        return new SyncOutcome(Status.APPLIED, null, null, remittance, flagged);
    }

    static SyncOutcome alreadyApplied(RemittanceView remittance) {
        return new SyncOutcome(Status.ALREADY_APPLIED, null, null, remittance, remittance.review() != null);
    }

    static SyncOutcome rejected(String code, String message) {
        return new SyncOutcome(Status.REJECTED, code, message, null, false);
    }
}
