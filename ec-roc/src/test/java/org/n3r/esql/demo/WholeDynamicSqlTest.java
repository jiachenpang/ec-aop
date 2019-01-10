package org.n3r.esql.demo;

import org.junit.*;
import org.n3r.core.collection.RMap;
import org.n3r.esql.Esql;

public class WholeDynamicSqlTest {
    @BeforeClass
    public static  void setup() {
        new Esql().id("setup").execute();
    }

    @AfterClass
    public static void teardown() {
        new Esql().id("teardown").execute();
    }

    @Test
    public void test1() {
        new Esql().id("insertDemo").execute();
    }

    @Test
    public void test2() {
        new Esql().id("insertDemo").params(RMap.asMap("params", "fuck")).execute();
    }
}
