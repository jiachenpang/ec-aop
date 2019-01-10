package org.n3r.esql;

import java.sql.PreparedStatement;

import org.n3r.esql.res.EsqlSub;
import org.slf4j.Logger;

public interface EStmt {
    void setPreparedStatment(PreparedStatement preparedStatement);

    void setSubSql(EsqlSub subSql);

    void setLogger(Logger logger);

    void setEsqlTran(EsqlTran esqlTran);

    void closeStmt();

    void setParams(Object[] params);

    Object[] getParams();

}
