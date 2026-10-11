package org.jobits.ottos.remittances;

/** What a queued action from the mobile app does; each one equals an online endpoint. */
public enum SyncActionType {
    /** POST /remittances/{id}/deliver */
    DELIVER,
    /** POST /remittances/{id}/deliver-with-pin */
    DELIVER_WITH_PIN,
    /** POST /remittances/{id}/postpone */
    POSTPONE,
    /** POST /remittances/{id}/incidents */
    REPORT_INCIDENT
}
