package org.n3r.core.utils;

import java.util.concurrent.TimeUnit;

public class RThread {
    public static void sleepMilis(long milis) {
        try {
            Thread.sleep(milis);
        } catch (InterruptedException e) {
        }
    }

    public static void sleep(long time, TimeUnit timeUnit) {
        sleepMilis(timeUnit.toMillis(time));
    }
}
