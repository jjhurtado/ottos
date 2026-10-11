package org.jobits.ottos;

/** Optional texts are stored trimmed, and a blank one as no value. */
public final class Texts {

    private Texts() {
    }

    /** null for null or blank; otherwise the text without outer spaces. */
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
