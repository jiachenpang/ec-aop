package org.n3r.config.impl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Properties;

import org.n3r.config.ex.ConfigException;
import org.n3r.core.lang.RClose;
import org.springframework.core.io.Resource;

import com.google.common.base.Charsets;

public class TableConfigable extends DefaultConfigable {

    public TableConfigable(Resource res) {
        super(buildProperties(res));
    }

    private static Properties buildProperties(Resource res) {
        Properties props = new Properties();

        Reader reader = null;
        try {
            reader = new InputStreamReader(res.getInputStream(), Charsets.UTF_8);
            TableReader tableReader = new TableReader(reader);
            List<ConfigTable> tables = tableReader.getTables();
            for (ConfigTable table : tables) {
                String tableName = table.getTableName();
                if (props.containsKey(tableName)) {
                    throw new ConfigException(
                            "duplicate key [" + tableName + "] in file...");
                }
                props.put(tableName, table);
            }

        }
        catch (IOException e) {
        }
        finally {
            RClose.closeQuietly(reader);
        }
        return props;
    }

}
