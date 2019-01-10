package org.n3r.esql.map;

import static org.n3r.esql.util.EsqlUtils.*;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SingleValueMapper implements EsqlRowMapper {

    @Override
    public Object mapRow(ResultSet rs, int rowNum) throws SQLException {
        return getResultSetValue(rs, 1);
    }

}
