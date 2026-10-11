package org.jobits.ottos.sync;

/** What an action of the mobile app's offline queue does; each one equals an online endpoint. */
public enum SyncActionKind {
    /** POST /remittances/{id}/deliver */
    DELIVER,
    /** POST /remittances/{id}/deliver-with-pin */
    DELIVER_WITH_PIN,
    /** POST /remittances/{id}/postpone */
    POSTPONE,
    /** POST /remittances/{id}/incidents */
    REPORT_INCIDENT,
    /** POST /cash/me/exchanges; it has no remittance */
    CASH_EXCHANGE
}
