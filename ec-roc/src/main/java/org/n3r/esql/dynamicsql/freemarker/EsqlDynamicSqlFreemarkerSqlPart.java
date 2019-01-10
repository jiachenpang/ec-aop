package org.n3r.esql.dynamicsql.freemarker;

import com.google.common.base.Joiner;
import freemarker.template.Template;
import org.n3r.esql.res.EsqlPart;
import org.n3r.esql.util.EsqlUtils;
import org.n3r.freemarker.FreemarkerTemplateEngine;

import java.util.List;


public class EsqlDynamicSqlFreemarkerSqlPart implements EsqlPart {
    private final Template template;

    public EsqlDynamicSqlFreemarkerSqlPart(List<String> dynamicSqlLines, String dynamicSqlLinesKey) {
        template = FreemarkerTemplateEngine.putTemplate(dynamicSqlLinesKey, Joiner.on(' ').join(dynamicSqlLines));
    }

    @Override
    public String getSqlPart(Object bean) {
        String sql = FreemarkerTemplateEngine.process(bean, template);
        return sql;
    }
}
