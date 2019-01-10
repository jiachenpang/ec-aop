package com.ailk.ecaop.dao.essbusiness;

import java.util.List;
import java.util.Map;

import org.n3r.ecaop.dao.base.DaoEngine;
import org.n3r.esql.Esql;

/**
 * 该类用于存储对北六ESS省份数据的操作
 * 
 * @auther Zhang Meng
 * @create 2016_08_17_11:23
 */
public class N6EssDao {

    public String selSubscribeId(Map inMap) {
        List retList = init(inMap).id("selectSubscribeId").params(inMap).execute();
        return retList.get(0).toString();
    }

    private Esql init(Map inMap) {
        return DaoEngine.getNESSDao("/com/ailk/ecaop/sql/3gess/EssProvinceInfo.esql", inMap.get("province"));
    }
}
