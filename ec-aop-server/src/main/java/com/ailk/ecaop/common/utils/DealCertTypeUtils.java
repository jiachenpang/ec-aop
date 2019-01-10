package com.ailk.ecaop.common.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RHQ2018082800066-增加“港澳台居民居住证”证件类型的需求
 * @author wangmc
 * @date 2018-09-14
 */
public class DealCertTypeUtils {

    /**
     * 港澳台居民居住证时,在trade_sub_item下发四个节点
     * @param msg-需要含有custId
     * @param custInfo-包含证件类型、证件号码以及证件信息
     * @param ext-含有tradeSubItem节点
     */
    @SuppressWarnings("cast")
    public static void dealCertType(Map msg, Map custInfo, Map ext) {
        System.out.println(msg.get("apptx") + ",dealCertType:msg:" + msg + ",custInfo:" + custInfo + ",ext:" + ext);
        if (IsEmptyUtils.isEmpty(msg) || IsEmptyUtils.isEmpty(custInfo) || IsEmptyUtils.isEmpty(ext)
                || !"35".equals(custInfo.get("certType"))) {
            return;
        }
        Map tradeSubItem = (Map) ext.get("tradeSubItem");
        if (IsEmptyUtils.isEmpty(tradeSubItem)) {
            return;
        }
        List<Map> subItemList = (List<Map>) tradeSubItem.get("item");
        if (IsEmptyUtils.isEmpty(subItemList)) {
            subItemList = new ArrayList<Map>();
        }
        // 参数分别为:证件号码;签发次数;签发机关;证件起始有效期
        String[] getKeys = new String[] { "psptNumber", "issuesNumber", "psptAuthority", "psptStartDate" };
        String[] putKeys = new String[] { "HKongMacaoTaiwan", "IssuesNumber", "PSPTLSSUING_AUTHORITY",
                "PSPT_START_DATE" };
        LanUtils lan = new LanUtils();
        for (int i = 0; i < getKeys.length; i++) {
            if (!IsEmptyUtils.isEmpty(custInfo.get(getKeys[i]))) {
                subItemList.add(lan.createTradeSubItem4D(putKeys[i], custInfo.get(getKeys[i]),
                        (String) msg.get("custId")));
            }
        }
    }
}
