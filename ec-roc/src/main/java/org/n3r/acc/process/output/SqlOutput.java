package org.n3r.acc.process.output;

import org.n3r.config.Configable;
import org.n3r.core.tag.EcRocTag;
import org.n3r.esql.Esql;

import java.util.Map;

@EcRocTag("Sql")
public class SqlOutput extends DirectOutput {
    private String sqlid;
    private String connectionName;
    private String sqlfile;
    private Esql esql;
    private int current = 0;
    private int batchNum = 1000;


    @Override
    public void outputLine(Object[] fields) {
        Object[] outputFields = fields;
        if (outputIndices.length > 0) {
            outputFields = new Object[outputIndices.length];
            int outputIndex = 0;
            for (int index : outputIndices) outputFields[outputIndex++] = fields[index];
        }

        if (current == 0) esql.startBatch();

        esql.params(outputFields).execute();

        if (++current == batchNum) clearBatch();
    }

    @Override
    public SqlOutput fromSpec(Configable config, Map<String, String> context) {
        this.connectionName = config.getStr("connectionName", Esql.DEFAULT_CONN_NAME);
        this.sqlid = config.getStr("sqlid");
        this.sqlfile = config.getStr("sqlfile");
        esql = new Esql(connectionName).useSqlFile(sqlfile).id(sqlid);

        super.fromSpec(config, context);
        return this;
    }


    @Override
    public void finishOutput() {
        clearBatch();
    }

    private void clearBatch() {
        if (current <= 0) return;

        esql.executeBatch();
        current = 0;
    }
}
