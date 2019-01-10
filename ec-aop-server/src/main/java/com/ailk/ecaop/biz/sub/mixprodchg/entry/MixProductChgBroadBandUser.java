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
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

/**
 * 融合产品变更预提交接口_宽带用户实体
 * @author wangmc
 * @date 2018-08-06
 */
public class MixProductChgBroadBandUser extends MixProductChgSuperUser {

    public MixProductChgBroadBandUser(Exchange exchange, Map msg, ParametersMappingProcessor[] pmp,
            String serialNumber, String mixTradeId, String userType) {
        super(exchange, msg, pmp, serialNumber, mixTradeId, userType);
        // TODO Auto-generated constructor stub
    }

    @Override
    public Map flowControl() throws Exception {
        //System.out.println("this is broadBand user");
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

    private void preTradeOther() {
        // tradeRelation
        preTradeRelationData();

        // tradeItem
        preTradeItemData();

        // tradeSubItem
        preTradeSubItem();

    }

    private void preTradeSubItem() {

        Map tradeSubItem = new HashMap();
        List<Map> tradeSubItemList = new ArrayList<Map>();
        String userId = (String) properties.get("userId");
        String speed = "";
        for (Map product : productInfo) {
            if (!IsEmptyUtils.isEmpty(String.valueOf(product.get("speedLevel")))) {
                speed = String.valueOf(product.get("speedLevel"));
            }
        }
        tradeSubItemList.add(lan.createTradeSubItemJ("SPEED", speed, userId));
        // 这个是订单属性传入的值
        /*tradeSubItemList.add(lan.createTradeSubItem2("terminalchannel", "1", userId));
        tradeSubItemList.add(lan.createTradeSubItem2("terminalsrcmode", "A004", userId));
        tradeSubItemList.add(lan.createTradeSubItem2("terminaltype1", "2", userId));
        tradeSubItemList.add(lan.createTradeSubItem2("ishomeservice", "1", userId));
        tradeSubItemList.add(lan.createTradeSubItem2("iptvnbr", "", userId));
        tradeSubItemList.add(lan.createTradeSubItem2("iptvpassword", "", userId));
        tradeSubItemList.add(lan.createTradeSubItem2("terminalbrand", "", userId));
        tradeSubItemList.add(lan.createTradeSubItem2("terminalsn", "", userId));
        tradeSubItemList.add(lan.createTradeSubItem2("terminalmac", "", userId));
        tradeSubItemList.add(lan.createTradeSubItem2("iptvspecnbr", "", userId));
        tradeSubItemList.add(lan.createTradeSubItem2("iptvusername", "", userId));
        tradeSubItemList.add(lan.createTradeSubItem2("iptvuserpassword", "", userId));
        tradeSubItemList.add(lan.createTradeSubItem2("iptvremark", "", userId));
        tradeSubItemList.add(lan.createTradeSubItem2("developdepartid", "", userId));
        tradeSubItemList.add(lan.createTradeSubItem2("developdepartname", "", userId));
        tradeSubItemList.add(lan.createTradeSubItem2("developstaffname", "", userId));
        tradeSubItemList.add(lan.createTradeSubItem2("developerstaffid", "", userId));*/
        tradeSubItemList.add(lan.createTradeSubItemJ("ORGI_COMP_SN_A", serialNumber, userId));
        tradeSubItemList.add(lan.createTradeSubItemJ("ORGI_COMP_ID_A", msg.get("mixUserId"), userId));
        tradeSubItemList.add(lan.createTradeSubItemJ("BINDING_TYPE", "H007", userId));
        tradeSubItemList.add(lan.createTradeSubItemJ("BINDING_SERIALNUM", msg.get("serialNumber"), userId));
        tradeSubItemList.add(lan.createTradeSubItemJ("BINDING_USERID", msg.get("mixUserId"), userId));

        perServiceAttr(productInfo, tradeSubItemList, ext);
        tradeSubItem.put("item", tradeSubItemList);
        ext.put("tradeSubItem", tradeSubItem);
    }

    /**
     * 处理订单属性、订单子属性
     * @param productInfo
     * @param tradeSubItem
     * @param ext
     */
    private void perServiceAttr(List<Map> productInfo, List<Map> tradeSubItem, Map ext) {
        Map userInfo = (Map) threePartRet.get("userInfo");
        // 根据元素类型获取到对应的节点
        Map dataKey = MapUtils.asMap("D", "tradeDiscnt", "S", "tradeSvc", "X", "tradeSp");
        Map codeKey = MapUtils.asMap("D", "discntCode", "S", "serviceId", "X", "spServiceId");
        for (Map product : productInfo) {
            List<Map> packageElement = (List<Map>) product.get("packageElement");
            if (IsEmptyUtils.isEmpty(packageElement)) {
                continue;
            }
            for (Map pacEle : packageElement) {
                // 若该元素存在属性节点,则获取该元素的itemId
                if (!IsEmptyUtils.isEmpty(pacEle.get("itemInfo"))) {
                    Map tradeKeyData = (Map) ext.get(dataKey.get(pacEle.get("elementType")));
                    if (IsEmptyUtils.isEmpty(tradeKeyData) || IsEmptyUtils.isEmpty(tradeKeyData.get("item"))) {
                        continue;
                    }
                    String getCode = (String) codeKey.get(pacEle.get("elementType"));
                    List<Map> dataItem = (List<Map>) tradeKeyData.get("item");
                    for (Map item : dataItem) {
                        // 如果是该元素,则获取该元素的itemId拼装tradeSubItem节点
                        if (pacEle.get("elementId").equals(String.valueOf(item.get(getCode)))
                                && !IsEmptyUtils.isEmpty(item.get("itemId"))) {
                            Map dataMap = MapUtils.asMap("itemId", item.get("itemId"), "startDate",
                                    item.get("startDate"), "endDate", item.get("endDate"));
                            preTradeSubItemByServiceAttr((List<Map>) pacEle.get("itemInfo"), tradeSubItem, dataMap);
                        }
                    }
                }
                // 处理订单子属性
                if (!IsEmptyUtils.isEmpty(pacEle.get("subItemInfo"))) {
                    Map subItem = (Map) pacEle.get("subItemInfo");
                    tradeSubItem.add(lan.createTradeSubItemB2(String.valueOf(userInfo.get("userId")),
                            String.valueOf(subItem.get("subItemCode")), subItem.get("subItemValue")));
                }
            }
        }
    }

    /**
     * 拼装tradeSubItem节点
     * @param serviceAttr
     * @param tradeSubItem
     * @param serviceAttr
     */
    private void preTradeSubItemByServiceAttr(List<Map> serviceAttr, List<Map> tradeSubItem, Map dataMap) {
        LanUtils lan = new LanUtils();
        for (Map attr : serviceAttr) {
            tradeSubItem.add(lan.createTradeSubItemB((String) dataMap.get("itemId"), (String) attr.get("itemCode"),
                    attr.get("ItemValue"), (String) dataMap.get("startDate"), (String) dataMap.get("endDate")));
        }
    }

    private void preTradeItemData() {
        Map tradeItem = new HashMap();
        List<Map> tradeItemList = new ArrayList<Map>();

        tradeItemList.add(lan.createAttrInfoNoTime("OLD_GROUP_ID", msg.get("mixUserId")));
        tradeItemList.add(lan.createAttrInfoNoTime("DEVELOP_DEPART_ID", msg.get("channelId")));
        tradeItemList.add(lan.createAttrInfoNoTime("MAIN_CARD_TAG", "0"));
        tradeItemList.add(lan.createAttrInfoNoTime("COMP_DEAL_STATE", "1"));
        tradeItemList.add(lan.createAttrInfoNoTime("DEVELOP_STAFF_ID", msg.get("recomPersonId")));
        tradeItemList.add(lan.createAttrInfoNoTime("ALONE_TCS_COMP_INDEX", "2"));
        tradeItem.put("item", tradeItemList);
        ext.put("tradeItem", tradeItem);

    }

    private void preTradeRelationData() {
        Map userInfo = (Map) threePartRet.get("userInfo");
        List<Map> oldProducts = (List<Map>) threePartRet.get("productInfo");
        // 拼装tradeRelation主副卡节点-8800退订，H007订购
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
        item.put("roleCodeB", "4");
        itemList.add(item);
        // 订购
        item.put("relationTypeCode", "H007");
        item.put("modifyTag", "0");
        item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
        item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        item.put("roleCodeB", "0");
        itemList.add(item);

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
            item.put("roleCodeB", map.get("roleCodeB")); // roleCodeB：订购-0，退订-4
            item.put("remark", "");
            relationList.add(item);
        }
        ext.put("tradeRelation", MapUtils.asMap("item", relationList));
    }

}
