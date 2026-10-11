package org.jobits.ottos.remittances;

import java.util.List;

/** The statuses and transitions configured in the database, as the frontend reads them. */
public record WorkflowView(List<StatusView> statuses, List<TransitionView> transitions) {

    public record StatusView(String code, String name, int position, boolean initial, boolean requiresCourier,
                             boolean isFinal) {
    }

    public record TransitionView(String from, String to, String permission) {
    }
}
