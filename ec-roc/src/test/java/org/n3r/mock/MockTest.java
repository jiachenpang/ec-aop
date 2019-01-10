package org.n3r.mock;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.BeforeClass;
import org.junit.Test;
import org.n3r.config.Config;
import org.n3r.core.lang.RStr;
import org.n3r.esql.Esql;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

public class MockTest {

    private static final Pattern paramParams = Pattern
            .compile("\\w+([.$]\\w+)*\\s*(\\(\\s*[.\\w]+\\s*(,\\s*[.\\w]+\\s*)*\\))?");
    final static String tableName = getConfigParams()[0];
    final static String connectionName = getConfigParams()[1];

    @BeforeClass
    public static void beforeClass() {
        new Esql(connectionName).dynamics(tableName).update("createTable").execute();
        new Esql(connectionName).dynamics(tableName).update("insertData").execute();
    }

    private static String[] getConfigParams() {
        String propertyValue = Config.getStr("mockImpl", "org.n3r.mock.impl.RmdbMockImpl(n3_mock, mockConnection)");

        Matcher matcher = paramParams.matcher(propertyValue);
        Splitter splitter = Splitter.on(',').trimResults();
        String[] strArray = null;
        while (matcher.find()) {
            String group = matcher.group().trim();
            int posBrace = group.indexOf('(');
            strArray = Iterables.toArray(splitter.split(RStr.substrInQuotes(group, '(', posBrace)), String.class);
        }
        return strArray;
    }

    @Test
    public void testSimple() {
        String mockRsp = Mock.mock("here is key", null);
        assertEquals("here is value", mockRsp);
    }

    @Test
    public void testCondition() {
        HashMap<String, String> in = Maps.newHashMap();
        in.put("condition", "Rocket-Man");
        String mockRsp = Mock.mock("condition key", in);
        in.put("condition", "Iron-Man");
        mockRsp = Mock.mock("condition key", in);
        assertEquals("Iron-Man", mockRsp);
    }

@Test
public void testConditionPriority() {
    HashMap<String, String> in = Maps.newHashMap();
    in.put("condition_height", "I am taller!");
    in.put("condition_weight", "I am fatter!");
    String mockRsp = Mock.mock("priority key", in);
    assertEquals("you win!fatter!", mockRsp);
}

    @Test
    public void testNoMockConfig() {
        HashMap<String, String> in = Maps.newHashMap();
        in.put("id", "321321198706245812");
        String mockRsp = Mock.mock("ecaop.trades.query.common.certificate.check.Null", in);
        assertEquals("", mockRsp);
    }

    @Test
    public void testNormal() {
        HashMap<String, String> in = Maps.newHashMap();
        in.put("id", "321321198706245812");
        String mockRsp = Mock.mock("ecaop.trades.query.common.certificate.check", in);
        assertEquals("19890624", mockRsp);
    }

    @Test
    public void testDefaultMock() {
        HashMap<String, String> in = Maps.newHashMap();
        in.put("id", "321321198706245814");
        String mockRsp = Mock.mock("ecaop.trades.query.common.certificate.check", in);
        assertEquals("19890628", mockRsp);
    }

    @Test
    public void testOnlyDefalutMock() {
        String mockRsp = Mock.mock("ecaop.trades.query.common.certificate.check2", new Object());
        assertEquals("19890630", mockRsp);
    }

    @Test
    public void testPriority() {
        HashMap<String, String> in = Maps.newHashMap();
        in.put("id", "321321198706245812");
        in.put("name", "wang");
        String mockRsp = Mock.mock("ecaop.trades.query.common.certificate.check3", in);
        assertEquals("19890628", mockRsp);
    }


    @Test
    public void noProperCondition() {
        HashMap<String, String> in = Maps.newHashMap();
        in.put("id", "32132119870624581X");
        String mockRsp = Mock.mock("ecaop.trades.query.common.certificate.nocondition", in);
        assertEquals("", mockRsp);
    }

}
