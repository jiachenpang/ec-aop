package com.ailk.ecaop.dao.essbusiness;

/**
 * Created by Liu JiaDi on 2016/8/17.
 */

import java.util.List;
import java.util.Map;

import org.n3r.ecaop.dao.base.DaoEngine;
import org.n3r.esql.Esql;

/**
 * 该类用于存储对3GE省份数据的操作
 * @auther Zhang Meng
 * @create 2016_08_17_11:23
 */
public class EssProvinceDao {

    public String selSubscribeId(Map inMap) {
        List retList = init(inMap).id("selectSubscribeId").params(inMap).execute();
        return retList.get(0).toString();
    }

    private Esql init(Map inMap) {
        Object zoneCode = new EssBusinessDao().selZoneCodeByProvince(inMap);
        return DaoEngine.get3GePDao("/com/ailk/ecaop/sql/3gess/EssProvinceInfo.esql", zoneCode);
    }

    /**
     * 查询号码预占表中的号码信息
     * @param inMap
     * @return
     */
    public List selOccupyInfo(Map inMap) {
        List retList = init(inMap).id("selNumInfoFromOccupy").params(inMap).execute();
        return retList;
    }

    /**
     * 查询号码空闲表中的号码信息
     * @param inMap
     * @return
     */
    public List selIDLEInfo(Map inMap) {
        List retList = init(inMap).id("selNumInfoFromIDLE").params(inMap).execute();
        return retList;
    }

    /**
     * 获取开户的订单信息
     * @param inMap
     * @return
     */
    public List<Map> qrySubscribeInfo(Map inMap) {
        return init(inMap).id("qrySubscribeInfo").params(inMap).execute();
    }

    /**
     * 插入开户的订单信息
     * @param inMap
     * @return
     */
    public void intoSubscribeInfo(Map inMap) {
        init(inMap).id("IntoSubscribeInfo").params(inMap).execute();
    }

    public void intoSubscribeBlob(Map inMap) {
        init(inMap).id("updateE2EStepInfo").params(inMap).execute();
    }

    public void intoSubscribeTable(Map inMap) {
        init(inMap).id("insertSubscribe").params(inMap).execute();
    }

}
