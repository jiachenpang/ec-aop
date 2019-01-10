package org.n3r.acc.utils;

import org.n3r.acc.compare.impl.FieldsRecord;
import org.n3r.config.Configable;
import org.n3r.core.tag.EcTagUtils;
import org.n3r.core.tag.FromSpecConfig;
import org.n3r.core.tag.Spec;
import org.n3r.core.tag.SpecParser;
import org.n3r.esql.util.EsqlUtils;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;

public class AccUtils {
    public static FieldsRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        Object[] fieldsValue = new Object[metaData.getColumnCount()];
        String[] fieldsName = new String[metaData.getColumnCount()];
        for (int i = 0, ii = metaData.getColumnCount(); i < ii; ++i) {
            fieldsName[i] = EsqlUtils.lookupColumnName(metaData, i + 1);
            fieldsValue[i] = EsqlUtils.getResultSetValue(rs, i + 1);
        }

        return new FieldsRecord(fieldsValue, fieldsName, null);
    }

    public static void tryFromSpec(Object object, Configable config, Map<String, String> context) {
        if (object instanceof FromSpecConfig)
            ((FromSpecConfig) object).fromSpec(config, context);
    }

    public static <T> T parseSpec(String specConfig, Class<T> clazz) {
        Spec spec = SpecParser.parseSpec(specConfig);
        return EcTagUtils.parseTag(clazz, spec);
    }

    public static <T> T parseSpec(String specConfig, Class<T> clazz, Configable config, Map<String, String> context) {
        T instance = AccUtils.parseSpec(specConfig, clazz);
        AccUtils.tryFromSpec(instance, config, context);

        return instance;
    }
}
