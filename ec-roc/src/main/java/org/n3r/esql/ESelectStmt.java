package org.n3r.esql;

import java.io.Closeable;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.n3r.core.lang.RClose;
import org.n3r.esql.ex.EsqlExecuteException;
import org.n3r.esql.impl.EsqlRsRetriever;
import org.n3r.esql.param.EsqlParamsBinder;
import org.n3r.esql.res.EsqlSub;
import org.slf4j.Logger;

public class ESelectStmt implements Closeable, EStmt {
    private ResultSet resultSet;
    private PreparedStatement preparedStatement;
    private boolean resultSetNext;
    private EsqlRsRetriever rsRetriever;
    private int rowNum;
    private EsqlSub subSql;
    private Logger logger;
    private Object[] params;
    private int fetchSize;

    public void executeQuery() {
            executeQuery(params);
    }

    public void executeQuery(Object... params) {
        resultSetNext = true;
        rowNum = 0;
        try {
            new EsqlParamsBinder().bindParams(preparedStatement, subSql, params, logger);
            resultSet = preparedStatement.executeQuery();
            if (fetchSize > 0) resultSet.setFetchSize(fetchSize);
        } catch (SQLException e) {
            throw new EsqlExecuteException("executeQuery", e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T next() {
        if (!resultSetNext)
            return null;

        try {
            T rowBean = (T) rsRetriever.selectRow(resultSet, ++rowNum);
            if (rowBean == null) {
                resultSetNext = false;
                closeRs();
            }
            return rowBean;
        } catch (SQLException e) {
            throw new EsqlExecuteException("select row", e);
        }
    }

    public void closeRs() {
        RClose.closeQuietly(resultSet);
        resultSet = null;
    }

    @Override
    public void closeStmt() {
        RClose.closeQuietly(preparedStatement);
        preparedStatement = null;
    }

    @Override
    public void setPreparedStatment(PreparedStatement preparedStatement) {
        this.preparedStatement = preparedStatement;
    }

    public void setRsRetriever(EsqlRsRetriever rsRetriever) {
        this.rsRetriever = rsRetriever;
    }

    @Override
    public void close() throws IOException {
        closeRs();
        closeStmt();
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
    public void setEsqlTran(EsqlTran esqlTran) {
    }

    @Override
    public void setParams(Object[] params) {
        this.params = params;
    }

    @Override
    public Object[] getParams() {
        return this.params;
    }

    public void setFetchSize(int fetchSize) {
       this.fetchSize = fetchSize;
    }
}
