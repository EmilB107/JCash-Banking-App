package com.jcash.app.users;

import java.util.regex.Pattern;

public final class MobileNumberNormalizer {

    private static final Pattern INTERNATIONAL_FORMAT = Pattern.compile("\\+639\\d{9}");

    private MobileNumberNormalizer() {
    }

    public static String normalize(String mobileNumber) {
        if (mobileNumber == null) {
            return null;
        }

        String trimmed = mobileNumber.trim();
        if (INTERNATIONAL_FORMAT.matcher(trimmed).matches()) {
            return "0" + trimmed.substring(3);
        }

        return trimmed;
    }
}
