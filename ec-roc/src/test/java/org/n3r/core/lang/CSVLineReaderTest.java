package org.n3r.core.lang;

import org.junit.Assert;
import org.junit.Test;

public class CSVLineReaderTest {
    @Test
    public void test() {
        String[] line = new CSVLineReader().parseLine("a, b, c");
        Assert.assertEquals(3, line.length);
        Assert.assertEquals("a", line[0]);
        Assert.assertEquals("b", line[1]);
        Assert.assertEquals("c", line[2]);

        line = new CSVLineReader().parseLine("\"a,b\",b,c");
        Assert.assertEquals(3, line.length);
        Assert.assertEquals("a,b", line[0]);
        Assert.assertEquals("b", line[1]);
        Assert.assertEquals("c", line[2]);

        line = new CSVLineReader().parseLine(" \"{\"\"name\"\":\"\"mockedName\"\"}\"");
        Assert.assertEquals(1, line.length);
        Assert.assertEquals("{\"name\":\"mockedName\"}", line[0]);

        line = new CSVLineReader().parseLine("101,\"{\"\"name\"\":\"\"mockedName\"\"}\"");
        Assert.assertEquals(2, line.length);
        Assert.assertEquals("101", line[0]);
        Assert.assertEquals("{\"name\":\"mockedName\"}", line[1]);
    }
}
