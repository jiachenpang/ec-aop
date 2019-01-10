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
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

/**
 * 融合产品变更预提交接口_移网用户实体
 * @author wangmc
 * @date 2018-08-06
 */
public class MixProductChgPhoneUser extends MixProductChgSuperUser {

    public MixProductChgPhoneUser(Exchange exchange, Map msg, ParametersMappingProcessor[] pmp, String serialNumber,
            String mixTradeId, String userType) {
        super(exchange, msg, pmp, serialNumber, mixTradeId, userType);
    }

    @Override
    public Map flowControl() throws Exception {
        //System.out.println("this is phone user");
        ELKAopLogger.logStr("this is phone user");

        // 拼装产品节点
        preProductInfo();

        // 拼装ext节点-tradeRelation
        preExt();

        // 拼装base
        preBase();

        // 拼装其他产品节点
        preTradeOther();

        // 调预提交
        callPreSub();

        return null;
    }

    /**
     * 拼装tradeRelation、tradeItem、tradeSubItem节点
     */
    private void preTradeOther() {

        // tradeRelation
        preTradeRelationData();

        // tradeItem
        preTradeItemData();

        // tradeSubItem
        preTradeSubItem();

    }

    private void preTradeRelationData() {
        Map userInfo = (Map) threePartRet.get("userInfo");
        List<Map> oldProducts = (List<Map>) threePartRet.get("productInfo");
        String oldMainProductActiveTime = GetDateUtils.getDate();
        // 获取主产品的生效时间
        for (Map oldProduct : oldProducts) {
            String productMode = (String) oldProduct.get("oldProduct");
            if ("00".equals(productMode)) {
                oldMainProductActiveTime = (String) oldProduct.get("productActiveTime");
            }
        }
        // 拼装tradeRelation主副卡节点-8800退订，H007订购，主副卡ZF（副卡信息只在关系节点绑定下，无其他操作）
        Map tradeRelation = new HashMap();
        List<Map> itemList = new ArrayList<Map>();

        String mixUserId = (String) msg.get("mixUserId");
        String userId = (String) userInfo.get("userId");
        msg.put("phoneUserId", userId);
        List<Map> viceNumberInfo = (List<Map>) msg.get("viceNumberInfo");
        Map item = new HashMap();
        // 退订
        item.put("relationTypeCode", "8800");
        item.put("startDate", oldMainProductActiveTime);
        msg.put("oldMainProductActiveTime", oldMainProductActiveTime);
        item.put("endDate", GetDateUtils.getMonthLastDay());
        item.put("modifyTag", "1");
        itemList.add(item);
        // 订购
        item.put("relationTypeCode", "H007");
        item.put("modifyTag", "0");
        item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
        item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        itemList.add(item);
        //主卡关系绑定，这个是必需要下发的
        item.put("relationTypeCode", "H007");
        item.put("modifyTag", "0");
        item.put("idA", userId);
        item.put("idB", userId);
        item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
        item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        item.put("serialNumberA", serialNumber);
        item.put("serialNumberB", serialNumber);
        itemList.add(item);
        // 副卡信息，传了副卡信息就下发，关系为ZF类型
        for (int i = 1; i < viceNumberInfo.size(); i++) {
            Map viceNumberMap = (Map) viceNumberInfo.get(i).get("viceNumberInfo");
            String viceNumber = (String) viceNumberMap.get("viceNumber");
            Map item1 = new HashMap();
            item1.put("relationTypeCode", "ZF");
            item1.put("modifyTag", "0");
            item1.put("idA", userId);
            item1.put("idB", userId);
            item1.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
            item1.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            item1.put("serialNumberA", serialNumber);
            item1.put("serialNumberB", viceNumber);
            itemList.add(item1);
        }
        dealRelaData(itemList, userId, mixUserId);
    }

    /**
     * 
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
            item.put("roleCodeB", "1"); // 移网报文模板下发1
            item.put("remark", "");
            relationList.add(item);
        }
        ext.put("tradeRelation", MapUtils.asMap("item", relationList));
    }

    private void preTradeItemData() {
        Map tradeItem = new HashMap();
        List<Map> tradeItemList = new ArrayList<Map>();

        tradeItemList.add(new LanUtils().createAttrInfoNoTime("OLD_GROUP_ID", msg.get("mixUserId")));
        tradeItemList.add(new LanUtils().createAttrInfoNoTime("DEVELOP_DEPART_ID", msg.get("channelId")));
        tradeItemList.add(new LanUtils().createAttrInfoNoTime("MAIN_CARD_TAG", "0"));
        tradeItemList.add(new LanUtils().createAttrInfoNoTime("COMP_DEAL_STATE", "1"));
        tradeItemList.add(new LanUtils().createAttrInfoNoTime("DEVELOP_STAFF_ID", msg.get("recomPersonId")));
        tradeItemList.add(new LanUtils().createAttrInfoNoTime("ALONE_TCS_COMP_INDEX", "3"));
        tradeItem.put("item", tradeItemList);
        ext.put("tradeItem", tradeItem);

    }

    private void preTradeSubItem() {
        String userId = (String) msg.get("phoneUserId");
        Map tradeSubItem = new HashMap();
        List<Map> tradeSubItemList = new ArrayList<Map>();

        tradeSubItemList.add(lan.createTradeSubItemJ("MAIN_CARD_TAG", "0", userId));
        tradeSubItemList.add(lan.createTradeSubItemJ("ORGI_COMP_SN_A", serialNumber, userId));
        tradeSubItemList.add(lan.createTradeSubItemJ("ORGI_COMP_ID_A", userId, userId));

        tradeSubItem.put("item", tradeSubItemList);
        ext.put("tradeSubItem", tradeSubItem);
    }
}
