package org.n3r.esql.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.n3r.core.lang.RStr;
import org.n3r.esql.dynamicsql.EsqlDynamicSqlFactory;
import org.n3r.esql.dynamicsql.EsqlDynamicSqlParsable;
import org.n3r.esql.dynamicsql.freemarker.EsqlDynamicSqlFreemarkerFactory;
import org.n3r.esql.res.EsqlItem;
import org.n3r.esql.res.EsqlPart;
import org.n3r.esql.res.EsqlPartLiteral;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EsqlSubParser {
    private static Logger log = LoggerFactory.getLogger(EsqlSubParser.class);
    private static EsqlDynamicSqlParsable dynamicSqlParser;
    private static EsqlDynamicSqlFactory dynamicSqlFactory;
    static {
//        String dynamicSqlFactoryConfig = Config.getStr("esql.dynamicsql.factory");
//        if (StringUtils.isNotEmpty(dynamicSqlFactoryConfig)) {
//            dynamicSqlFactory = Reflect.on(dynamicSqlFactoryConfig).create().get();
//        }
//        else {
//            dynamicSqlFactory = new EsqlDynamicSqlFreemarkerFactory();
//        }
        dynamicSqlFactory = new EsqlDynamicSqlFreemarkerFactory();
        dynamicSqlParser = dynamicSqlFactory.createParser();
    }

    public static List<EsqlPart> parse(Map<String, EsqlItem> sqlFile, List<String> rawSqlLines, String sqlId) {
        StringBuilder sql = new StringBuilder();
        List<EsqlPart> sqlParts = new ArrayList<EsqlPart>();
        int seq = 0;

        List<String> sqlLines = processIncludes(sqlFile, rawSqlLines);
        for (int i = 0, ii = sqlLines.size(); i < ii; ++i) {
            String sqlLine = sqlLines.get(i);
            List<String> dynamicSqlLines;
            EsqlCondStr condition = new EsqlCondStr(sqlLine);
            int jumpLines = 0;
            if (condition.isQuoted() && "#dynamic".equals(condition.getConditionKey()))  {
                dynamicSqlLines = parseDynamicSql(sqlLines, i);
                jumpLines = dynamicSqlLines != null ? dynamicSqlLines.size() + 1 : 0;  // 包括两个包含标记行：<#dynamic>与</#dynamic行>
            }
            else {
                dynamicSqlLines = dynamicSqlParser.parseDynamicSql(sqlLines, i);
                jumpLines = dynamicSqlLines != null ? dynamicSqlLines.size() - 1 : 0;
            }

            if (dynamicSqlLines == null) {
                sql.append(sqlLine).append(' ');
                continue;
            }

            if (StringUtils.isNotBlank(sql)) {
                sqlParts.add(new EsqlPartLiteral(RStr.trimRight(sql.toString())));
                RStr.clear(sql);
            }

            sqlParts.add(dynamicSqlFactory.createSqlPart(dynamicSqlLines, sqlId + (seq++)));
            i +=  jumpLines;
        }

        if (sql.length() > 0) sqlParts.add(new EsqlPartLiteral(sql.toString()));

        return sqlParts;
    }

    private static List<String> parseDynamicSql(List<String> sqlLines, int index) {
        List<String> dynamicSqlLines = new ArrayList<String>();

        for (int i = index + 1, ii = sqlLines.size(); i < ii; ++i) {
            String sqlLine =  sqlLines.get(i).trim();

            if (sqlLine.equals("</#dynamic>")) {
                return dynamicSqlLines;
            }
            dynamicSqlLines.add(sqlLines.get(i));
        }

        // 没有检测到</#结束标记
        log.debug("no end tag detected in template");
        return null;
    }



    private static List<String> processIncludes(Map<String, EsqlItem> sqlFile, List<String> rawSqlLines) {
        ArrayList<String> sqlLines = new ArrayList<String>();
        for (String line : rawSqlLines) {
            EsqlCondStr condition = new EsqlCondStr(line);
            if (condition.isQuoted() && "#include".equals(condition.getConditionKey())) {
                EsqlItem esqlItem = sqlFile.get(condition.getConditionValue());
                List<String> includeSqlLines = esqlItem.getRawSqlLines();
                sqlLines.addAll(processIncludes(sqlFile, includeSqlLines));
            } else sqlLines.add(line);
        }

        return sqlLines;
    }

}
