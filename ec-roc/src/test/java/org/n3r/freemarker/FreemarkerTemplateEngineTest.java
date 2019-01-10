package org.n3r.freemarker;

import freemarker.template.Template;
import org.junit.Assert;
import org.junit.Test;
import org.n3r.core.collection.RMap;
import org.n3r.core.lang.RDate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FreemarkerTemplateEngineTest {

    @Test
    public void testCurrentTimeFunction() throws Exception {
        Template template = FreemarkerTemplateEngine.putTemplate("millis", "${roc.now()}");
        String result = FreemarkerTemplateEngine.process(template);
        Assert.assertTrue(result.matches("\\d{13}"));

        template = FreemarkerTemplateEngine.putTemplate("day", "${roc.now('yyyy-MM-dd')}");
        result = FreemarkerTemplateEngine.process(template);
        Assert.assertEquals(RDate.toDateStr("yyyy-MM-dd"), result);

        template = FreemarkerTemplateEngine.putTemplate("self", "${roc.now(format)}");
        result = FreemarkerTemplateEngine.process(RMap.asMap("format", "yyyy-MM-dd HH"), template);
        Assert.assertEquals(RDate.toDateStr("yyyy-MM-dd HH"), result);
    }

    @Test
    public void testLoopList() throws Exception {
        String templateSource = "<#list numId as num>"
                                  + "<NumID><ProKey>EAOP</ProKey><SerialNumber>${num.serialNumber}"
                                  + "</SerialNumber><ServiceType>01</ServiceType></NumID>"
                              + "</#list>";

        Template template = FreemarkerTemplateEngine.putTemplate("loopList", templateSource);

        Map root = RMap.newHashMap();
        Map map1 = RMap.of("serialNumber", "bruce");
        Map map2 = RMap.of("serialNumber", "wenbin");
        List numId = new ArrayList<Map>();
        numId.add(map1);
        numId.add(map2);
        root.put("numId", numId);

        String result = FreemarkerTemplateEngine.process(root, template);

        String expected = "<NumID><ProKey>EAOP</ProKey><SerialNumber>bruce</SerialNumber><ServiceType>01"
                        + "</ServiceType></NumID><NumID><ProKey>EAOP</ProKey><SerialNumber>wenbin</SerialNumber>"
                        + "<ServiceType>01</ServiceType></NumID>";

        Assert.assertEquals(expected, result);
    }

    @Test
    public void testLoopMap() throws Exception {
        String templateSource = "<#list testMap?keys as testKey>"
                                  + "${testKey}=${testMap[testKey]}&"
                              + "</#list>";

        Template template = FreemarkerTemplateEngine.putTemplate("loopMap", templateSource);

        Map root = RMap.newHashMap();
        Map map = RMap.of("bruce", "wenbin", "bigoo", "hjb");
        root.put("testMap", map);

        String result = FreemarkerTemplateEngine.process(root, template);

        String expected = "bruce=wenbin&bigoo=hjb&";

        Assert.assertEquals(expected, result);
    }

    public static class JavaBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
    @Test
    public void testJavaBean() throws Exception {
        JavaBean bean = new JavaBean();
        bean.setName("bingoo");

        Template dingdang = FreemarkerTemplateEngine.putTemplate("dingdang", "hello ${name}");
        String process = FreemarkerTemplateEngine.process(bean, dingdang);
        Assert.assertEquals("hello bingoo", process);


        dingdang = FreemarkerTemplateEngine.putTemplate("dingdang1", "<#switch a>\n" +
                "<#case 1>\n" +
                "  WHERE A = 1\n" +
                "  <#break>\n" +
                "<#case 2>\n" +
                "  WHERE A = 2\n" +
                "  <#break>\n" +
                "<#default>\n" +
                "  WHERE A = 3\n" +
                "</#switch>");
        process = FreemarkerTemplateEngine.process(RMap.of("a", 2), dingdang);
        Assert.assertEquals("  WHERE A = 2\n", process);

    }
}
