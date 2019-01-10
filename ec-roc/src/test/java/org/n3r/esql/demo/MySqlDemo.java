package org.n3r.esql.demo;

import org.junit.Test;
import org.n3r.esql.Esql;
import org.n3r.esql.EsqlPage;

import java.util.List;
import java.util.Map;

public class MySqlDemo {
    @Test
    public void pagingSqlShouldWork() {
        List<Map<String, Object>> result = new Esql("mysql").id("pagingDemo").limit(new EsqlPage(3,10)).execute();
        System.out.println(result);


        int id = (Integer) new Esql("mysql").selectFirst("autoIncr").execute();
        System.out.println(id);
    }
}
