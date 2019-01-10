package org.n3r.acc.process.filter;

import org.apache.commons.lang3.StringUtils;
import org.n3r.acc.process.output.DataOutput;
import org.n3r.config.Configable;
import org.n3r.core.io.RFile;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.utils.RCmd;
import org.n3r.esql.Esql;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Providing a wrapper of oracle's sqlldr for easy use.
 */
@EcRocTag("Sqlldr")
public class SqlldrFilter implements DataFileFilter{
    private Logger logger = LoggerFactory.getLogger(SqlldrFilter.class);
    private String importFile;
    private String tableName;
    private String connectionName;
    private String dateFormat;
    private String userid;

    @Override
    public void filter(InputStream is, DataOutput dataOutput) {
        File ctrl = RFile.createTempFile(tableName, ".ctrl");
        RFile.writeStringToFile(createCtrlFileContent(), ctrl);
        logger.info("create sqlldr ctrl file {} with content {}", ctrl.getAbsolutePath(), ctrl);

        File log = RFile.createTempFile(tableName, ".log");

        RCmd cmd = new RCmd("sqlldr", userid, "control=" + ctrl.getAbsolutePath(),
                "log=" + log.getAbsolutePath(), "parallel=true", "direct=true");

        cmd.syncExec(5 * 60 * 1000); // 5 min timeout

        logger.info("sqlldr log content {}", RFile.readFileToString(log));

        ctrl.delete();
        log.delete();
    }

    private String createCtrlFileContent() {
        return new StringBuilder()
                    .append("LOAD DATA\r\n")
                    .append("INFILE '" + importFile + "'\r\n")
                    .append("APPEND INTO TABLE " + tableName + "\r\n")
                    .append("FIELDS TERMINATED BY ',' \r\n")
                    .append(createmportTableFields()).toString();
    }

    private String createmportTableFields() {
        List<Map<String, Object>> result = new Esql(connectionName).params(tableName)
                 .execute("SELECT * FROM USER_TAB_COLUMNS T WHERE T.TABLE_NAME = ##");
        StringBuilder fields = new StringBuilder("(");
        boolean firstField = true;
        for (Map<String, Object> col: result) {
            if (firstField) firstField = false;
            else fields.append(',');

            fields.append(col.get("COLUMN_NAME"));
            String dateType = (String) col.get("DATA_TYPE");
            if ("DATE".equals(dateType)) {
                fields.append(" DATE '").append(dateFormat).append('\'');
            }
        }
        fields.append(')');
        return fields.toString();
    }

    public SqlldrFilter fromSpec(Configable config) {
        connectionName = config.getStr("connectionName", Esql.DEFAULT_CONN_NAME);
        tableName = config.getStr("tableName");
        if (StringUtils.isEmpty(tableName))
            throw new RuntimeException("tableName is required for mysql load");

        tableName = tableName.toUpperCase();

        importFile = checkImportFile(config.getStr("importFile"));
        dateFormat = config.getStr("date.format", "yyyy-mm-dd hh24:mi:ss");

        userid = checkUserid(config.getStr("userid"));

        return this;
    }

    public SqlldrFilter importFile(String importFile) {
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

    private String checkUserid(String userid) {
        if (StringUtils.isEmpty(userid))
            throw new RuntimeException("userid is required for mysql load");

        return userid;
    }
}
