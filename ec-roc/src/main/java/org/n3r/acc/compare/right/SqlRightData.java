package org.n3r.acc.compare.right;

import org.n3r.acc.compare.FieldReference;
import org.n3r.acc.compare.Record;
import org.n3r.acc.compare.impl.FieldsRecord;
import org.n3r.acc.utils.AccUtils;
import org.n3r.config.Configable;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.tag.FromSpecConfig;
import org.n3r.esql.Esql;
import org.n3r.esql.map.EsqlRowMapper;
import org.n3r.esql.util.EsqlUtils;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@EcRocTag("Sql")
public class SqlRightData implements FromSpecConfig<SqlRightData>, RightData, EsqlRowMapper {
    private String connectionName;
    private String sqlid;
    private String sqlfile;
    private Record stopRecord = new StopRecord();
    private BlockingQueue<Record> queue = new ArrayBlockingQueue<Record>(10000, true);
    private Map<String, String> context;

    @Override
    public void start() {
        final EsqlRowMapper mapper = this;
        new Thread() {
            @Override
            public void run() {
                new Esql(connectionName).useSqlFile(sqlfile).select(sqlid)
                        .params(context)
                        .returnType(mapper).setFetchSize(10000).execute();
                putToQueue(stopRecord);
            }
        }.start();
    }

    private void putToQueue(Record record) {
        try {
            queue.put(record);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private Record takeFromQueue() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Record mapRow(ResultSet rs, int rowNum) throws SQLException {
        putToQueue(AccUtils.mapRow(rs, rowNum));

        return null;
    }

    @Override
    public Record readRecord(FieldReference keyReference) {
        Record record = takeFromQueue();
        if (record == stopRecord) return null;

        ((FieldsRecord)record).createKey(keyReference);
        return record;
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public SqlRightData fromSpec(Configable config, Map<String, String> context) {
        this.connectionName = config.getStr("connectionName", Esql.DEFAULT_CONN_NAME);
        this.sqlid = config.getStr("sqlid");
        this.sqlfile = config.getStr("sqlfile");
        this.context = context;

        return this;
    }

    private static class StopRecord implements Record {

        @Override
        public String getKey() {
            return null;
        }

        @Override
        public Object getValue(FieldReference compareField) {
            return null;
        }
    }


}



