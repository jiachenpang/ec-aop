package com.ailk.ecaop.common.utils;

import java.util.Map;

import com.ailk.ecaop.dao.essbusiness.OperateCheckDao;

public class OperateBaseCheckUtils {

    private static OperateCheckDao dao;
    static {
        dao = new OperateCheckDao();
    };

    public Map baseCheck(Map inMap) {
        Map staffInfo = dao.qryStaffInfo(inMap);
        inMap.putAll(staffInfo);
        Map departInfo = dao.qryDepartInfo(inMap);
        inMap.putAll(departInfo);
        dao.selChnInfoByChnId(inMap);
        return inMap;

    }
}
