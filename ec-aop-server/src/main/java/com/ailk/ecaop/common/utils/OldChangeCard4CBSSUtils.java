package com.ailk.ecaop.common.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CBSS专用工具类
 * 
 * @author Steven
 */
public class OldChangeCard4CBSSUtils {

    Map preBase(Map outMap, Map inMap, Map msg) {
        Map base = MapUtils.asMap("nextDealTag", "Z", "userDiffCode", "00", "inModeCode", "E", "tradeTypeCode", "0141");
        MapUtils.arrayPutFix("0", base, "olcomTag", "foregift", "advancePay", "cancelTag", "chkTag");
        Object tradeId = outMap.get("ordersId");
        base.put("subscribeId", tradeId);
        base.put("tradeId", tradeId);
        base.put("acceptDate", GetDateUtils.getDate());
        Map userInfo = (Map) inMap.get("userInfo");
        base.put("productId", userInfo.get("productId"));
        base.put("brandCode", userInfo.get("brandCode"));
        base.put("userId", userInfo.get("userId"));
        base.put("netTypeCode", userInfo.get("serviceClassCode"));
        Map custInfo = (Map) inMap.get("custInfo");
        base.put("custId", custInfo.get("custId"));
        base.put("usecustId", custInfo.get("custId"));
        base.put("custName", custInfo.get("custName"));
        base.put("acctId", "-1");// 先默认-1
        base.put("serinalNamber", msg.get("serialNumber"));
        ChangeCodeUtils ChangeCodeUtils = new ChangeCodeUtils();
        base.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
        base.put("execTime", base.get("acceptDate"));
        Object operFee = msg.get("cardFee");
        base.put("operFee", null == operFee ? "0" : operFee);
        base.put("CANCEL_TAG", "1");
        return base;
    }

    public Map preExt(Map inMap, Map msg) {
        Map ext = new HashMap();
        ext.put("tradeRes", preTradeRes(inMap, msg));
        ext.put("tradeItem", preTradeItem(inMap, msg));
        ext.put("tradeSubItem", preTradeSubItem(msg));
        return ext;
    }

    private Map preTradeRes(Map inMap, Map msg) {
        Map tradeRes = new HashMap();
        List<Map> item = new ArrayList<Map>();
        Map oldSimCard = new HashMap();
        oldSimCard.put("reTypeCode", "1");
        oldSimCard.put("resCode", msg.get("iccid").toString().substring(0, 19));
        oldSimCard.put("resInfo1", msg.get("imsi"));
        oldSimCard.put("resInfo4", "003");// 卡容量
        oldSimCard.put("resInfo5", msg.get("cardType"));// 卡类型
        oldSimCard.put("modifyTag", "0");
        oldSimCard.put("startDate", GetDateUtils.getDate());
        oldSimCard.put("endDate", "20501231235959");
        Map newSimCard = new HashMap();
        newSimCard.put("reTypeCode", "1");
        newSimCard.put("resCode", inMap.get("simCard"));
        newSimCard.put("resInfo1", inMap.get("imsi"));
        newSimCard.put("modifyTag", "1");
        newSimCard.put("startDate", inMap.get("startDate"));
        newSimCard.put("endDate", GetDateUtils.getDate());
        item.add(oldSimCard);
        item.add(newSimCard);
        tradeRes.put("item", item);
        return tradeRes;
    }

    private Map preTradeItem(Map inMap, Map msg) {
        Map tradeItem = new HashMap();
        List<Map> item = new ArrayList<Map>();
        item.add(LanUtils.createTradeItem("REASON_DESCRIPTION", "12"));
        item.add(LanUtils.createTradeItem("REASON_CODE_NO", "06"));
        item.add(LanUtils.createTradeItem("OLD_SIM", inMap.get("simCard").toString()));
        item.add(LanUtils.createTradeItem("SIMCARD_OPERATE_FLAG", "0"));
        item.add(LanUtils.createTradeItem("REASON_CODE", "其他原因"));
        item.add(LanUtils.createTradeItem("SIM_CARD", msg.get("iccid").toString()));
        item.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", msg.get("channelType").toString()));
        item.add(LanUtils.createTradeItem("X_EXISTFLAG", "0"));
        item.add(LanUtils.createTradeItem("RemoteTag", "1"));
        if ("1".equals(msg.get("opeType"))) {
            item.add(LanUtils.createTradeItem("IS_BK", "1"));
        }
        item.add(LanUtils.createTradeItem("RSRV_STR1", ""));
        tradeItem.put("item", item);
        return tradeItem;
    }

    private Map preTradeSubItem(Map msg) {
        Map tradeSubItem = new HashMap();
        List<Map> item = new ArrayList<Map>();
        item.add(new LanUtils().createTradeSubItem("othercontact", "NULL", msg.get("imsi").toString()));
        tradeSubItem.put("item", item);
        return tradeSubItem;
    }

    public void preSubmitMessage(Map outMap, Map inMap, Map msg) {
        Map userInfo = (Map) inMap.get("userInfo");
        inMap.put("imsi", msg.get("imsi"));
        inMap.put("simCard", inMap.get("simCard"));
        inMap.put("startDate", userInfo.get("openDate"));
        outMap.put("base", preBase(outMap, inMap, msg));
        outMap.put("ext", preExt(inMap, msg));
    }

    public void preOrderSubMessage(Map outMap, Map inMap, Map msg) {
        List<Map> subOrderSubReq = new ArrayList<Map>();
        List<Map> rspInfo = (ArrayList<Map>) inMap.get("rspInfo");
        outMap.put("provOrderId", rspInfo.get(0).get("bssOrderId"));
        outMap.put("orderNo", rspInfo.get(0).get("provOrderId"));
        List<Map> provinceOrderInfo = (ArrayList<Map>) rspInfo.get(0).get("provinceOrderInfo");
        Map subOrderSubMap = new HashMap();
        if (null != provinceOrderInfo && 0 != provinceOrderInfo.size()) {
            List<Map> feeInfo = (ArrayList<Map>) provinceOrderInfo.get(0).get("preFeeInfoRsp");
            int totalFee = Integer.valueOf(provinceOrderInfo.get(0).get("totalFee").toString());
            if (null == feeInfo || 0 == feeInfo.size()) {
                feeInfo.add(dealEmptyFeeInfo(msg));
            }
            else {
                totalFee = dealNotEmpty(feeInfo, msg, totalFee);
            }
            outMap.put("origTotalFee", totalFee);
            subOrderSubMap.put("feeInfo", feeInfo);
        }
        subOrderSubMap.put("subProvinceOrderId", inMap.get("bssOrderId"));
        subOrderSubMap.put("subOrderId", inMap.get("bssOrderId"));
        subOrderSubReq.add(subOrderSubMap);
        outMap.put("subOrderSubReq", subOrderSubReq);
        Map payInfo = (Map) msg.get("payInfo");
        if (null != msg.get("changePayType")) {
            payInfo.put("payType", ChangeCodeUtils.changeN6PayType(payInfo.get("payType").toString()));
        }
        payInfo.put("subProvinceOrderId", outMap.get("provOrderId"));
        payInfo.put("payMoney", outMap.get("origTotalFee"));
        List<Map> payInfoList = new ArrayList<Map>();
        payInfoList.add(payInfo);
        outMap.put("payInfo", payInfoList);
    }

    private void dealCommFeeInfo(Map fee) {
        fee.put("feeCategory", fee.get("feeMode"));
        fee.put("feeId", fee.get("feeTypeCode"));
        fee.put("feeDes", fee.get("feeTypeName"));
        fee.put("origFee", fee.get("oldFee"));
        fee.put("isPay", "0");
        fee.put("calculateTag", "N");
        fee.put("payTag", "1");
        fee.put("calculateId", GetSeqUtil.getSeqFromCb());
        fee.put("calculateDate", GetDateUtils.getDate());
    }

    /**
     * 处理外围系统不下发费用信息的情景
     * 
     * @param msg
     * @return
     */
    private Map dealEmptyFeeInfo(Map msg) {
        Map fee = new HashMap();
        fee.put("feeCategory", "0");
        fee.put("feeId", "1010");
        fee.put("feeDes", "[营业费用]SIM卡/USIM卡费");
        fee.put("origFee", msg.get("cardFee"));
        fee.put("realFee", msg.get("cardFee"));
        fee.put("isPay", "0");
        fee.put("calculateTag", "N");
        fee.put("payTag", "1");
        fee.put("calculateId", GetSeqUtil.getSeqFromCb());
        fee.put("calculateDate", GetDateUtils.getDate());
        return fee;
    }

    private int dealNotEmpty(List<Map> feeInfo, Map msg, int totalFee) {
        for (Map fee : feeInfo) {
            if ("0".equals(fee.get("feeMode")) && "1010".equals(fee.get("feeTypeCode"))) {
                Object f = msg.get("cardFee");
                if (null == f || "".equals(f)) {
                    f = "0";
                }
                int cardFee = Integer.valueOf(f.toString());
                int realFee = Integer.valueOf(fee.get("fee").toString());
                if (cardFee < realFee) {
                    totalFee -= (realFee - cardFee);
                    fee.put("realFee", cardFee);
                    fee.put("reliefFee", realFee - cardFee);
                    fee.put("reliefResult", "商城减免");
                }
                else {
                    fee.put("realFee", realFee);
                }
            }
            else {// 20160328优化，非卡费减免为0
                fee.put("realFee", "0");
                fee.put("reliefFee", fee.get("fee"));
                fee.put("reliefResult", "商城减免");
                totalFee = 0;
            }
            dealCommFeeInfo(fee);
        }
        return totalFee;
    }
}
