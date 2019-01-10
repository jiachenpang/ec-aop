package org.n3r.config.impl;

import static org.junit.Assert.assertEquals;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.n3r.config.Config;
import org.n3r.core.lang.RStr;
import org.n3r.esql.Esql;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

public class RMDBConfigableTest {

    private static final Pattern paramParams = Pattern
            .compile("\\w+([.$]\\w+)*\\s*(\\(\\s*[.\\w]+\\s*(,\\s*[.\\w]+\\s*)*\\))?");
    final static String connectionName = getConnectionName();

    @BeforeClass
    public static void beforeTest() {

        new Esql(connectionName).update("dropConfigTable").execute();
        new Esql(connectionName).update("createConfigTable").execute();
        new Esql(connectionName).update("insertConfigData").execute();
    }

    private static String getConnectionName() {
        String propertyValue = Config.getStr("config.implementation");

        Matcher matcher = paramParams.matcher(propertyValue);
        Splitter splitter = Splitter.on(',').trimResults();

        String connectionName = "";
        while (matcher.find()) {
            String group = matcher.group().trim();
            int posBrace = group.indexOf('(');
            String[] strArray = Iterables.toArray(splitter.split(RStr.substrInQuotes(group, '(', posBrace)), String.class);
            connectionName = strArray[0];
        }
        return connectionName;
    }

    @AfterClass
    public static void afterClass() {



    }

    @Test
    public void test1() {
//        assertEquals("value1", RefreshConfig.getStr("config.key1"));
        assertEquals(200, Config.getInt("config.key2"));
        assertEquals(true, Config.getBool("config.key3"));
    }

    @Test
    public void test2() {
        assertEquals(6, Config.refreshConfigSet("config"));
        assertEquals(7, Config.refreshConfigSet(null));

        new Esql(connectionName).update("increaseConfigKey1Version").execute();

        assertEquals(7, Config.refreshConfigSet("config"));

    }

    public static void main(String[] args) {
        long sum = Config.refreshConfigSet("config2");
        System.out.println(sum);
    }
}
