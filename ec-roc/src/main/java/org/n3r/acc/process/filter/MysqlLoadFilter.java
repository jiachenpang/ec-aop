package org.n3r.acc.process.filter;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.n3r.acc.process.output.DataOutput;
import org.n3r.config.Configable;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.tag.FromSpecConfig;
import org.n3r.esql.Esql;

import java.io.File;
import java.io.InputStream;
import java.util.Map;

@EcRocTag("MysqlLoad")
public class MysqlLoadFilter implements DataFileFilter, FromSpecConfig<MysqlLoadFilter> {
    private Esql esql;
    private String tableName;
    private String importFile;

    @Override
    public void filter(InputStream is, DataOutput dataOutput) {
        // 转义反斜杠
        String fullFileName = StringEscapeUtils.ESCAPE_JAVA.translate(importFile);
        esql.dynamics(tableName, fullFileName).execute();
    }

    @Override
    public MysqlLoadFilter fromSpec(Configable config, Map<String, String> context) {
        String connectionName = config.getStr("connectionName", Esql.DEFAULT_CONN_NAME);
        String sqlid = config.getStr("sqlid");
        String sqlfile = config.getStr("sqlfile");
        esql = new Esql(connectionName).useSqlFile(sqlfile).id(sqlid);

        tableName = config.getStr("tableName");
        if (StringUtils.isEmpty(tableName))
            throw new RuntimeException("tableName is required for mysql load");

        importFile = checkImportFile(config.getStr("importFile"));

        return this;
    }

    public MysqlLoadFilter importFile(String importFile) {
        this.importFile = checkImportFile(importFile);
        return this;
    }

    private String checkImportFile(String importFile) {
        if (StringUtils.isEmpty(importFile))
            throw new RuntimeException("importFile is required for mysql load");
        if (!new File(importFile).exists())
            throw new RuntimeException("importFile " + importFile + " does not exist");

        return importFile;
    }
}
