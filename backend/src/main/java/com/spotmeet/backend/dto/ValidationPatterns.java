package com.spotmeet.backend.dto;

import java.util.regex.Pattern;

/** Validation patterns shared by the request DTOs and the app. */
public final class ValidationPatterns {

    /** Up to 255 characters, with a 64-character local part and domain labels of up to 63. */
    public static final String EMAIL = "^(?=.{1,255}$)(?=[^@]{1,64}@)[A-Za-z0-9]+([._%+-][A-Za-z0-9]+)*@([A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$";

    public static final String OTP = "^[0-9]{6}$";

    /** Outer whitespace, matching JavaScript String.trim(). */
    public static final String NAME_WHITESPACE = "^[\\s\\p{Z}\\uFEFF]+|[\\s\\p{Z}\\uFEFF]+$";

    public static final String ORGANIZATION_ACCESS_KEY = "^#[A-Za-z0-9_\\-]{2,50}$";

    public static final String COMMITTEE_NAME = "^[\\p{L}\\p{M}0-9 _-]{2,50}$";

    /** Whitespace escapes supported by Java and PostgreSQL regular expressions. */
    public static final String ORGANIZATION_WHITESPACE = "[\\t\\n\\x0B\\f\\r \\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+";
    private static final Pattern ORGANIZATION_SPACES = Pattern.compile(ORGANIZATION_WHITESPACE);
    private static final Pattern ORGANIZATION_EDGES = Pattern.compile("^" + ORGANIZATION_WHITESPACE + "|" + ORGANIZATION_WHITESPACE + "$");

    public static String normalizeOrganizationName(String value) {
        return value == null ? null : ORGANIZATION_SPACES.matcher(value).replaceAll(" ").trim();
    }

    public static String normalizeOrganizationValue(String value) {
        return value == null ? null : ORGANIZATION_EDGES.matcher(value).replaceAll("");
    }

    private ValidationPatterns() {}
}
