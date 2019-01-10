package org.n3r.esql;

import java.io.Closeable;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.n3r.core.lang.RClose;
import org.n3r.esql.ex.EsqlExecuteException;
import org.n3r.esql.param.EsqlParamsBinder;
import org.n3r.esql.res.EsqlSub;
import org.slf4j.Logger;

public class EUpdateStmt implements Closeable, EStmt {
    private boolean autoCommit = true;
    private PreparedStatement preparedStatement;
    private EsqlSub subSql;
    private Logger logger;
    private EsqlTran esqlTran;
    private Object[] params;

    @Override
    public void close() {

    }

    public int update() {
        return update(params);
    }

    public int update(Object ...params) {
        new EsqlParamsBinder().bindParams(preparedStatement, subSql, params, logger);
        int ret;
        try {
            ret = preparedStatement.executeUpdate();
            if(autoCommit && esqlTran != null) esqlTran.commit();
            return ret;
        } catch (SQLException e) {
            throw new EsqlExecuteException("executeUpdate failed", e);
        }

    }

    public void setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    public void commit() {
        if(esqlTran != null) esqlTran.commit();
    }

    public void rollback() {
        if(esqlTran != null) esqlTran.rollback();
    }

    @Override
    public void setPreparedStatment(PreparedStatement preparedStatement) {
        this.preparedStatement = preparedStatement;
    }

    @Override
    public void setSubSql(EsqlSub subSql) {
        this.subSql = subSql;
    }

    @Override
    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void closeStmt() {
        RClose.closeQuietly(preparedStatement );
        preparedStatement = null;
    }

    @Override
    public void setEsqlTran(EsqlTran esqlTran) {
        this.esqlTran = esqlTran;
    }

    @Override
    public Object[] getParams() {
        return params;
    }

    @Override
    public void setParams(Object[] params) {
        this.params = params;
    }


}
