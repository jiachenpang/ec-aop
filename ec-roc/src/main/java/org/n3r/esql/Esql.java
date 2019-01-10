package org.n3r.esql;

import com.google.common.collect.Lists;
import org.n3r.config.Configable;
import org.n3r.core.lang.RClose;
import org.n3r.esql.config.EsqlConfigManager;
import org.n3r.esql.ex.EsqlExecuteException;
import org.n3r.esql.ex.EsqlIdNotFoundException;
import org.n3r.esql.impl.DbTypeFactory;
import org.n3r.esql.impl.EsqlBatch;
import org.n3r.esql.impl.EsqlExecInfo;
import org.n3r.esql.impl.EsqlRsRetriever;
import org.n3r.esql.map.EsqlRowMapper;
import org.n3r.esql.param.EsqlParamPlaceholder.InOut;
import org.n3r.esql.param.EsqlParamsBinder;
import org.n3r.esql.param.EsqlParamsParser;
import org.n3r.esql.parser.EsqlDynamicReplacer;
import org.n3r.esql.parser.EsqlSqlParser;
import org.n3r.esql.res.EsqlItem;
import org.n3r.esql.res.EsqlSub;
import org.n3r.esql.res.EsqlSub.EsqlType;
import org.n3r.esql.util.EsqlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.n3r.esql.util.EsqlUtils.*;

public class Esql implements Closeable {
    public static final String DEFAULT_CONN_NAME = "DEFAULT";
    private Logger logger = LoggerFactory.getLogger(Esql.class);
    private Object connectionNameOrConfigable;
    private EsqlItem esqlItem;
    private Object[] params;
    private String sqlClassPath;
    private EsqlPage page;
    private EsqlBatch batch;
    private Object[] dynamics;
    private EsqlTran externalTran;
    private EsqlTran internalTran;
    private Connection connection;
    private ArrayList<Object> executeResults;
    private DbType dbType;
    private EsqlRsRetriever rsRetriever = new EsqlRsRetriever();
    private int fetchSize;

    public static ThreadLocal<EsqlExecInfo> execContext = new ThreadLocal<EsqlExecInfo>() {
        @Override
        protected EsqlExecInfo initialValue() {
            return new EsqlExecInfo();
        }
    };

    public static EsqlExecInfo getExecContextInfo() {
        return execContext.get();
    }

    public static void setExecContextInfo(EsqlExecInfo execContextInfo) {
        execContext.set(execContextInfo);
    }

    public Connection getConnection() {
        return newTran(connectionNameOrConfigable).getConn();
    }


    private void createConn() {
        if (connection != null) return;

        connection = internalTran != null ? internalTran.getConn() : externalTran.getConn();
        dbType = DbTypeFactory.parseDbType(connection);
    }

    public List<Object> getResults() {
        return executeResults;
    }

    @SuppressWarnings("unchecked")
    public <T> T execute(String... directSqls) {
        checkPreconditions(directSqls);
        executeResults = new ArrayList<Object>();

        Object ret = null;
        try {
            if (batch == null) tranStart();
            createConn();

            for (EsqlSub subSql : createSqlSubs(directSqls)) {
                ret = execSub(ret, subSql);
                executeResults.add(ret);
            }

            if (batch == null) tranCommit();
        } catch (SQLException e) {
            logger.error("sql exception", e);
            if (batch != null) batch.cleanupBatch();
            throw new EsqlExecuteException("exec sql failed["
                    + Esql.getExecContextInfo().getSql() + "]" + e.getMessage());
        } finally {
            resetState();
            close();
        }

        return (T) ret;
    }

    private void resetState() {
        rsRetriever.resetMaxRows();
    }

    @Override
    public void close() {
        if (batch == null) tranClose();
        connection = null;
    }

    public ESelectStmt selectStmt() {
        tranStart();
        createConn();

        List<EsqlSub> sqlSubs = createSqlSubs();
        if (sqlSubs.size() != 1)
            throw new EsqlExecuteException("only one select sql supported in this method");

        EsqlSub sqlSub = sqlSubs.get(0);
        if (sqlSub.getSqlType() != EsqlType.SELECT)
            throw new EsqlExecuteException("only one select sql supported in this method");

        ESelectStmt selectStmt = new ESelectStmt();
        try {
            prepareStmt(selectStmt, sqlSub);
        } catch (SQLException e) {
            throw new EsqlExecuteException("prepareSelectStmt fail", e);
        }
        selectStmt.setRsRetriever(rsRetriever);
        selectStmt.setFetchSize(fetchSize);

        return selectStmt;
    }

    public EUpdateStmt updateStmt() {
        tranStart();
        createConn();

        List<EsqlSub> sqlSubs = createSqlSubs();
        if (sqlSubs.size() != 1)
            throw new EsqlExecuteException("only one update sql supported in this method");

        EsqlSub sqlSub = sqlSubs.get(0);
        if (!isUpdateStmt(sqlSub))
            throw new EsqlExecuteException("only one update/merge/delete/insert sql supported in this method");

        EUpdateStmt updateStmt = new EUpdateStmt();
        try {
            prepareStmt(updateStmt, sqlSub);
        } catch (SQLException e) {
            throw new EsqlExecuteException("prepareSelectStmt fail", e);
        }

        return updateStmt;
    }


    private void checkPreconditions(String... directSqls) {
        if (esqlItem != null || directSqls.length > 0) return;

        throw new EsqlExecuteException("No sqlid defined!");
    }

    private Object execSub(Object ret, EsqlSub subSql) throws SQLException {
        Esql.getExecContextInfo().setSql(subSql.getSql());

        try {
            return isDdl(subSql) ? execDdl(realSql(subSql)) : pageExecute(ret, subSql);
        } catch (EsqlExecuteException ex) {
            if (!subSql.getEsqlItem().isOnerrResume()) throw ex;
        }

        return 0;
    }

    private boolean execDdl(String sql) {
        Statement stmt = null;
        logger.debug("sql: {}", sql);
        try {
            stmt = connection.createStatement();
            return stmt.execute(sql);
        } catch (SQLException ex) {
            throw new EsqlExecuteException(ex);
        } finally {
            RClose.closeQuietly(stmt);
        }
    }

    private Object pageExecute(Object ret, EsqlSub subSql) throws SQLException {
        if (page == null || !subSql.isLastSelectSql())
            return execDml(ret, subSql);

        if (page.getTotalRows() == 0) page.setTotalRows(executeTotalRowsSql(ret, subSql));

        return executePageSql(ret, subSql);
    }

    private Object executePageSql(Object ret, EsqlSub subSql) throws SQLException {
        // oracle专用物理分页。
        EsqlSub pageSql = dbType.createPageSql(subSql, page);

        return execDml(ret, pageSql);
    }

    private int executeTotalRowsSql(Object ret, EsqlSub subSql) throws SQLException {
        EsqlSub totalSqlSub = subSql.clone();
        createTotalSql(totalSqlSub);

        Object totalRows = execDml(ret, totalSqlSub);
        if (totalRows instanceof Number) return ((Number) totalRows).intValue();

        throw new EsqlExecuteException("returned total rows object " + totalRows + " is not a number");
    }

    private void createTotalSql(EsqlSub subSql) {
        String sql = subSql.getSql().toUpperCase();
        int fromPos1 = sql.indexOf("FROM");

        int fromPos2 = sql.indexOf("DISTINCT");
        fromPos2 = fromPos2 < 0 ? sql.indexOf("FROM", fromPos1 + 4) : fromPos2;

        subSql.setSql(fromPos2 > 0 ? "SELECT COUNT(*) CNT__ FROM (" + sql + ")"
                : "SELECT COUNT(*) AS CNT " + sql.substring(fromPos1));
        subSql.setWillReturnOnlyOneRow(true);
    }

    private Object execDml(Object ret, EsqlSub subSql) throws SQLException {
        Object execRet = batch != null ? execDmlInBatch(ret, subSql) : execDmlNoBatch(ret, subSql);
        logger.debug("result: {}", execRet);

        return execRet;
    }

    private Object execDmlInBatch(Object ret, EsqlSub subSql) throws SQLException {
        return batch.processBatchUpdate(subSql);
    }

    private void prepareStmt(EStmt stmt, EsqlSub subSql) throws SQLException {
        PreparedStatement ps = null;
        try {
            ps = prepareSql(subSql, realSql(subSql));

            stmt.setPreparedStatment(ps);
            stmt.setSubSql(subSql);
            stmt.setLogger(logger);
            stmt.setParams(params);
            stmt.setEsqlTran(externalTran != null ? externalTran : internalTran);
        } catch (Exception ex) {
            RClose.closeQuietly(ps);
        }
    }

    private Object execDmlNoBatch(Object ret, EsqlSub subSql) throws SQLException {
        ResultSet rs = null;
        PreparedStatement ps = null;
        try {
            ps = prepareSql(subSql, realSql(subSql));
            new EsqlParamsBinder().bindParams(ps, subSql, params, logger);

            if (subSql.getSqlType() == EsqlType.SELECT) {
                rs = ps.executeQuery();
                if (fetchSize > 0) rs.setFetchSize(fetchSize);

                return rsRetriever.convert(rs, subSql);
            }

            if (EsqlUtils.isProcedure(subSql.getSqlType())) {
                CallableStatement cs = (CallableStatement) ps;
                return execAndRetrieveProcedureRet(subSql, cs);
            }

            return ps.executeUpdate();

        } finally {
            RClose.closeQuietly(rs, ps);
        }
    }

    private Object execAndRetrieveProcedureRet(EsqlSub subSql, CallableStatement cs) throws SQLException {
        cs.execute();

        if (subSql.getOutCount() == 0) return null;

        if (subSql.getOutCount() == 1)
            for (int i = 0, ii = subSql.getPlaceHolders().length; i < ii; ++i)
                if (subSql.getPlaceHolders()[i].getInOut() != InOut.IN) return cs.getObject(i + 1);

        switch (subSql.getPlaceHolderOutType()) {
            case AUTO_SEQ:
                return retrieveAutoSeqOuts(subSql, cs);
            case VAR_NAME:
                return rsRetriever.getCallableReturnMapper().mapResult(subSql, cs);
            default:
                break;
        }

        return null;
    }

    private Object retrieveAutoSeqOuts(EsqlSub subSql, CallableStatement cs) throws SQLException {
        List<Object> objects = Lists.newArrayList();
        for (int i = 0, ii = subSql.getPlaceHolders().length; i < ii; ++i)
            if (subSql.getPlaceHolders()[i].getInOut() != InOut.IN) objects.add(cs.getObject(i + 1));

        return objects;
    }

    public PreparedStatement prepareSql(EsqlSub subSql, String realSql) throws SQLException {
        logger.debug("sql: {} ", realSql);
        return EsqlUtils.isProcedure(subSql.getSqlType())
                ? connection.prepareCall(realSql) : connection.prepareStatement(realSql);
    }

    public String realSql(EsqlSub subSql) {
        String sql = new EsqlDynamicReplacer().repaceDynamics(subSql, dynamics);

        return autoTrimLastUnusedPart(sql);
    }

    public Esql returnType(Class<?> returnType) {
        rsRetriever.setReturnType(returnType);
        return this;
    }

    public Esql returnType(EsqlRowMapper esqlRowMapper) {
        rsRetriever.setEsqlRowMapper(esqlRowMapper);
        return this;
    }

    public Esql(String connectionName) {
        this.connectionNameOrConfigable = connectionName;
    }

    public Esql(Configable configable) {
        this.connectionNameOrConfigable = configable;
    }

    public Esql() {
        this(DEFAULT_CONN_NAME);
    }

    protected void initSqlId(String sqlId, String sqlClassPath) {
        this.sqlClassPath = isEmpty(sqlClassPath) ? getSqlClassPath(4) : sqlClassPath;

        esqlItem = EsqlSqlParser.getEsqlItem(this.sqlClassPath, sqlId);

        if (esqlItem == null) throwError(sqlId);

        rsRetriever.setEsqlItem(esqlItem);
    }

    private void throwError(String sqlId) {
        StringBuilder errMsg = new StringBuilder(sqlId + " not found ");
        boolean useSqlFile = isNotEmpty(sqlClassPath);
        if (useSqlFile) errMsg.append("in sql file ").append(sqlClassPath);

        throw new EsqlIdNotFoundException(errMsg.toString());
    }

    protected List<EsqlSub> createSqlSubs(String... directSqls) {
        return directSqls.length == 0
                ? esqlItem.createSqlSubs(compositeParams(params))
                : createSqlSubsByDirectSqls(directSqls);
    }

    private List<EsqlSub> createSqlSubsByDirectSqls(String[] sqls) {
        List<EsqlSub> sqlSubs = Lists.newArrayList();
        EsqlSub lastSelectSql = null;
        for (String sql : sqls) {
            EsqlSub subSql = new EsqlParamsParser().parseRawSql(sql, null);
            sqlSubs.add(subSql);

            if (subSql.getSqlType() == EsqlType.SELECT) lastSelectSql = subSql;
        }

        if (lastSelectSql != null) lastSelectSql.setLastSelectSql(true);

        return sqlSubs;
    }

    public Esql useSqlFile(Class<?> sqlBoundClass) {
        sqlClassPath = "/" + sqlBoundClass.getName().replace('.', '/') + ".esql";
        return this;
    }

    public Esql useSqlFile(String sqlClassPath) {
        this.sqlClassPath = sqlClassPath;
        return this;
    }

    public Esql id(String sqlId) {
        initSqlId(sqlId, sqlClassPath);
        return this;
    }

    public Esql merge(String sqlId) {
        initSqlId(sqlId, sqlClassPath);
        return this;
    }

    public Esql update(String sqlId) {
        initSqlId(sqlId, sqlClassPath);
        return this;
    }

    public Esql insert(String sqlId) {
        initSqlId(sqlId, sqlClassPath);
        return this;
    }

    public Esql delete(String sqlId) {
        initSqlId(sqlId, sqlClassPath);
        return this;
    }

    public Esql select(String sqlId) {
        initSqlId(sqlId, sqlClassPath);
        return this;
    }

    public Esql selectFirst(String sqlId) {
        initSqlId(sqlId, sqlClassPath);
        limit(1);
        return this;
    }

    public Esql procedure(String sqlId) {
        initSqlId(sqlId, sqlClassPath);
        return this;
    }

    public String getSqlId() {
        return esqlItem.getSqlId();
    }

    private void tranStart() {
        if (externalTran != null) return;
        if (internalTran != null) return;

        internalTran = newTran(connectionNameOrConfigable);
        internalTran.start();
    }

    private void tranCommit() {
        if (externalTran != null) return;

        internalTran.commit();
    }

    private void tranClose() {
        if (internalTran != null) RClose.closeQuietly(internalTran);

        internalTran = null;
    }

    /**
     * 创建一个新的事务对象，并且在当前Esql中委托该对象控制事务。
     *
     * @return
     */
    public EsqlTran newTran() {
        EsqlTran tran = newTran(connectionNameOrConfigable);
        connection = tran.getConn();
        useTran(tran);

        return tran;
    }

    /**
     * 委托外部事务对象来控制事务。
     *
     * @param tran
     * @return
     */
    public Esql useTran(EsqlTran tran) {
        externalTran = tran;
        return this;
    }

    /**
     * 创建一个事务对象。
     *
     * @param connectionNameOrConfigable
     * @return
     */
    public static EsqlTran newTran(Object connectionNameOrConfigable) {
        return EsqlConfigManager.getConfig(connectionNameOrConfigable).getTran();
    }

    public String getConnectionName() {
        return "" + connectionNameOrConfigable;
    }

    public String getSqlPath() {
        return sqlClassPath;
    }

    public Esql params(Object... params) {
        this.params = params;
        return this;
    }

    public Esql limit(EsqlPage page) {
        this.page = page;

        return this;
    }

    public Esql startBatch(int maxBatches) {
        batch.startBatch(maxBatches);
        return this;
    }

    public Esql startBatch() {
        batch = new EsqlBatch(this);
        batch.startBatch();

        tranStart();
        return this;
    }

    public int executeBatch() {
        int totalRows = 0;
        try {
            totalRows = batch.processBatchExecution();
            tranCommit();
        } catch (SQLException e) {
            throw new EsqlExecuteException("executeBatch failed:" + e.getMessage());
        } finally {
            tranClose();
        }

        batch = null;
        return totalRows;
    }

    public Esql dynamics(Object... dynamics) {
        this.dynamics = dynamics;
        return this;
    }

    public Esql limit(int maxRows) {
        rsRetriever.setMaxRows(maxRows);
        return this;
    }

    public Object[] getParams() {
        return params;
    }

    public Logger getLogger() {
        return logger;
    }

    public Esql returnType(String returnTypeName) {
        rsRetriever.setReturnTypeName(returnTypeName);
        return this;
    }

    public Esql setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
        return this;
    }
}
