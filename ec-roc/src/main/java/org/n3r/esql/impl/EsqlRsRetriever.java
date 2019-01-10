package org.n3r.esql.impl;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.n3r.core.joor.Reflect;
import org.n3r.core.lang.RByte;
import org.n3r.core.util.AfterPropertiesSet;
import org.n3r.esql.map.EsqlBeanRowMapper;
import org.n3r.esql.map.EsqlCallableResultBeanMapper;
import org.n3r.esql.map.EsqlCallableReturnMapMapper;
import org.n3r.esql.map.EsqlCallableReturnMapper;
import org.n3r.esql.map.EsqlMapMapper;
import org.n3r.esql.map.EsqlRowMapper;
import org.n3r.esql.map.SingleValueMapper;
import org.n3r.esql.res.EsqlItem;
import org.n3r.esql.res.EsqlSub;
import org.n3r.esql.util.EsqlUtils;

public class EsqlRsRetriever {
    private EsqlItem esqlItem;
    private static int DEFAULT_MAXROWS = 100000;
    private int maxRows = DEFAULT_MAXROWS;
    private EsqlRowMapper esqlRowMapper;
    private String returnTypeName;
    private Class<?> returnType;

    public Object convert(ResultSet rs, EsqlSub subSql) throws SQLException {
        return maxRows <= 1 || subSql.isWillReturnOnlyOneRow() ? firstRow(rs) : selectList(rs);
    }

    private Object firstRow(ResultSet rs) throws SQLException {
        if (!rs.next()) return null;

        if(rs.getMetaData().getColumnCount() == 1)
            return convertSingleValue(EsqlUtils.getResultSetValue(rs, 1));

        return rowBeanCreate(rs, 1);
    }

    public Object selectRow(ResultSet rs, int rownum) throws SQLException {
        return rownum <= maxRows && rs.next() ? rowBeanCreate(rs, rownum) : null;
    }

    private Object selectList(ResultSet rs) throws SQLException {
        List<Object> result = new ArrayList<Object>();

        for (int rownum = 1; rs.next() && rownum <= maxRows; ++rownum) {
            Object rowObject = rowBeanCreate(rs, rownum);
            if (rowObject != null) result.add(rowObject);
        }

        return result;
    }

    private Object rowBeanCreate(ResultSet rs, int rowNum) throws SQLException {
        Object rowBean = getRowMapper(rs.getMetaData()).mapRow(rs, rowNum);
        if (rowBean instanceof AfterPropertiesSet)
            ((AfterPropertiesSet) rowBean).afterPropertiesSet();

        return rowBean;
    }

    private EsqlRowMapper getRowMapper(ResultSetMetaData metaData) throws SQLException {
        if (esqlRowMapper != null) return esqlRowMapper;

        if (returnType == null && esqlItem != null) returnType = esqlItem.getReturnType();

        if (returnType != null && EsqlRowMapper.class.isAssignableFrom(returnType))
            return Reflect.on(returnType).create().get();

        if (returnType != null) return new EsqlBeanRowMapper(returnType);

        return metaData.getColumnCount() > 1 ? new EsqlMapMapper() : new SingleValueMapper();
    }

    public EsqlCallableReturnMapper getCallableReturnMapper() {
        if (returnType == null && esqlItem != null) returnType = esqlItem.getReturnType();

        if (returnType != null && EsqlCallableReturnMapper.class.isAssignableFrom(returnType))
            return Reflect.on(returnType).create().get();

        if (returnType != null) return new EsqlCallableResultBeanMapper(returnType);

        return new EsqlCallableReturnMapMapper();
    }

    private Object convertSingleValue(Object resultSetValue) {
        if (returnType == null && esqlItem != null) returnType = esqlItem.getReturnType();

        String returnTypeName = this.returnTypeName;
        if (returnTypeName == null)
              returnTypeName = esqlItem == null ? null : esqlItem.getSqlOptions().get("returnType");
        if (returnType == null && returnTypeName == null) return resultSetValue;

        if ("string".equals(returnTypeName)) {
            if (resultSetValue instanceof byte[])  {
                return RByte.toStr((byte[])resultSetValue);
            }
            return String.valueOf(resultSetValue);
        }
        if ("int".equals(returnTypeName)) {
            if (resultSetValue instanceof Number) return ((Number)resultSetValue).intValue();
        }
        if ("long".equals(returnTypeName)) {
            if (resultSetValue instanceof Number) return ((Number)resultSetValue).longValue();
        }

        return resultSetValue;
    }

    public void setEsqlItem(EsqlItem esqlItem) {
        this.esqlItem = esqlItem;
    }

    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }



    public void setEsqlRowMapper(EsqlRowMapper esqlRowMapper) {
       this.esqlRowMapper = esqlRowMapper;
    }

    public void setReturnTypeName(String returnTypeName) {
       this.returnTypeName = returnTypeName;
    }

    public void setReturnType(Class<?> returnType) {
       this.returnType = returnType;
    }

    public void resetMaxRows() {
        this.maxRows = DEFAULT_MAXROWS;
    }
}
