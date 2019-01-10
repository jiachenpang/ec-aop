package org.n3r.core.utils;


import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;


/**
 * Test cases for {@link TimeSpanParser}.
 */
public class TestTimeSpanParser extends TestCase {

    /** {@inheritDoc} */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /** {@inheritDoc} */
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }


    public void testParseStringTimeUnitSeconds() {
        assertEquals(0, TimeSpanParser.parse("0s", TimeUnit.SECONDS));
        assertEquals(1, TimeSpanParser.parse("1s", TimeUnit.SECONDS));
        assertEquals(0, TimeSpanParser.parse("1s", TimeUnit.MINUTES));
        assertEquals(2, TimeSpanParser.parse("120s", TimeUnit.MINUTES));
        assertEquals(24, TimeSpanParser.parse("86400s", TimeUnit.HOURS));
        assertEquals(1, TimeSpanParser.parse("86400s", TimeUnit.DAYS));
        assertEquals(86400, TimeSpanParser.parse("86400s", TimeUnit.SECONDS));
        assertEquals(0, TimeSpanParser.parse("0", TimeUnit.SECONDS));
        assertEquals(8467, TimeSpanParser.parse("8467", TimeUnit.SECONDS));
    }


    public void testParseStringTimeUnitMinutes() {
        assertEquals(0, TimeSpanParser.parse("0m", TimeUnit.MINUTES));
        assertEquals(60, TimeSpanParser.parse("1m", TimeUnit.SECONDS));
        assertEquals(1, TimeSpanParser.parse("1m", TimeUnit.MINUTES));
        assertEquals(120, TimeSpanParser.parse("120m", TimeUnit.MINUTES));
        assertEquals(24, TimeSpanParser.parse("1440m", TimeUnit.HOURS));
        assertEquals(1, TimeSpanParser.parse("1440m", TimeUnit.DAYS));
        assertEquals(600, TimeSpanParser.parse("10m", TimeUnit.SECONDS));
        assertEquals(0, TimeSpanParser.parse("0m", TimeUnit.SECONDS));
    }


    public void testParseStringTimeUnitHours() {
        assertEquals(0, TimeSpanParser.parse("0h", TimeUnit.HOURS));
        assertEquals(3600, TimeSpanParser.parse("1h", TimeUnit.SECONDS));
        assertEquals(120, TimeSpanParser.parse("2h", TimeUnit.MINUTES));
        assertEquals(1440, TimeSpanParser.parse("1440h", TimeUnit.HOURS));
        assertEquals(2, TimeSpanParser.parse("48h", TimeUnit.DAYS));
        assertEquals(3600, TimeSpanParser.parse("1h", TimeUnit.SECONDS));
        assertEquals(0, TimeSpanParser.parse("0h", TimeUnit.SECONDS));
    }


    public void testParseStringTimeUnitDays() {
        assertEquals(0, TimeSpanParser.parse("0d", TimeUnit.DAYS));
        assertEquals(1, TimeSpanParser.parse("1d", TimeUnit.DAYS));
        assertEquals(2880, TimeSpanParser.parse("2d", TimeUnit.MINUTES));
        assertEquals(744, TimeSpanParser.parse("31d", TimeUnit.HOURS));
        assertEquals(48, TimeSpanParser.parse("48d", TimeUnit.DAYS));
        assertEquals(86400, TimeSpanParser.parse("1d", TimeUnit.SECONDS));
        assertEquals(0, TimeSpanParser.parse("0d", TimeUnit.SECONDS));
    }


    public void testParseStringTimeMixed() {
        assertEquals(2, TimeSpanParser.parse("1d24h5s", TimeUnit.DAYS));
        assertEquals(172805, TimeSpanParser.parse("1d24h5s", TimeUnit.SECONDS));
        assertEquals(0, TimeSpanParser.parse("59m59s", TimeUnit.HOURS));
        assertEquals(59, TimeSpanParser.parse("59m59s", TimeUnit.MINUTES));
        assertEquals(1440, TimeSpanParser.parse("1d5s", TimeUnit.MINUTES));
        assertEquals(86405, TimeSpanParser.parse("1d5s", TimeUnit.SECONDS));
    }


    public void testParseStringEmpty() {
        assertEquals(0, TimeSpanParser.parse("", TimeUnit.DAYS));
        assertEquals(0, TimeSpanParser.parse("", TimeUnit.SECONDS));
        assertEquals(0, TimeSpanParser.parse("", TimeUnit.HOURS));
        assertEquals(0, TimeSpanParser.parse(null, TimeUnit.MINUTES));
        assertEquals(0, TimeSpanParser.parse(null, TimeUnit.MINUTES));
        assertEquals(0, TimeSpanParser.parse(null, TimeUnit.SECONDS));
    }


    public void testParseStringTimeInvalidArguments() {
        final Collection<String> illegalTimeSpans =
                Arrays.asList("d5m", "5m1d", "1x5m", "(1d)", "0.5d", "0,5d", "5d1d", "s", "-3d");
        for (final String timeSpan : illegalTimeSpans) {
            try {
                TimeSpanParser.parse(timeSpan, TimeUnit.SECONDS);
                fail("IllegalArgumentException excpected for illegal time span " + timeSpan);
            } catch (final IllegalArgumentException e) {
                ;// ok
            }
        }
    }


    public void testParseStringTimeUnitWithDefault() {
        assertEquals(5, TimeSpanParser.parse("", TimeUnit.DAYS, 5));
        assertEquals(172805, TimeSpanParser.parse("", TimeUnit.SECONDS, 172805));
        assertEquals(0, TimeSpanParser.parse("", TimeUnit.HOURS, 0));
        assertEquals(5, TimeSpanParser.parse(null, TimeUnit.SECONDS, 5));
        assertEquals(172805, TimeSpanParser.parse(null, TimeUnit.MINUTES, 172805));
        assertEquals(0, TimeSpanParser.parse(null, TimeUnit.MINUTES, 0));
    }

    public void testParseStringTimeInvalidArgumentsWithDefaults() {
        final Collection<String> illegalTimeSpans =
                Arrays.asList("d5m", "5m1d", "1x5m", "(1d)", "0.5d", "0,5d", "5d1d", "s", "-3d");
        for (final String timeSpan : illegalTimeSpans) {
            try {
                TimeSpanParser.parse(timeSpan, TimeUnit.SECONDS, 5);
                fail("IllegalArgumentException excpected for illegal time span " + timeSpan);
            } catch (final IllegalArgumentException e) {
                ;// ok
            }
        }
    }

}