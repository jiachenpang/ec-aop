package org.n3r.shorturl;


import org.junit.Assert;

public class Base62 {
    /**
     * All possible digits for representing a number as a String
     * This is conservative and does not include 'special'
     * characters since some browsers don't handle them right.
     * The IE for instance seems to be case insensitive in class
     * names for CSSs. Grrr.
     */
    private static final String baseDigits = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final static char[] DIGITS = baseDigits.toCharArray();
    public static final int MAX_RADIX = DIGITS.length;
    /**
     * Codes number up to radix 62.
     * Note, this method is only public for backward compatiblity. don't
     * use it.
     *
     * @param minDigits returns a string with a least minDigits digits
     */
    public static String toString(long i, int radix, int minDigits) {
        char[] buf = new char[65];
        radix = Math.min(Math.abs(radix), MAX_RADIX);
        minDigits = Math.min(buf.length - 1, Math.abs(minDigits));
        int charPos = buf.length - 1;
        boolean negative = (i < 0);
        if (negative) {
            i = -i;
        }
        while (i >= radix) {
            buf[charPos--] = DIGITS[(int) (i % radix)];
            i /= radix;
        }
        buf[charPos] = DIGITS[(int) i];
        // if minimum length of the result string is set, pad it with the
        // zero-representation (that is: '0')
        while (charPos > buf.length - minDigits)
            buf[--charPos] = DIGITS[0];
        if (negative) {
            buf[--charPos] = '-';
        }
        return new String(buf, charPos, buf.length - charPos);
    }

    public static void main1(String[] args) {
        System.out.println(toString(100000, 62, 0));
    }


    public static String toBase62(int decimalNumber) {
        return fromDecimalToOtherBase(62, decimalNumber);
    }

    public static int fromBase62(String base62Number) {
        return fromOtherBaseToDecimal(62, base62Number);
    }

    private static String fromDecimalToOtherBase(int base, int decimalNumber) {
        String result = decimalNumber == 0 ? "0" : "";
        while (decimalNumber != 0) {
            int mod = decimalNumber % base;
            result = baseDigits.substring(mod, mod + 1) + result;
            decimalNumber = decimalNumber / base;
        }
        return result;
    }

    private static int fromOtherBaseToDecimal(int base, String number) {
        int result = 0;
        for (int pos = number.length(), multiplier = 1; pos > 0; pos--) {
            result += baseDigits.indexOf(number.substring(pos - 1, pos)) * multiplier;
            multiplier *= base;
        }
        return result;
    }

    public static void main(String[] args) {
        System.out.println(toString(100000, 62, 0));
        System.out.println(toBase62(100000));
        System.out.println(fromBase62("Q0u"));

        long start = System.currentTimeMillis();
        for(int i = 0; i < 10000000; ++i)  {
            toString(i, 62, 0);
        }
        System.out.println((System.currentTimeMillis() - start) / 1000.0); // 1.922

        start = System.currentTimeMillis();
        for(int i = 0; i < 10000000; ++i)  {
            toBase62(i);
        }
        System.out.println((System.currentTimeMillis() - start) / 1000.0); // 5.95

        for(int i = 0; i < 10000000; ++i)  {
            Assert.assertEquals(toString(i, 62, 0), toBase62(i));
        }
    }
}
