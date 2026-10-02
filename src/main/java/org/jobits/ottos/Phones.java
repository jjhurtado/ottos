package org.jobits.ottos;

/** Phone numbers are stored and searched normalized: digits only, keeping a leading + if present. */
public final class Phones {

    private Phones() {
    }

    public static String normalize(String phone) {
        if (phone == null) {
            return null;
        }
        String trimmed = phone.trim();
        String digits = trimmed.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        return trimmed.startsWith("+") ? "+" + digits : digits;
    }
}
