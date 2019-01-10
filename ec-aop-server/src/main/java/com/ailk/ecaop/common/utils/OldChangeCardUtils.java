package com.ailk.ecaop.common.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

public class OldChangeCardUtils {

    Map preBase(Map outMap, Map inMap, Map msg) {
        Map base = MapUtils.asMap("nextDealTag", "Z", "userDiffCode", "00", "inModeCode", "E", "tradeTypeCode", "0141");
        if (null != outMap.get("inModeCode")) {
            base.put("inModeCode", outMap.get("inModeCode"));
        }
        MapUtils.arrayPutFix("0", base, "olcomTag", "foregift", "advancePay", "cancelTag", "chkTag");
        Object tradeId = outMap.get("ordersId");
        base.put("subscribeId", tradeId);
        base.put("tradeId", tradeId);
        base.put("acceptDate", GetDateUtils.getDate());
        Map userInfo = ((ArrayList<Map>) inMap.get("userInfo")).get(0);
        base.put("productId", userInfo.get("productId"));
        base.put("brandCode", userInfo.get("brandCode"));
        base.put("feeState", "0");
        base.put("userId", userInfo.get("userId"));
        base.put("netTypeCode", userInfo.get("serviceClassCode"));
        Map custInfo = ((ArrayList<Map>) inMap.get("custInfo")).get(0);
        base.put("custId", custInfo.get("custId"));
        base.put("usecustId", custInfo.get("custId"));
        base.put("custName", custInfo.get("custName"));
        base.put("acctId", "-1");// 先默认-1
        base.put("serinalNamber", msg.get("serialNumber"));
        base.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
        base.put("execTime", base.get("acceptDate"));
        Object operFee = msg.get("cardFee");
        base.put("operFee", null == operFee || "".equals(operFee) ? "0" : operFee);
        base.put("CANCEL_TAG", "1");
        return base;
    }

    public Map preExt(Map inMap, Map msg, String appCode) {
        Map ext = new HashMap();
        ext.put("tradeRes", preTradeRes(inMap, msg));
        ext.put("tradeItem", preTradeItem(inMap, msg, appCode));
        ext.put("tradeSubItem", preTradeSubItem(inMap, msg));
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

    private Map preTradeItem(Map inMap, Map msg, String appCode) {
        Map tradeItem = new HashMap();
        List<Map> item = new ArrayList<Map>();
        item.add(LanUtils.createTradeItem("REASON_DESCRIPTION", "12"));
        item.add(LanUtils.createTradeItem("REASON_CODE_NO", "06"));
        item.add(LanUtils.createTradeItem("OLD_SIM", inMap.get("simCard").toString()));
        // if ("jspre".equals(inMap.get("appCode")))
        // {
        item.add(LanUtils.createTradeItem("CARD_DATA", msg.get("cardData")));
        item.add(LanUtils.createTradeItem("PROCID", msg.get("procId")));
        // }
        item.add(LanUtils.createTradeItem("SIMCARD_OPERATE_FLAG", "0"));
        item.add(LanUtils.createTradeItem("REASON_CODE", "其他原因"));
        item.add(LanUtils.createTradeItem("SIM_CARD", msg.get("iccid").toString()));

        item.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", msg.get("channelType").toString()));
        item.add(LanUtils.createTradeItem("X_EXISTFLAG", "0"));
        item.add(LanUtils.createTradeItem("RemoteTag", "1"));
        // ------------------RLN2017062200058-关于AOP补换卡、终端销售接口支持行销支付的需求(start)---------
        item.add(LanUtils.createTradeItem("MARKING_APP", "1".equals(msg.get("markingTag")) ? "1" : "0"));
        item.add(LanUtils.createTradeItem("MarketingChannelType",
                "0".equals(msg.get("saleModType")) ? "01" : "1".equals(msg.get("saleModType")) ? "02" : ""));
        // ------------------RLN2017062200058-关于AOP补换卡、终端销售接口支持行销支付的需求(end)-----------
        if ("1".equals(msg.get("opeType"))) {
            item.add(LanUtils.createTradeItem("IS_BK", "1"));
        }
        if (new ChangeCodeUtils().isWOPre(appCode)) {
            item.add(LanUtils.createTradeItem("WORK_TRADE_ID", msg.get("ordersID")));
        }
        item.add(LanUtils.createTradeItem("RSRV_STR1", ""));
        tradeItem.put("item", item);
        return tradeItem;
    }

    private Map preTradeSubItem(Map inMap, Map msg) {
        Map userInfo = ((ArrayList<Map>) inMap.get("userInfo")).get(0);
        Map tradeSubItem = new HashMap();
        List<Map> item = new ArrayList<Map>();
        // FIXME CHANGE_2 北六3G补换卡时,下发带开始结束时间的trade_sub_item节点 by wangmc 20180724
        if ("1".equals(msg.get("isN6ChangeCard"))) {
            item.add(LanUtils.createTradeSubItemAll("0", "othercontact", "NULL", userInfo.get("userId").toString(),
                    GetDateUtils.getDate(), "20501231235959"));
        }
        else {
            item.add(new LanUtils().createTradeSubItem("othercontact", "NULL", userInfo.get("userId").toString()));
        }
        tradeSubItem.put("item", item);
        return tradeSubItem;
    }

    public void preSubmitMessage(Map outMap, Map inMap, Map msg, String appCode) {
        Map userInfo = ((ArrayList<Map>) inMap.get("userInfo")).get(0);
        List<Map> resInfo = (ArrayList<Map>) userInfo.get("resInfo");
        for (int i = 0; i < resInfo.size(); i++) {
            if ("1".equals(resInfo.get(i).get("resTypeCode"))) {
                Object usim = resInfo.get(i).get("resCode");
                if (null == usim || "".equals(usim)) {
                    throw new EcAopServerBizException("9999", "获取原USIM卡信息失败");
                }
                Object imsi = resInfo.get(i).get("resInfo1");
                Object startDate = resInfo.get(i).get("startDate");
                inMap.put("simCard", usim);
                inMap.put("imsi", imsi);
                inMap.put("startDate", startDate);
            }
        }
        outMap.put("base", preBase(outMap, inMap, msg));
        outMap.put("ext", preExt(inMap, msg, appCode));
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
        if (null == msg.get("payInfo")) {
            throw new EcAopServerBizException("9999", "获取不到'payInfo'节点信息！");
        }
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
