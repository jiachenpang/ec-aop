package com.ailk.ecaop.biz.sub.mixprodchg.entry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.MapUtils;

/**
 * 融合产品变更预提交接口_固话用户实体
 * @author wangmc
 * @date 2018-08-06
 */
public class MixProductChgFixedPhoneUser extends MixProductChgSuperUser {

    public MixProductChgFixedPhoneUser(Exchange exchange, Map msg, ParametersMappingProcessor[] pmp,
            String serialNumber, String mixTradeId, String userType) {
        super(exchange, msg, pmp, serialNumber, mixTradeId, userType);
    }

    @Override
    public Map flowControl() throws Exception {
        // System.out.println("this is fixPhone user");
        ELKAopLogger.logStr("this is fixPhone user");

        // 拼装产品节点
        preProductInfo();

        // 拼装ext节点-tradeRelation
        preExt();

        // 拼装base
        preBase();

        // 拼装其他产品节点
        preTradeOther();

        return null;
    }

    private void preTradeOther() {

        // tradeRelation
        preTradeRelationData();

        // tradeItem
        preTradeItemData();

        // tradeSubItem
        preTradeSubItem();

    }

    private void preTradeSubItem() {
        String userId = (String) msg.get("phoneUserId");
        Map tradeSubItem = new HashMap();
        List<Map> tradeSubItemList = new ArrayList<Map>();

        tradeSubItemList.add(lan.createTradeSubItemJ("ORGI_COMP_SN_A", msg.get("serialNumber"), userId));
        tradeSubItemList.add(lan.createTradeSubItemJ("ORGI_COMP_ID_A", msg.get("phoneUserId"), userId));

        tradeSubItem.put("item", tradeSubItemList);
        ext.put("tradeSubItem", tradeSubItem);
    }

    private void preTradeItemData() {
        Map tradeItem = new HashMap();
        List<Map> tradeItemList = new ArrayList<Map>();

        tradeItemList.add(lan.createAttrInfoNoTime("OLD_GROUP_ID", msg.get("mixUserId")));
        tradeItemList.add(lan.createAttrInfoNoTime("DEVELOP_DEPART_ID", msg.get("channelId")));
        tradeItemList.add(lan.createAttrInfoNoTime("COMP_DEAL_STATE", "1"));
        tradeItemList.add(lan.createAttrInfoNoTime("DEVELOP_STAFF_ID", msg.get("recomPersonId")));
        tradeItemList.add(lan.createAttrInfoNoTime("ALONE_TCS_COMP_INDEX", "1"));
        tradeItem.put("item", tradeItemList);
        ext.put("tradeItem", tradeItem);

    }

    private void preTradeRelationData() {
        Map userInfo = (Map) threePartRet.get("userInfo");
        List<Map> oldProducts = (List<Map>) threePartRet.get("productInfo");
        // 拼装tradeRelation-8800退订，H007订购
        Map tradeRelation = new HashMap();
        List<Map> itemList = new ArrayList<Map>();

        String mixUserId = (String) msg.get("mixUserId");
        String userId = (String) userInfo.get("userId");
        properties.put("userId", userId);// 拼装tradeSubItem节点要用
        List<Map> viceNumberInfo = (List<Map>) msg.get("viceNumberInfo");
        Map item = new HashMap();
        // 退订
        item.put("relationTypeCode", "8800");
        item.put("startDate", msg.get("oldMainProductActiveTime"));
        item.put("endDate", GetDateUtils.getMonthLastDay());
        item.put("modifyTag", "1");
        itemList.add(item);
        // 订购
        item.put("relationTypeCode", "H007");
        item.put("modifyTag", "0");
        item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
        item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        itemList.add(item);

        dealRelaData(itemList, userId, mixUserId);

    }

    /**
     * 封装关系节点
     * @param itemList
     * @param userId 移网用户ID
     * @param mixUserId 虚拟用户ID
     */
    private void dealRelaData(List<Map> itemList, String userId, String mixUserId) {
        List<Map> relationList = new ArrayList<Map>();
        for (Map map : itemList) {
            Map item = new HashMap();
            item.put("modifyTag", map.get("modifyTag"));
            item.put("relationTypeCode", map.get("relationTypeCode"));
            item.put("startDate", map.get("startDate"));
            item.put("endDate", map.get("endDate"));
            item.put("serialNumberA", MapUtils.getDefault(map, "serialNumberA", (String) msg.get("mixSerialNumber")));
            item.put("serialNumberB", MapUtils.getDefault(map, "serialNumberB", serialNumber));
            item.put("idA", MapUtils.getDefault(map, "idA", mixUserId));
            item.put("idB", MapUtils.getDefault(map, "idB", userId));
            item.put("xDatatype", "NULL");
            item.put("relationAttr", "3");
            item.put("orderno", "-1");
            item.put("shortCode", "");
            item.put("roleCodeA", "0");
            item.put("roleCodeB", "3");
            item.put("remark", "");
            relationList.add(item);
        }
        ext.put("tradeRelation", MapUtils.asMap("item", relationList));
    }

}
