package org.jobits.ottos.remittances;

import org.jobits.ottos.remittances.domain.RemittanceRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Tracking codes (OT-XXXXXX, without look-alike characters) and PINs of new remittances. */
@Component
class RemittanceCodes {

    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RemittanceRepository remittances;

    RemittanceCodes(RemittanceRepository remittances) {
        this.remittances = remittances;
    }

    String newCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder("OT-");
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while (remittances.existsByCode(code));
        return code;
    }

    String newPin() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
