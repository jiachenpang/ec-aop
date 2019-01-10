package org.n3r.core.lang;

import static org.apache.commons.lang3.StringUtils.*;

public class RBoolean {
    public static boolean isFalseValue(String paramValue) {
        return equalsIgnoreCase(paramValue, "false")
                || equalsIgnoreCase(paramValue, "f")
                || equalsIgnoreCase(paramValue, "off")
                || "0".equals(paramValue)
                || equalsIgnoreCase(paramValue, "no")
                || equalsIgnoreCase(paramValue, "n");
    }

    public static boolean isTrueValue(String paramValue) {
        return equalsIgnoreCase(paramValue, "true")
                || equalsIgnoreCase(paramValue, "t")
                || equalsIgnoreCase(paramValue, "on")
                ||  "1".equals(paramValue)
                || equalsIgnoreCase(paramValue, "yes")
                || equalsIgnoreCase(paramValue, "y");
    }
}
