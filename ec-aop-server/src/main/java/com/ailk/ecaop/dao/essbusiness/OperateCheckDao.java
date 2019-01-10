package com.ailk.ecaop.dao.essbusiness;

import java.util.List;
import java.util.Map;

import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.dao.base.DaoEngine;
import org.n3r.esql.Esql;

import com.ailk.ecaop.common.utils.IsEmptyUtils;

public class OperateCheckDao {

    Esql essDao = DaoEngine.get3GeDao("/com/ailk/ecaop/sql/3gess/OperateCheck.esql");

    public Map qryStaffInfo(Map inMap) {
        List<Map> staffInfo = essDao.id("qryStaffInfo").params(inMap).execute();
        if (IsEmptyUtils.isEmpty(staffInfo)) {
            throw new EcAopServerBizException("9999", "没有查询到对应的员工信息");
        }
        Map staffMap = staffInfo.get(0);
        if (!staffMap.get("EPARCHY_CODE").equals(inMap.get("city"))) {
            throw new EcAopServerBizException("9999", "请求报文中的地市编码和员工所属地市编码不一致");
        }
        return staffMap;
    }

    public Map qryDepartInfo(Map inMap) {
        List<Map> departInfo = essDao.id("selDepartInfo").params(inMap).execute();
        if (IsEmptyUtils.isEmpty(departInfo)) {
            throw new EcAopServerBizException("9999", "该员工没有对应的部门信息");
        }
        Map departMap = departInfo.get(0);
        if (!"0".equals(departMap.get("VALIDFLAG"))) {
            throw new EcAopServerBizException("9999", "员工所属部门已失效");
        }
        return departInfo.get(0);
    }

    public Map selChnInfoByChnId(Map inMap) {
        List<Map> channelInfo = essDao.id("selChnInfoByChnId").params(inMap).execute();
        if (IsEmptyUtils.isEmpty(channelInfo)) {
            throw new EcAopServerBizException("9999", "该员工没有对应的渠道信息");
        }
        Map channelMap = channelInfo.get(0);
        if (!channelMap.get("CHANNEL_CODE").equals(inMap.get("channelId"))) {
            throw new EcAopServerBizException("9999", "请求报文中的渠道编码和员工所属渠道编码不一致");
        }
        if (!channelMap.get("CHANNEL_TYPE").equals(inMap.get("channelType"))) {
            throw new EcAopServerBizException("9999", "请求报文中的渠道类型和员工所属渠道类型不一致");
        }
        return channelMap;
    }
}
