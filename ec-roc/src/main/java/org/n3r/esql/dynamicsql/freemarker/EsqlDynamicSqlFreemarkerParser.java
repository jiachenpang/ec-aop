package org.n3r.esql.dynamicsql.freemarker;

import org.n3r.esql.dynamicsql.EsqlDynamicSqlParsable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class EsqlDynamicSqlFreemarkerParser implements EsqlDynamicSqlParsable {
    private Logger log = LoggerFactory.getLogger(EsqlDynamicSqlFreemarkerParser.class);

    @Override
    public List<String> parseDynamicSql(List<String> sqlLines, int index) {
        String sqlLine = sqlLines.get(index);
        sqlLine = sqlLine.trim();
        if (!sqlLine.startsWith("<#")) return null;
        List<String> dynamicSqlLines = new ArrayList<String>();
        dynamicSqlLines.add(sqlLines.get(index));

        for (int i = index + 1, ii = sqlLines.size(); i < ii; ++i) {
            sqlLine =  sqlLines.get(i).trim();
            dynamicSqlLines.add(sqlLines.get(i));
            if (sqlLine.startsWith("</#")) return dynamicSqlLines;
        }

        // 没有检测到</#结束标记
        log.debug("no end tag detected in freemarker template");

        return null;
    }
}
