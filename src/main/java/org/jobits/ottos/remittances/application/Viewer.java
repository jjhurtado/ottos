package org.jobits.ottos.remittances.application;

import org.jobits.ottos.remittances.RemittanceType;
import org.jobits.ottos.remittances.domain.Remittance;

import java.util.Set;
import java.util.UUID;

/**
 * The staff member reading or acting on remittances, with their permissions.
 * <ul>
 *   <li>remittances:read sees every remittance; remittances:read-assigned only the ones assigned to them.</li>
 *   <li>Fee, rate and totals of pickups need remittances:read-financials; those of deliveries need remittances:read.</li>
 *   <li>The PIN is shown only with remittances:read (it goes on the sender's receipt, never to the courier).</li>
 * </ul>
 */
public record Viewer(UUID userId, Set<String> permissions) {

    public static final String READ = "remittances:read";
    public static final String READ_ASSIGNED = "remittances:read-assigned";
    public static final String READ_FINANCIALS = "remittances:read-financials";

    public Viewer {
        permissions = Set.copyOf(permissions);
    }

    public boolean has(String permission) {
        return permissions.contains(permission);
    }

    boolean canSee(Remittance r) {
        return has(READ) || (has(READ_ASSIGNED) && userId.equals(r.getCourierId()));
    }

    boolean seesFinancials(Remittance r) {
        return has(READ_FINANCIALS) || (r.getType() == RemittanceType.DELIVERY && has(READ));
    }

    boolean seesPin() {
        return has(READ);
    }
}
