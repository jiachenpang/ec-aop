package org.n3r.acc.compare.diff;

import org.n3r.acc.compare.Record;
import org.n3r.acc.compare.impl.FieldsRecord;
import org.n3r.acc.utils.AccUtils;
import org.n3r.config.Configable;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.tag.FromSpecConfig;
import org.n3r.esql.Esql;
import org.n3r.esql.map.EsqlRowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

@EcRocTag("tryFindRight")
public class TryFindRightDiffListener implements OnlyLeftDiffListener, FromSpecConfig<TryFindRightDiffListener>, EsqlRowMapper {
    private Esql esql;
    private FieldsRecord rightRecord;
    private Map<String, String> context;
    @Override
    public Record onLeftRight(Record record) {
        rightRecord = null;
        esql.params(context.get("provinceCode"), record.getKey()).returnType(this).execute();

        if (rightRecord != null) rightRecord.setKey(record.getKey());

        return rightRecord;
    }

    @Override
    public TryFindRightDiffListener fromSpec(Configable config, Map<String, String> context) {
        String connectionName = config.getStr("diff.connectionName", Esql.DEFAULT_CONN_NAME);
        String sqlid = config.getStr("diff.sqlid4TryFindRight");
        String sqlfile = config.getStr("diff.sqlfile");
        this.context = context;
        esql = new Esql(connectionName).useSqlFile(sqlfile).id(sqlid);

        return this;
    }

    @Override
    public Object mapRow(ResultSet rs, int rowNum) throws SQLException {
        this.rightRecord = AccUtils.mapRow(rs, rowNum);

        return null;
    }
}
