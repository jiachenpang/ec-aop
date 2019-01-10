package org.n3r.esql.demo;

import freemarker.template.Template;
import ognl.Ognl;
import org.apache.commons.beanutils.BeanUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.n3r.esql.Esql;
import org.n3r.freemarker.FreemarkerTemplateEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class InTest {
    @BeforeClass
    public static void beforeClass() {
        new Esql().id("setup").execute();
    }

    @AfterClass
    public static void afterClass() {
        new Esql().id("teardown").execute();
    }

    @Test
    public void testFtl() throws Exception {
        InBean inBean = new InBean();

        assertEquals("2", BeanUtils.getProperty(inBean, "lst[1]"));
        assertEquals("b", BeanUtils.getProperty(inBean, "arr[1]"));

        Map m = new HashMap();
        m.put("p", inBean.getLst());
        m.put("p2", "abc");
        assertEquals("1", Ognl.getValue("p[0]", m));
        // assertEquals("1", BeanUtils.getProperty(m, "p[0]"));
        // Above: java.lang.IllegalArgumentException: Indexed or mapped properties are not supported on objects of type Map: p[0]


        String sql = "select * from dual " +
                "where 1 in (''" +
                "<#list a as x>" +
                ",#a[${x_index}]#" +
                "</#list>" +
                ")";

        Template test = FreemarkerTemplateEngine.putTemplate("test", sql);
        Map map = new HashMap();
        map.put("a", inBean.getLst());
        String trueSql = FreemarkerTemplateEngine.process(map, test);
        assertEquals("select * from dual where 1 in ('',#a[0]#,#a[1]#,#a[2]#,#a[3]#)", trueSql);
    }

    @Test
    public void testIn() throws Exception {
        InBean inBean = new InBean();
        new Esql().id("testIn").params(inBean).execute();
    }

    @Test
    public void testInsertAll() throws Exception {
        InBean inBean = new InBean();
        new Esql().id("testInsertAll").params(inBean).execute();
    }

    @Test
    public void testInSeq() throws Exception {
        InBean inBean = new InBean();
        new Esql().id("testInSeq").params(inBean.getLst()).execute();
    }

    @Test
    public void testInsertAllSeq() throws Exception {
        InBean inBean = new InBean();
        new Esql().id("testInsertAllSeq").params(inBean.getLst(), inBean.getId()).execute();
    }

    public static class InBean {
        private List<String> lst = new ArrayList<String>();

        {
            lst.add("1");
            lst.add("2");
            lst.add("3");
            lst.add("4");
        }

        private String[] arr;

        {
            arr = new String[]{"a", "b", "c"};
        }

        private String id = "" + System.currentTimeMillis();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public List<String> getLst() {
            return lst;
        }

        public void setLst(List<String> lst) {
            this.lst = lst;
        }

        public String[] getArr() {
            return arr;
        }

        public void setArr(String[] arr) {
            this.arr = arr;
        }
    }
}
