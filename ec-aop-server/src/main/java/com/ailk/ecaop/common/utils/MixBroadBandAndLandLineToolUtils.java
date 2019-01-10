package com.ailk.ecaop.common.utils;
/**
 * Created by Liu JiaDi on 2016/7/5.
 */

import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import org.apache.commons.lang.StringUtils;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.alibaba.fastjson.JSONArray;

/**
 * 该类用于各种宽带固话相关业务数据整合、转码以及参数准备
 *
 * @auther Liu JiaDi
 * @create 2016_07_05_14:36
 */
public class MixBroadBandAndLandLineToolUtils {

    /**
     * 该方法用于对宽带或固话终端进行转码
     * 转化为cb对应码
     */
    public static String changeTerminalCode(String machineType) {
        if ("1".equals(machineType)) {
            machineType = "A001";
        } else if ("2".equals(machineType)) {
            machineType = "A002";
        } else if ("3".equals(machineType)) {
            machineType = "A003";
        } else if ("4".equals(machineType)) {
            machineType = "A006";
        } else if ("5".equals(machineType)) {
            machineType = "A009";
        }
        return machineType;
    }

    /**
     * 该方法用于对宽带或固话使用人性质userProperty进行转码
     * 转化为cb对应码
     */
    public static String changeUserPropertyCode(String userProperty) {
        if (StringUtils.isEmpty(userProperty)) {
            userProperty = "1";
        } else if ("2".equals(userProperty)) {
            userProperty = "3";
        } else if ("3".equals(userProperty)) {
            userProperty = "2";
        } else if ("4".equals(userProperty)) {
            userProperty = "5";
        } else if ("5".equals(userProperty)) {
            userProperty = "4";
        } else if ("6".equals(userProperty)) {
            userProperty = "99";
        }
        return userProperty;
    }

    /**
     * 该方法用于对宽带或固话地域标识cityMark进行转码
     * 转化为cb对应码
     */
    public static String changeCityMarkCode(String cityMark) {
        if (StringUtils.isEmpty(cityMark) || "1".equals(cityMark)) {
            cityMark = "C";
        } else {
            cityMark = "T";
        }
        return cityMark;
    }

    /**
     * 该方法用于对宽带或固话受理方式acceptMode进行转码
     * 转化为cb对应码
     */
    public static String changeAcceptModeCode(String acceptMode) {
        if (StringUtils.isEmpty(acceptMode) || "1".equals(acceptMode)) {
            acceptMode = "0";
        } else {
            acceptMode = "1";
        }
        return acceptMode;
    }

    /**
     * 该方法用于对宽带或固话boradDiscntInfo下面开户资费、到期资费、续包资费
     * 以及费用、有效期等字段的处理
     * msg中必传属性值itemid、开始时间startDate(针对不同业务开始时间可能不同)、tradeSubItem(如果没有可以忽略)
     * endDate写死
     * 只针对sub_item需要下发的信息
     * 对于开户资费id对应的trade_discnt请自行处理
     */
    public static void preStartOrEndDiscntInfo(Map msg) {
        List tradeSubItem = (List<Map>) msg.get("tradeSubItem");
        if (null == tradeSubItem) {
            tradeSubItem = new ArrayList<Map>();
        }
        String endDate = "20501231235959";// 辽宁北六E要求，结束时间必须为14位
        String nextDate = GetDateUtils.getNextMonthFirstDayFormat();
        LanUtils lan = new LanUtils();
        String boradDiscntId = "";//开户选择的资费
        String delayType = "";//到期资费方式
        String delayDiscntId = "";//到期资费
        String delayYearDiscntId = "";//续包年资费
        String delayDiscntType = "";//生效方式
        String boradDiscntCycle = "";//包年周期
        String cycleNum = "";
        int cycleFee = 0;
        String diacntName;
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        Map actiPeparam = new HashMap();
        String itemId = (String) msg.get("itemId");
        if (StringUtils.isEmpty(itemId)) {
            throw new EcAopServerBizException("9999", "请传入对应itemId");
        }
        String startDate = (String) msg.get("startDate");
        if (StringUtils.isEmpty(startDate)) {
            throw new EcAopServerBizException("9999", "请传入对应startDate");
        }
        Map newUserInfo = (Map) msg.get("newUserInfo");
        List<Map> boradDiscntInfos = (List<Map>) newUserInfo.get("boradDiscntInfo");
        if (null != boradDiscntInfos && boradDiscntInfos.size() > 0) {
            boradDiscntCycle = (String) boradDiscntInfos.get(0).get("boradDiscntCycle");
            boradDiscntId = (String) boradDiscntInfos.get(0).get("boradDiscntId");
            if (StringUtils.isEmpty(boradDiscntId)) {
                throw new EcAopServerBizException("9999", "开户选择的主资费编码boradDiscntId为空，请检查");
            }
        }
        Map boradDiscntAttr = (Map) boradDiscntInfos.get(0).get("boradDiscntAttr");
        if (null != boradDiscntAttr && boradDiscntAttr.size() > 0) {
            delayType = (String) boradDiscntAttr.get("delayType");
            if (StringUtils.isEmpty(delayType)) {
                throw new EcAopServerBizException("9999", "到期资费方式delayType为空，请检查");
            }
            delayYearDiscntId = (String) boradDiscntAttr.get("delayYearDiscntId");
            if (StringUtils.isEmpty(delayYearDiscntId) && "1".equals(delayType)) {
                throw new EcAopServerBizException("9999", "续约包年时续包年资费必传");
            }
            delayDiscntId = (String) boradDiscntAttr.get("delayDiscntId");
            if (StringUtils.isEmpty(delayDiscntId) && !"3".equals(delayType)) {
                throw new EcAopServerBizException("9999", "到期资费delayDiscntId为空，请检查");
            }
            else if (StringUtils.isEmpty(delayDiscntId) && "3".equals(delayType)) {
                delayDiscntId = "";
            }
            delayDiscntType = (String) boradDiscntAttr.get("delayDiscntType");
            if (StringUtils.isEmpty(delayDiscntType)) {
                throw new EcAopServerBizException("9999", "生效方式delayDiscntType为空，请检查");
            }
        }

        //aDiscntCode 到期资费 bDiscntCode续包年资费
        if ("1".equals(delayType)) {
            tradeSubItem.add(lan.createTradeSubItemC(itemId, "expireDealMode", "a", startDate, endDate));
            tradeSubItem.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
            tradeSubItem.add(lan.createTradeSubItemC(itemId, "bDiscntCode", delayYearDiscntId, startDate, endDate));
        }
        if ("2".equals(delayType)) {
            tradeSubItem.add(lan.createTradeSubItemC(itemId, "expireDealMode", "b", startDate, endDate));
            tradeSubItem.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
            tradeSubItem.add(lan.createTradeSubItemC(itemId, "bDiscntCode", "", startDate, endDate));
        }
        if ("3".equals(delayType)) {
            tradeSubItem.add(lan.createTradeSubItemC(itemId, "expireDealMode", "t", startDate, endDate));
            tradeSubItem.add(lan.createTradeSubItemC(itemId, "aDiscntCode", delayDiscntId, startDate, endDate));
            tradeSubItem.add(lan.createTradeSubItemC(itemId, "bDiscntCode", "", startDate, endDate));
        }
        actiPeparam.put("DISCNT_CODE", boradDiscntId);
        List<Map> activityList = dao.queryBrdOrLineDiscntInfo(actiPeparam);
        Map activityListMap = null;
        if (activityList != null && activityList.size() > 0) {
            activityListMap = activityList.get(0);

        } else {
            throw new EcAopServerBizException("9999", "在资费信息表中未查询到ID为【" + boradDiscntId + "】的资费信息");

        }
        try {
            String resActivityper = String.valueOf(activityListMap.get("END_OFFSET"));// 失效效偏移时间
            String endTag = String.valueOf(activityListMap.get("END_ENABLE_TAG"));// 是否是绝对时间
            String disnctFee = String.valueOf(activityListMap.get("DISNCT_SALEFEE"));// 资费销售金额 单位分
            String endUnit = String.valueOf(activityListMap.get("END_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年
            diacntName = String.valueOf(activityListMap.get("DISCNT_NAME"));// 资费名称
            // 5:自然年';
            if (!"null".equals(endTag) && "1".equals(endTag) && !"null".equals(resActivityper) && !"null".equals(endUnit) && "3".equals(endUnit)) {
                cycleNum = resActivityper;
            } else {
                cycleNum = "12";
            }
            /*
             * if ("null".equals(disnctFee)) {
             * throw new EcAopServerBizException("9999", "在资费信息表中未查询到销售费用DISNCT_SALEFEE信息");
             * } else {
             * cycleFee = Integer.parseInt(disnctFee) / 100;
             * }
             */
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", e.getMessage() + "获取资费信息失败，资费是：" + boradDiscntId);
        }
        //这些属性只有包年时候有
        if (!"null".equals(diacntName) && diacntName.contains("年")) {
            if (StringUtils.isNotEmpty(boradDiscntCycle)) {
                cycleNum = boradDiscntCycle;
            }
            if ("0".equals(delayDiscntType)) {
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "effectMode", delayDiscntType, startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "adStart", startDate, startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "cycleNum", "1", startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "cycle", cycleNum, startDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "fixedHire", cycleNum, startDate, endDate));
                // tradeSubItem.add(lan.createTradeSubItemC(itemId, "cycleFee", cycleFee, startDate, endDate));
            }
            else {
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "effectMode", delayDiscntType, nextDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "adStart", nextDate, nextDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "cycleNum", "1", nextDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "cycle", cycleNum, nextDate, endDate));
                tradeSubItem.add(lan.createTradeSubItemC(itemId, "fixedHire", cycleNum, nextDate, endDate));
                // tradeSubItem.add(lan.createTradeSubItemC(itemId, "cycleFee", cycleFee, nextDate, endDate));
            }
        }
        msg.put("tradeSubItem", tradeSubItem);
    }

    /**
     * 北六E包年资费处理
     * 该方法目前处理：1.下发包年属性到trade_sub_item(包年属性传到产品的附加包下)。
     * 2.处理到期资费，到期资费下发到TRADE_DISCNT节点下。
     * 参数：msg中必传属性值itemId、eparchyCode、tradeSubItem(如果没有可以忽略)
     */
    public static void preStartOrEndDiscntInfoForN6(Map msg) {
        // 资费节点校验
        checkDiscntInfo(msg);

        // 到期资费下发（TRADE_DISCNT节点处理）
        dealDelayDiscnt(msg);

        // 包年资费属性下发（TRADE_SUB_ITEM节点处理）
        dealDiscntAttr(msg);
    }

    /**
     * 北六E-包年开户时下发资费节点校验
     */
    public static void checkDiscntInfo(Map msg) {
        String delayType = "";// 到期资费方式
        String delayDiscntId = "";// 到期资费

        String boradDiscntId = "";// 开户选择的资费
        String delayDiscntType = "";// 生效方式
        String delayYearDiscntId = "";// 续包年资费

        String itemId = (String) msg.get("itemId");
        if (StringUtils.isEmpty(itemId)) {
            throw new EcAopServerBizException("9999", "请传入对应itemId");
        }
        Map newUserInfo = (Map) msg.get("newUserInfo");
        List<Map> boradDiscntInfos = (List<Map>) newUserInfo.get("boradDiscntInfo");
        if (null != boradDiscntInfos && boradDiscntInfos.size() > 0) {
            boradDiscntId = (String) boradDiscntInfos.get(0).get("boradDiscntId");
            if (StringUtils.isEmpty(boradDiscntId)) {
                throw new EcAopServerBizException("9999", "开户选择的主资费编码boradDiscntId为空，请检查");
            }
        }
        Map boradDiscntAttr = (Map) boradDiscntInfos.get(0).get("boradDiscntAttr");
        if (null != boradDiscntAttr && boradDiscntAttr.size() > 0) {
            delayType = (String) boradDiscntAttr.get("delayType");
            if (StringUtils.isEmpty(delayType)) {
                throw new EcAopServerBizException("9999", "到期资费方式delayType为空，请检查");
            }
            delayYearDiscntId = (String) boradDiscntAttr.get("delayYearDiscntId");
            if (StringUtils.isEmpty(delayYearDiscntId) && "1".equals(delayType)) {
                throw new EcAopServerBizException("9999", "续约包年时续包年资费必传");
            }
            delayDiscntId = (String) boradDiscntAttr.get("delayDiscntId");// 北六校验
            // if (StringUtils.isEmpty(delayDiscntId)) {
            // throw new EcAopServerBizException("9999", "到期资费delayDiscntId为空，请检查");
            // }
            delayDiscntType = (String) boradDiscntAttr.get("delayDiscntType");
            if (StringUtils.isEmpty(delayDiscntType)) {
                throw new EcAopServerBizException("9999", "生效方式delayDiscntType为空，请检查");
            }
        }
        msg.put("delayType", delayType);
        msg.put("itemIdNew", itemId);// 处理TRADE_SUB_ITEM时用此ID，这里换个名字防止后边处理资费时覆盖
        msg.put("delayDiscntId", delayDiscntId);

    }

    /**
     * 北六E-包年开户时，到期资费下发
     */
    public static void dealDelayDiscnt(Map msg) {
        // 到期资费下发在TRADE_DISCNT节点下
        String delayDiscntId = (String) msg.get("delayDiscntId");
        List<Map> discntList = (List<Map>) msg.get("discntList");
        if (null == discntList) {
            discntList = new ArrayList<Map>();
        }
        if (delayDiscntId != null && delayDiscntId.length() > 0) {
            String itemid = TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID");
            msg.put("elementId", delayDiscntId);
            msg.put("itemId", itemid);
            discntList.add(TradeManagerUtils.preProductInfoByElementIdProductId(msg));
        }
        msg.put("discntList", discntList);
    }

    /**
     * 北六E-包年开户时，包年资费属性下发
     */
    public static void dealDiscntAttr(Map msg) {
        // 包年资费的部分属性传到产品的附加包节点下，然后在这了下发
        List tradeSubItem = (List<Map>) msg.get("tradeSubItem");
        if (null == tradeSubItem) {
            tradeSubItem = new ArrayList<Map>();
        }
        String bnzf = "";
        String delayType = (String) msg.get("delayType");
        String itemId = (String) msg.get("itemIdNew");
        Map newUserInfo = (Map) msg.get("newUserInfo");
        List<Map> productInfo = (List) newUserInfo.get("productInfo");
        JSONArray packageElement = (JSONArray) productInfo.get(0).get("packageElement");
        List<Map> packageElementList = JSONArray.toJavaObject(packageElement, List.class);
        if (packageElementList != null) {
            for (Map inMap : packageElementList) {
                if ("D".equals(inMap.get("elementType") + "")) {
                    // 取出请求参数中的资费的属性
                    JSONArray attrs = (JSONArray) inMap.get("serviceAttr");
                    List<Map> attrList = JSONArray.toJavaObject(attrs, List.class);
                    if (attrList != null && attrList.size() > 0) {
                        bnzf = inMap.get("elementId") + "";
                        for (int i = 0; i < attrList.size(); i++) {
                            // 资费与其对应的属性的itemid值保持一致
                            tradeSubItem.add(createTradeSubItemForDis(attrList.get(i).get("code"),
                                    attrList.get(i).get("value"), itemId));
                        }
                    }
                }
            }
        }

        // 部分包年资费属性（其他的属性由省份传入）
        if ("1".equals(delayType)) {// 续约包年
            // 山东北六E目前没有续约包年场景
        }
        if ("2".equals(delayType)) {// 续约标准资费
            tradeSubItem.add(createTradeSubItemForDis("expiredealmode", "N", itemId));
        }
        if ("3".equals(delayType)) {// 到期停机
            tradeSubItem.add(createTradeSubItemForDis("expiredealmode", "Y", itemId));
        }
        msg.put("bnzf", bnzf);// 包年时开户资费会在产品的附加包里下发一次（为了将资费的属性传进来），加此标记避免资费重复下发
        msg.put("tradeSubItem", tradeSubItem);
    }

    /**
     * 封装不带日期的资费属性
     */
    public static Map createTradeSubItemForDis(Object key, Object value, Object itemId) {
        return MapUtils.asMap("attrTypeCode", "3", "attrCode", key, "attrValue", value, "itemId", itemId);
    }

}
