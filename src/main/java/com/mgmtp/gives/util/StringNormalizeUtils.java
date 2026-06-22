package com.mgmtp.gives.util;

import java.text.Normalizer;

public final class StringNormalizeUtils {

    private StringNormalizeUtils() {
        // Prevent instantiation
    }

    /**
     * Normalises a text string (intended for names/titles) using:
     * 1. Unicode NFC normalisation (composes accented characters into canonical form).
     * 2. Unicode-aware strip (removes leading/trailing Unicode whitespaces).
     * 3. Collapsing multiple internal whitespaces into a single space.
     *
     * @param name the raw string to normalise
     * @return the normalised string, or null if the input is null
     */
    public static String normalizeName(String name) {
        if (name == null) {
            return null;
        }

        // Apply Unicode NFC normalisation
        String normalised = Normalizer.normalize(name, Normalizer.Form.NFC);

        // Strip leading/trailing Unicode whitespace (including non-breaking spaces like \u2007)
        String stripped = normalised.replaceAll("^[\\p{Z}\\s]+|[\\p{Z}\\s]+$", "");

        // Collapse multiple internal whitespaces/Unicode spaces into a single space
        return stripped.replaceAll("[\\p{Z}\\s]+", " ");
    }

    /**
     * Normalises a description string by stripping Unicode spaces and converting
     * blank/empty strings to null.
     *
     * @param description the raw description to normalise
     * @return the normalised description, or null if null/blank
     */
    public static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String stripped = description.replaceAll("^[\\p{Z}\\s]+|[\\p{Z}\\s]+$", "");
        return stripped.isEmpty() ? null : stripped;
    }
}
