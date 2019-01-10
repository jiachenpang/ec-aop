package org.n3r.esql.param;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;

import ognl.Ognl;
import ognl.OgnlException;

import org.apache.commons.lang3.StringUtils;
import org.n3r.core.lang.RByte;
import org.n3r.core.lang.RDate;
import org.n3r.core.lang.RStr;
import org.n3r.esql.ex.EsqlExecuteException;
import org.n3r.esql.param.EsqlParamPlaceholder.InOut;
import org.n3r.esql.res.EsqlSub;
import org.n3r.esql.util.EsqlUtils;
import org.slf4j.Logger;

public class EsqlParamsBinder {
    private EsqlSub subSql;
    private Object[] params;
    private StringBuilder boundParams;
    private PreparedStatement ps;

    private static enum ParamExtra {
        Extra, Normal
    }

    public void bindParams(PreparedStatement ps, EsqlSub subSql, Object[] params, Logger logger) {
        this.subSql = subSql;
        this.params = params;
        boundParams = new StringBuilder();
        this.ps = ps;

        switch (subSql.getPlaceHolderType()) {
        case AUTO_SEQ:
            for (int i = 0; i < subSql.getPlaceholderNum(); ++i)
                setParam(subSql.getPlaceHolder(i), i, getParamByIndex(i), ParamExtra.Normal);
            break;
        case MANU_SEQ:
            for (int i = 0; i < subSql.getPlaceholderNum(); ++i)
                setParam(subSql.getPlaceHolder(i), i, findParamBySeq(i + 1), ParamExtra.Normal);
            break;
        case VAR_NAME:
            for (int i = 0; i < subSql.getPlaceholderNum(); ++i)
                setParam(subSql.getPlaceHolder(i), i, findParamByName(subSql, i), ParamExtra.Normal);
            break;
        default:
            break;
        }

        bindExtraParams();

        if (boundParams.length() > 0) logger.debug("param: {}", boundParams);
    }

    private void bindExtraParams() {
        Object[] extraBindParams = subSql.getExtraBindParams();
        if (extraBindParams == null) return;

        for (int i = subSql.getPlaceholderNum(); i < subSql.getPlaceholderNum() + extraBindParams.length; ++i)
            setParam(subSql.getPlaceHolder(i), i, extraBindParams[i - subSql.getPlaceholderNum()], ParamExtra.Extra);
    }

    private void setParam(EsqlParamPlaceholder placeHolder, int index, Object value, ParamExtra extra) {
        try {
            switch (extra) {
            case Extra:
                setParamExtra(placeHolder, index, value);
                break;
            default:
                setParamEx(placeHolder, index, value);
                break;
            }
        } catch (SQLException e) {
            throw new EsqlExecuteException("set parameters fail", e);
        }
    }

    private void setParamExtra(EsqlParamPlaceholder placeHolder, int index, Object value) throws SQLException {
        if (value instanceof Date) {
            Timestamp date = new Timestamp(((Date) value).getTime());
            ps.setTimestamp(index + 1, date);
            boundParams.append('[').append(RDate.toDateTimeStr(date)).append(']');
        }
        else {
            Object paramValue = value;
            if (placeHolder != null && placeHolder.getOption().contains("LOB") && value instanceof String)
                paramValue = RByte.toBytes((String)value);

            ps.setObject(index + 1, paramValue);
            boundParams.append('[').append(value).append(']');
        }
    }

    private void setParamEx(EsqlParamPlaceholder placeHolder, int index, Object value) throws SQLException {
        if (regiesterOut(index)) return;

        setParamExtra(placeHolder, index, value);
    }

    private boolean regiesterOut(int index) throws SQLException {
        InOut inOut = subSql.getPlaceHolders()[index].getInOut();
        if (EsqlUtils.isProcedure(subSql.getSqlType()) && inOut != InOut.IN)
            ((CallableStatement) ps).registerOutParameter(index + 1, Types.VARCHAR);

        return inOut == InOut.OUT;
    }

    private Object findParamByName(EsqlSub subSql, int index) {
        Object bean = EsqlUtils.compositeParams(params);

        String varName = subSql.getPlaceHolders()[index].getPlaceholder();
        // Object property = RBean.getPropertyQuietly(bean, varName);
        Object property = getPropertyValue(bean, varName);

        if (property != null) return property;

        String propertyName = RStr.convertUnderscoreNameToPropertyName(varName);
        if (!StringUtils.equals(propertyName, varName))
            property = getPropertyValue(bean, propertyName); // RBean.getPropertyQuietly(bean, propertyName);

        return property;
    }

    private Object getPropertyValue(Object bean, String varName) {
        try {
            return Ognl.getValue(varName, bean);
        } catch (OgnlException e) {
            throw new RuntimeException(e);
        }
    }

    private Object getParamByIndex(int index) {
        EsqlParamPlaceholder[] placeHolders = subSql.getPlaceHolders();
        if (index < placeHolders.length && EsqlUtils.isProcedure(subSql.getSqlType())
                && placeHolders[index].getInOut() == InOut.OUT) return null;

        if (params != null && index < params.length)
            return params[index];

        throw new EsqlExecuteException("[" + subSql.getSqlId() + "]执行过程中缺少参数");
    }

    private Object findParamBySeq(int index) {
        return getParamByIndex(subSql.getPlaceHolders()[index - 1].getSeq() - 1);
    }
}
