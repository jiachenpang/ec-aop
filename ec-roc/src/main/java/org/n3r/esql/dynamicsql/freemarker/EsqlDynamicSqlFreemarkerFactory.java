package org.n3r.esql.dynamicsql.freemarker;

import org.n3r.esql.dynamicsql.EsqlDynamicSqlFactory;
import org.n3r.esql.dynamicsql.EsqlDynamicSqlParsable;
import org.n3r.esql.res.EsqlPart;

import java.util.List;

public class EsqlDynamicSqlFreemarkerFactory implements EsqlDynamicSqlFactory {
    @Override
    public EsqlDynamicSqlParsable createParser() {
        return new EsqlDynamicSqlFreemarkerParser();
    }

    @Override
    public EsqlPart createSqlPart(List<String> dynamicSqlLines, String dynamicSqlLinesKey) {
        return new EsqlDynamicSqlFreemarkerSqlPart(dynamicSqlLines, dynamicSqlLinesKey);
    }
}

