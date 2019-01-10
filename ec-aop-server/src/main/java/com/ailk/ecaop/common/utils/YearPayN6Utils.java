package com.ailk.ecaop.common.utils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.lang.RDate;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

import com.ailk.ecaop.biz.product.DateUtils;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.alibaba.fastjson.JSON;

/**
 * 北六省宽带趸交、宽带包年相关公共方法
 * @author Steven
 */
public class YearPayN6Utils {

    public static final String ERROR_CODE = "8888";
    private final static Map EXTENDS_ATTR = MapUtils.asMap("ACCT_NBR", "ACCT_NBR", "ACCT_PASSWD", "ACCT_PASSWD",
            "LINK_NAME", "LINK_NAME", "LINK_PHONE", "LINK_PHONE");
    // 辽宁专用的.... by wangmc 20180809
    private final static Map LN_EXTENDS_ATTR = MapUtils.asMap("LINK_NAME", "LINK_NAME", "LINK_PHONE", "LINK_PHONE");

    public Map preTradeItem(Map inputMap) {
        Map TradeItem = new HashMap();
        List<Map> items = new ArrayList<Map>();
        if ("17".equals(inputMap.get("province"))) {
            items.add(LanUtils.createTradeItemTime("CONTINUE_HeavenEarthTrans", "DealTradeFee"));
            items.add(LanUtils.createTradeItemTime("SEND_TYPE", "0"));
            items.add(LanUtils.createTradeItemTime("STANDARD_KIND_CODE", inputMap.get("channelType").toString()));
            items.add(LanUtils.createTradeItemTime("xState", "Y"));
            items.add(LanUtils.createTradeItemTime("fillSubItemAfter", ""));
        }
        else {
            items.add(LanUtils.createTradeItemTime("PH_NUM", ""));
            items.add(LanUtils.createTradeItemTime("SFGX_2060", "N"));
            items.add(LanUtils.createTradeItemTime("GXLX_TANGSZ", ""));
            items.add(LanUtils.createTradeItemTime("STANDARD_KIND_CODE", inputMap.get("city")));
            items.add(LanUtils.createTradeItemTime("MARKETING_MODE", "1"));
            items.add(LanUtils.createTradeItemTime("IMMEDIACY_INFO", "0"));
            items.add(LanUtils.createTradeItemTime("USER_ACCPDATE", ""));
            items.add(LanUtils.createTradeItemTime("SUB_TYPE", "0"));
        }
        items.add(LanUtils.createTradeItemTime("ECS_SINGLE_FLAG", "1"));
        items.add(LanUtils.createTradeItemTime("DEVELOP_DEPART_ID", inputMap.get("recomDepartId")));
        items.add(LanUtils.createTradeItemTime("DEVELOP_STAFF_ID", inputMap.get("recomPersonId")));
        TradeItem.put("item", items);
        return TradeItem;
    }

    public Map preTradeUserItem(Map inputMap) {
        Map TradeUser = new HashMap();
        List<Map> item = new ArrayList<Map>();
        List<Map> productList = (List<Map>) inputMap.get("productInfo");
        String netTypeCode = "0040";
        for (Map productMap : productList) {
            if (!"0".equals(productMap.get("optType")) || !"0".equals(productMap.get("productMode"))) {
                continue;
            }
            Map itemMap = MapUtils.asMap("userId", inputMap.get("userId"), "productId", productMap.get("oldProductId"),
                    "xDatatype", "NULL", "brandCode", inputMap.get("brandCode"), "netTypeCode", netTypeCode,
                    "productTypeCode", inputMap.get("productTypeCode"));
            item.add(itemMap);
        }
        TradeUser.put("item", item);
        return TradeUser;
    }

    /**
     * 准备订购产品的TRADE_SUB_ITEM
     * @param inputMap
     * @return
     */
    public Map preTradeSubItem(Map inputMap, Map threePartInfo) {
        Map TradeSubItem = new HashMap();
        String discntitemId = (String) inputMap.get("discntItemId");
        String userId = (String) inputMap.get("userId");
        String startDate = GetDateUtils.getNextMonthFirstDayFormat();
        // 辽宁省份的宽带资费属性的开始时间要取资费的开始时间 by wangmc 20180809
        if (!IsEmptyUtils.isEmpty(inputMap.get("LNDiscntStartDate"))) {
            startDate = (String) inputMap.get("LNDiscntStartDate");
        }
        String endDate = "20501231235959";
        LanUtils lan = new LanUtils();
        List<Map> items = new ArrayList<Map>();
        List<Map> broadDiscntInfo = (List<Map>) inputMap.get("broadDiscntInfo");
        inputMap.put("broadDiscntId", broadDiscntInfo.get(0).get("broadDiscntId"));
        inputMap.put("delayDiscntId", ((Map) broadDiscntInfo.get(0).get("broadDiscntAttr")).get("delayDiscntId"));
        Object cycleFee = null;
        List<Map> feeInfos = (List<Map>) inputMap.get("feeInfo");
        if (feeInfos != null && 0 != feeInfos.size()) {
            for (Map feeInfo : feeInfos) {
                cycleFee = feeInfo.get("oldFee");
                inputMap.put("cycleFee", cycleFee);
            }
        }
        cycleFee = null == cycleFee ? "990" : cycleFee;
        if (null != broadDiscntInfo && !broadDiscntInfo.isEmpty()) {
            // 默认不传按续约标准资费b
            String delayType = (String) ((Map) broadDiscntInfo.get(0).get("broadDiscntAttr")).get("delayType");
            Object brandNumber = ((List<Map>) inputMap.get("productInfo")).get(0).get("brandNumber");
            inputMap.put("brandNumber", brandNumber);
            String expireDealMode = "1".equals(delayType) ? "a" : "3".equals(delayType) ? "t" : "b";
            String aDiscntCode = (String) inputMap.get("delayDiscntId");
            String bDiscntCode = (String) inputMap.get("broadDiscntId");
            items.add(lan.createTradeSubItemC(discntitemId, "adEnd", "", startDate, endDate));
            items.add(lan.createTradeSubItemC(discntitemId, "aDiscntCode", aDiscntCode, startDate, endDate));
            if (!"76".equals(inputMap.get("province"))) {
                items.add(lan.createTradeSubItemC(discntitemId, "callBack", "0", startDate, endDate));
                items.add(lan.createTradeSubItemC(discntitemId, "adStart", "0", startDate, endDate));
                // 山东不下发这些属性 by wangmc 20180804
                if (!"17".equals(inputMap.get("province"))) {
                    items.add(lan.createTradeSubItemC(discntitemId, "expireDealMode", expireDealMode, startDate,
                            endDate));
                    items.add(lan.createTradeSubItemC(discntitemId, "fixedHire", brandNumber, startDate, endDate));
                    if (!"91".equals(inputMap.get("province"))) {// 辽宁不要这个属性...
                        items.add(lan.createTradeSubItemC(discntitemId, "effectMode", delayType, startDate, endDate));
                    }
                    items.add(lan.createTradeSubItemC(discntitemId, "cycleNum", "1", startDate, endDate));
                    items.add(lan.createTradeSubItemC(discntitemId, "cycleFee", cycleFee, startDate, endDate));
                    items.add(lan.createTradeSubItemC(discntitemId, "cycle", brandNumber, startDate, endDate));
                    items.add(lan.createTradeSubItemC(discntitemId, "recharge", "", startDate, endDate));
                }
                if ("18".equals(inputMap.get("province"))) {
                    if ("a".equals(expireDealMode)) {
                        items.add(lan.createTradeSubItemC(discntitemId, "aDiscntCode", "", startDate, endDate));
                        items.add(lan.createTradeSubItemC(discntitemId, "bDiscntCode", "", startDate, endDate));
                    }
                    else if ("b".equals(expireDealMode)) {
                        items.add(lan.createTradeSubItemC(discntitemId, "aDiscntCode", aDiscntCode, startDate, endDate));
                        items.add(lan.createTradeSubItemC(discntitemId, "bDiscntCode", "", startDate, endDate));
                    }
                }
                else {
                    if ("a".equals(expireDealMode)) {
                        items.add(lan.createTradeSubItemC(discntitemId, "bDiscntCode", bDiscntCode, startDate, endDate));
                    }
                    else {
                        items.add(lan.createTradeSubItemC(discntitemId, "bDiscntCode", "", startDate, endDate));
                    }
                }
            }
        }
        if ("17".equals(inputMap.get("province"))) {
            // 这些属性使用宽带资费的itemId by wangmc 20180803
            preTradeSubItem4SD(items, inputMap, discntitemId, new SuniTradeUtils());
        }
        else {
            preTradeSubItemNotSD(items, threePartInfo, userId, lan, inputMap);
        }
        TradeSubItem.put("item", items);
        return TradeSubItem;
    }

    private void preTradeSubItemNotSD(List<Map> items, Map threePartInfo, String userId, LanUtils lan, Map inputMap) {
        // 判断宽带下发速率
        if ("18".equals(inputMap.get("province"))) {
            String productId = (String) inputMap.get("newProductId");
            String speed = changeSpeed(productId);
            String attrCode = changeAttrCode(speed);
            items.add(lan.createTradeSubItem(attrCode, speed, userId));
        }
        else {
            if (null != inputMap.get("speedLevel") && !"".equals(inputMap.get("speedLevel"))) {
                if ("91".equals(inputMap.get("province"))) {
                    if ("ADSL".equals(inputMap.get("brandCode"))) {
                        items.add(lan.createTradeSubItem("SPEED", getAdslSpeed(inputMap), userId));
                    }
                    if ("FTTH|LANZ|1L00".contains(inputMap.get("brandCode") + "")) {
                        items.add(lan.createTradeSubItem("SPEED", getFtthSpeed(inputMap), userId));
                    }
                }
                else {
                    String speed = (String) inputMap.get("speedLevel");
                    items.add(lan.createTradeSubItem("SPEED", speed, userId));
                }
            }
            else {
                items.add(lan.createTradeSubItem("SPEED", "10", userId));//
            }
            // 辽宁不下发这些属性 by wangmc 20180809
            if (!"91".equals(inputMap.get("province"))) {
                items.add(lan.createTradeSubItem("SHARE_NBR", "", userId));
                items.add(lan.createTradeSubItem("USETYPE", "1", userId));
                items.add(lan.createTradeSubItem("WOPAY_MONEY", "", userId));
                items.add(lan.createTradeSubItem("ACCESS_TYPE", "B61", userId));//
                items.add(lan.createTradeSubItem("TIME_LIMIT_ID", "2", userId));
                items.add(lan.createTradeSubItem("CONNECTNETMODE", "1", userId));
                items.add(lan.createTradeSubItem("ISWAIL", "0", userId));
                items.add(lan.createTradeSubItem("TERMINAL_SN", "", userId));
                items.add(lan.createTradeSubItem("TERMINAL_MAC", "", userId));
                items.add(lan.createTradeSubItem("ISWOPAY", "0", userId));
                items.add(lan.createTradeSubItem("KDLX_2061", "N", userId));
                items.add(lan.createTradeSubItem("TERMINALSRC_MODE", "A001", userId));
                items.add(lan.createTradeSubItem("USER_TYPE_CODE", "0", userId));
                items.add(lan.createTradeSubItem("CB_ACCESS_TYPE", "A13", userId));
                items.add(lan.createTradeSubItem("COMMPANY_NBR", "", userId));
                items.add(lan.createTradeSubItem("TOWN_FLAG", "C", userId));
                items.add(lan.createTradeSubItem("TERMINAL_MODEL", "", userId));
                items.add(lan.createTradeSubItem("HZGS_0000", "", userId));
                items.add(lan.createTradeSubItem("EXPECT_RATE", "", userId));
                items.add(lan.createTradeSubItem("TERMINAL_TYPE", "2", userId));
                items.add(lan.createTradeSubItem("TERMINAL_BRAND", "", userId));
                items.add(lan.createTradeSubItem("COLLINEAR_TYPE", "X3", userId));
                items.add(lan.createTradeSubItem("OTHERCONTACT", "", userId));
            }
            Map userInfo = ((List<Map>) threePartInfo.get("userInfo")).get(0);
            List<Map> attrInfo = (List<Map>) userInfo.get("attrInfo");
            if (!IsEmptyUtils.isEmpty(attrInfo)) {
                for (Map attr : attrInfo) {
                    if (null == EXTENDS_ATTR.get(attr.get("attrCode"))) {
                        continue;
                    }
                    // 辽宁只需要下发LINK_NAME和LINK_PHONE
                    if ("91".equals(inputMap.get("province")) && null == LN_EXTENDS_ATTR.get(attr.get("attrCode"))) {
                        continue;
                    }
                    items.add(lan.createTradeSubItem(attr.get("attrCode").toString(), attr.get("attrValue"), userId));
                }
            }
        }
    }

    public static String changeAttrCode(Object speed) {
        Map changeMap = MapUtils.asMap("98044", "C_98", "98045", "C_98", "98046", "C_98", "98034", "C_98", "802987",
                "C_26", "802986", "C_26", "802978", "C_26", "802990", "C_26", "802897", "C_26");
        String retStr = (String) changeMap.get(speed);
        return null == retStr ? "10" : retStr;
    }

    public static String changeSpeed(String productId) {
        Map speedMap = (Map) JSON.parse(EcAopConfigLoader.getStr("ecaop.global.param.speedLevel.18"));
        String speed = (String) speedMap.get(productId);
        return speed;

    }

    private void preTradeSubItem4SD(List<Map> items, Map inputMap, String discntitemId, SuniTradeUtils lan) {
        String startDate = DateUtils.getNextMonthFirstDay();
        List<Map> paraList = (List<Map>) inputMap.get("para");
        Object yearmoney = null;
        if (paraList != null && 0 != paraList.size()) {
            for (Map para : paraList) {
                if ("discntFee".equals(para.get("paraId"))) {
                    yearmoney = para.get("paraValue");
                }
            }
        }
        yearmoney = null == yearmoney ? inputMap.get("cycleFee") : yearmoney;
        items.add(lan.createSubItem4UserAttr(discntitemId, "monthnum", inputMap.get("brandNumber"), startDate));
        items.add(lan.createSubItem4UserAttr(discntitemId, "monthdepositrate", "1.0", startDate));
        items.add(lan.createSubItem4UserAttr(discntitemId, "monthcyclefee", "100", startDate));
        items.add(lan.createSubItem4UserAttr(discntitemId, "monthnumsale", inputMap.get("brandNumber"), startDate));
        items.add(lan.createSubItem4UserAttr(discntitemId, "yearmoney", yearmoney, startDate));
        // items.add(lan.createSubItem4UserAttr(discntitemId, "expiredealmode", "N", startDate));
        // items.add(lan.createSubItem4UserAttr(discntitemId, "agrnotifynumber", "", startDate));
        // items.add(lan.createSubItem4UserAttr(discntitemId, "bcafixedhireflag", "0", startDate));
        // 取消标准资费的下发,并下发转换的速率ATTR_TYPE_CODE喂0时代表用户属性,itemId要使用用户ID
        // items.add(lan.createSubItem4UserAttr(discntitemId, "biaozhuncode", inputMap.get("delayDiscntId"),
        // startDate));
        items.add(new LanUtils().createTradeSubItemJ("Speed", getSpeed(inputMap), (String) inputMap.get("userId")));
        // items.add(lan.createSubItem4UserAttr(discntitemId, "xieyicode", inputMap.get("broadDiscntId"), startDate));
        // items.add(lan.createSubItem4UserAttr(discntitemId, "xieyifee", "", startDate));
        items.add(lan.createSubItem4UserAttr(discntitemId, "dealmode", "ADD", startDate));// 修改为使用ATTR_TYPE_CODE为3的方法
    }

    /**
     * 山东省专用获取宽带速率
     * @param msg
     * @return
     */
    private String getSpeed(Map msg) {
        Map speedMap = (Map) JSON.parse(EcAopConfigLoader.getStr("ecaop.global.param.N6broad.speedLevel.17"));
        return (String) speedMap.get(msg.get("speedLevel"));
    }

    private String getAdslSpeed(Map msg) {
        Map speedMap = (Map) JSON.parse(EcAopConfigLoader.getStr("ecaop.global.param.N6broad.speedLevel.ADSL.91"));
        return (String) speedMap.get(msg.get("speedLevel"));
    }

    private String getFtthSpeed(Map msg) {
        Map speedMap = (Map) JSON.parse(EcAopConfigLoader.getStr("ecaop.global.param.N6broad.speedLevel.91"));
        return (String) speedMap.get(msg.get("speedLevel"));
    }

    /**
     * 处理订购产品的资费信息
     * @param msg
     * @return
     * @throws Exception
     */
    public Map preDiscntData(Map msg) throws Exception {
        Map tradeDis = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        int brandNumber = Integer.valueOf((String) ((List<Map>) msg.get("productInfo")).get(0).get("brandNumber"));
        List<Map> discnt = (List<Map>) msg.get("discntList");
        Object province = msg.get("province");
        for (int i = 0; i < discnt.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("id", msg.get("userId"));
            item.put("idType", "1");
            item.put("userIdA", "-1");
            item.put("productId", discnt.get(i).get("productId"));
            item.put("packageId", discnt.get(i).get("packageId"));
            item.put("discntCode", discnt.get(i).get("discntCode"));
            item.put("specTag", "0");// FIXME
            item.put("relationTypeCode", "");
            String startDate = GetDateUtils.getNextMonthFirstDayFormat();
            if ("91".equals(msg.get("province"))) {
                item.put("startDate", GetDateUtils.getNextDateFormat());
                // 辽宁省份下发的资费属性的开始时间需要与资费的开始时间保持一致,后边拼装资费属性时会获取
                msg.put("LNDiscntStartDate", item.get("startDate"));
            }
            else {
                item.put("startDate", startDate);
            }
            item.put("itemId", msg.get("discntItemId"));
            if (null != discnt.get(i).get("activityTime")) {
                item.put("endDate", GetDateUtils.transDate((String) discnt.get(i).get("activityTime"), 14));
            }
            else {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                String endDate = sdf.format(RDate.addMonths(sdf.parse(startDate), brandNumber));
                endDate = endDate.substring(0, 8) + "000000";
                endDate = DateUtils.addSeconds(endDate, -1);
                item.put("endDate", endDate);
            }
            if ("91".equals(province)) {
                item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            }
            // 河北要求：变更新包年资费时，AOP传给BSS的资费的生效时间应为次月1日、失效时间应为2050年，
            // 同时资费属性的生效时间、失效时间应该与资费的生失效时间一致。
            if ("18".equals(province)) {
                item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            }
            if ("50".equals(discnt.get(i).get("productMode"))) {
                item.put("itemId", msg.get("itemId"));
            }
            item.put("modifyTag", "0");
            itemList.add(item);

        }
        Map numberDiscnt = (Map) msg.get("numberDiscnt");
        if (null != numberDiscnt && !numberDiscnt.isEmpty()) {
            Map item = new HashMap();
            item.putAll(numberDiscnt);
            itemList.add(item);
        }
        Map numberDis = (Map) msg.get("numberDis");
        if (null != numberDis && !numberDis.isEmpty()) {
            Map item1 = new HashMap();
            item1.putAll(numberDis);
            itemList.add(item1);

        }
        tradeDis.put("item", itemList);
        return tradeDis;
    }

    /**
     * 处理退订产品信息
     * 需要准备三个节点
     * TRADE_OTHER
     * TRADE_PRODUCT
     * TRADE_PRODUCT_TYPE
     * @param product
     * @param threePart
     * @return
     */
    public Map dealBackProduct(Map product, Map threePart) {
        Map userInfo = getInfoFromThreePart(threePart, "userInfo");
        List<Map> curtProductInfo = (List<Map>) userInfo.get("productInfo");
        if (IsEmptyUtils.isEmpty(curtProductInfo)) {
            throw new EcAopServerBizException("8888", "当前用户在北六ESS未订购任何产品");
        }
        Map curtProduct = null;
        for (Map temp : curtProductInfo) {
            if (product.get("oldProductId").equals(temp.get("productId"))) {
                curtProduct = temp;
            }
        }
        if (null == curtProduct) {
            throw new EcAopServerBizException("8888", "要退订的产品未被订购过,无法退订");
        }
        Map retMap = new HashMap();
        retMap.put("tradeOther", preTradeOtherItem(threePart));
        retMap.put("tradeProductType", preTradeProductTypeBackItem(curtProduct, userInfo));
        retMap.put("tradeProduct", preTradeProductBackItem(curtProduct, userInfo));
        return retMap;
    }

    public Map preTradeSvcBackItem(Map userInfo, Map svc) {
        Map item = new HashMap();
        item.put("xDatatype", MagicNumber.STRING_OF_NULL);
        item.put("userId", userInfo.get("userId"));
        item.put("serviceId", svc.get("serviceId"));
        item.put("modifyTag", "1");
        item.put("startDate", svc.get("startDate"));
        item.put("endDate", GetDateUtils.getMonthLastDayFormat());// 北六说老服务在当月最后一天结束 addby sss
        item.put("productId", svc.get("productId"));
        item.put("packageId", svc.get("packageId"));
        item.put("itemId", svc.get("itemId"));
        item.put("userIdA", svc.get("userIdA"));
        return item;
    }

    /**
     * 处理退订产品的TRADE_PRODUCT_TYPE节点
     * @param curtProduct
     * @param userInfo
     * @return
     */
    private Map preTradeProductTypeBackItem(Map curtProduct, Map userInfo) {
        Map item = new HashMap();
        item.put("xDatatype", MagicNumber.STRING_OF_NULL);
        item.put("userId", userInfo.get("userId"));
        item.put("productMode", curtProduct.get("productMode"));
        item.put("productId", curtProduct.get("productId"));
        item.put("productTypeCode", userInfo.get("productTypeCode"));
        item.put("modifyTag", "1");
        item.put("startDate", curtProduct.get("productActiveTime"));
        item.put("endDate", GetDateUtils.getMonthLastDayFormat());
        return item;
    }

    /**
     * 处理退订产品的TRADE_PRODUCT节点
     * @param curtProduct
     * @param userInfo
     * @return
     */
    private Map preTradeProductBackItem(Map curtProduct, Map userInfo) {
        Map item = new HashMap();
        item.put("xDatatype", MagicNumber.STRING_OF_NULL);
        item.put("userIdA", "-1");
        item.put("productMode", curtProduct.get("productMode"));
        item.put("productId", curtProduct.get("productId"));
        item.put("brandCode", userInfo.get("brandCode"));
        item.put("itemId", userInfo.get("userId"));
        item.put("modifyTag", "1");
        item.put("userId", userInfo.get("userId"));
        item.put("startDate", curtProduct.get("productActiveTime"));
        item.put("endDate", GetDateUtils.getMonthLastDayFormat());
        return item;
    }

    /**
     * 处理TRADE_OTHER节点,此节点只用于退订
     * @param threePart
     * @return
     */
    private Map preTradeOtherItem(Map threePart) {
        Map item = new HashMap();
        item.put("endDate", GetDateUtils.getMonthLastDayFormat());
        item.put("modifyTag", "1");
        Map userInfo = getInfoFromThreePart(threePart, "userInfo");
        item.put("rsrvStr1", userInfo.get("productId"));
        item.put("rsrvStr10", userInfo.get("serialNumber"));
        item.put("rsrvStr2", "00");
        item.put("rsrvStr3", "-9");
        item.put("rsrvStr4", userInfo.get("brandCode"));
        item.put("rsrvStr5", "undefined");
        item.put("rsrvStr6", "-1");
        item.put("rsrvStr7", "0");
        item.put("rsrvStr8", "-1");
        item.put("rsrvStr9", "0");
        item.put("rsrvValue", userInfo.get("userId"));
        item.put("rsrvValueCode", "NEXP");
        List<Map> productInfo = (List<Map>) userInfo.get("productInfo");
        Object startDate = GetDateUtils.getDate();
        if (!IsEmptyUtils.isEmpty(productInfo)) {
            for (Map product : productInfo) {
                if (userInfo.get("productId").equals(product.get("productId"))) {
                    startDate = product.get("productActiveTime");
                }
            }
        }
        item.put("startDate", startDate);// 当月第一天
        item.put("xDatatype", MagicNumber.STRING_OF_NULL);
        return item;
    }

    /**
     * 从三户接口中通过key获取节点信息,如果不存在,提示错误,否则取第一个.
     * @param inMap
     * @param key
     * @return
     */
    public Map getInfoFromThreePart(Map inMap, String key) {
        List<Map> infoList = (List<Map>) inMap.get(key);
        if (null == infoList || 0 == infoList.size()) {
            throw new EcAopServerBizException(ERROR_CODE, "北六ESS系统三户接口未返回[" + key + "]信息");
        }
        return infoList.get(0);
    }

    public Map preTradeItemItem() {
        Map item = new HashMap();
        return item;
    }
}
