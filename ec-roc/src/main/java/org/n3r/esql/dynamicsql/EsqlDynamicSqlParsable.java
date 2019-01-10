package org.n3r.esql.dynamicsql;

import java.util.List;

/**
 * ESQL解析动态SQL部分的接口
 */
public interface EsqlDynamicSqlParsable {
    List<String> parseDynamicSql(List<String> sqlLines, int index);
}
