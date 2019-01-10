package org.n3r.shorturl;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.google.common.base.Preconditions;

/**
 * Very quick & dirty shortening strategy: Generate a random 4-char string, where the characters
 * are chosen from the set of upper- and lower-case letters and decimal digits. Reject the
 * generated key and try again if it string is already in the set of keys.
 * <p/>
 * There's a theoretical limit of 14'776'336 keys at which point we should increment the key
 * length. OTOH a Properties-based implementation is never going to scale to that many records
 * and we'll be forced to switch to a better implementation before then.
 * <p/>
 * Then, too, there is a small chance that separate servlet threads could generate the same key
 * and perform the duplicate-key check at the same time, since that part of the algorithm is not
 * locked.
 */
public class SimpleShorteningStrategy {
    public static final int DEFAULT_LENGTH = 4;
    public static final String SHORTURL_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private int desiredLength = DEFAULT_LENGTH;

    private Random rng = new Random();
    private final Map<String, String> urlMap = new HashMap<String, String>();

    public String shorten(String url) {
        while (true) {
            final StringBuilder s = new StringBuilder();
            for (int i = 0; i < desiredLength; i++) {
                s.append(SHORTURL_CHARS.charAt(rng.nextInt(SHORTURL_CHARS.length())));
            }
            String shortUrl = s.toString();
            if (!urlMap.containsKey(shortUrl)) {
                urlMap.put(shortUrl, url);
                return shortUrl;
            }
        }
    }

}
