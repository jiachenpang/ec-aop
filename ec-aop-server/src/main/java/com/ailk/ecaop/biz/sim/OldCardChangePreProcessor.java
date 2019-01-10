package com.ailk.ecaop.biz.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.OldChangeCardUtils;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;

/**
 * 补换卡收费预提交
 * @auther Zhao Zhengchang
 * @create 2016_03_23
 */
@EcRocTag("oldCardChangePre")
public class OldCardChangePreProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        LanUtils lan = new LanUtils();

        if ("2".equals(msg.get("opeSysType"))) {
            // 调用三户接口
            String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType",
                    "opeSysType" };
            Map threePartMap = MapUtils.asMap("getMode", "101001101010001001", "serialNumber", msg.get("serialNumber"),
                    "tradeTypeCode", "9999");
            MapUtils.arrayPut(threePartMap, msg, copyArray);
            Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
            lan.preData("ecaop.trade.cbss.checkUserParametersMapping", threePartExchange);
            CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
            Map threePartRetMap = threePartExchange.getOut().getBody(Map.class);
            // 通过线上渠道发起的补换卡以及异地补换卡调用号卡中心成卡补换卡通知接口需求
            callNumCenterNotifyCardChangeResult(exchange, threePartRetMap, msg);
            // 调用预提交接口
            Map preSubmitMap = Maps.newHashMap();
            preSubmitMap.put("inModeCode", new ChangeCodeUtils().getInModeCode(exchange.getAppCode()));
            MapUtils.arrayPut(preSubmitMap, msg, copyArray);
            String tradeId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, preSubmitMap), "seq_trade_id");
            preSubmitMap.put("ordersId", tradeId);
            preSubmitMap.put("operTypeCode", "0");
            preSubmitMap.put("serinalNamber", msg.get("serialNumber"));
            threePartRetMap.put("appCode", exchange.getAppCode());
            new OldChangeCardUtils().preSubmitMessage(preSubmitMap, threePartRetMap, msg, exchange.getAppCode());// 由三户信息--准备base和EXT节点信息等
            msg.putAll(preSubmitMap);
            body.put("msg", msg);
            exchange.getIn().setBody(body);
            lan.preData("ecaop.trades.sccc.cancelPre.paramtersmapping", exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            lan.xml2Json("ecaop.trades.sccc.cancelPre.template", exchange);
            Map message = exchange.getOut().getBody(Map.class);
            if (null != message.get("rspInfo")) {
                List<Map> rspInfoList = (List<Map>) message.get("rspInfo");
                Map rspInfo = rspInfoList.get(0);
                // 处理返回参数
                Map result = dealReturn(rspInfo);
                exchange.getOut().setBody(result);
            }
        }
        else {
            String province = msg.get("province").toString();
            if ("18|17|76|11|97|91".contains(province)) {
                // 调用三户接口
                String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType" };
                Map threePartMap = MapUtils.asMap("getMode", "00000", "serialNumber", msg.get("serialNumber"),
                        "tradeTypeCode", "0141");
                MapUtils.arrayPut(threePartMap, msg, copyArray);
                Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
                lan.preData("ecaop.trade.cbss.checkUserParametersMapping", threePartExchange);
                CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.UsrForNorthSer" + "." + msg.get("province"));// 通了
                lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
                Map threePartRetMap = threePartExchange.getOut().getBody(Map.class);
                // 调用预提交接口
                // Map preSubmitMap = Maps.newHashMap();
                // MapUtils.arrayPut(preSubmitMap, msg, copyArray);
                String tradeId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_trade_id");
                msg.put("ordersId", tradeId);
                msg.put("operTypeCode", "0");
                // preSubmitMap.put("serinalNamber", msg.get("serialNumber"));
                preSubmitMessage(threePartRetMap, msg);
                // Exchange preSubmitExchange = ExchangeUtils.ofCopy(exchange, preSubmitMap);

                lan.preData("ecaop.trades.sccc.cancelPre.paramtersmapping", exchange);
                CallEngine.wsCall(exchange, "ecaop.comm.conf.url.OrdForNorthSer" + "." + msg.get("province"));
                lan.xml2Json("ecaop.trades.sccc.cancelPre.template", exchange);
                Map message = exchange.getOut().getBody(Map.class);
                if (null != message.get("rspInfo")) {
                    List<Map> rspInfoList = (List<Map>) message.get("rspInfo");
                    Map rspInfo = rspInfoList.get(0);
                    // 处理返回参数
                    Map result = dealReturn(rspInfo);
                    exchange.getOut().setBody(result);
                }

            }
            else {
                // 白卡割接需求改动: by wangmc 20171121
                // 分步补换卡调用下游T2000610接口时,报文头ProcID要与写卡数据查询时保持一致,在3G预提交时入库,正式提交时获取
                String simCardSwitch = EcAopConfigLoader.getStr("ecaop.global.param.simcard.province");
                if (simCardSwitch.contains(String.valueOf(msg.get("province")))) {
                    msg.put("simCardSwitch", "1");// 标记为1走号卡中心获取白卡信息
                }
                lan.preData("ecaop.param.mapping.smosa", exchange);
                CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
                lan.xml2Json4ONS("ecaop.trades.smosa.changedcardPre.template", exchange);// http协议
            }
        }

    }

    private void preSubmitMessage(Map inMap, Map msg) {
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
        msg.put("base", preBase(msg, inMap, new HashMap(msg)));
        msg.put("ext", preExt(inMap, msg));
    }

    /**
     * 处理返回信息
     */
    private Map dealReturn(Map rspInfo) {
        Map ret = new HashMap();
        ret.put("essSubscribeId", isNull(rspInfo.get("bssOrderId").toString()));
        if (null != rspInfo.get("provinceOrderInfo")) {
            List<Map> provinceOrderInfoList = (List<Map>) rspInfo.get("provinceOrderInfo");
            for (Map provinceOrderInfo : provinceOrderInfoList) {
                if (null != provinceOrderInfo.get("preFeeInfoRsp")) {
                    List<Map> feeInfo = (List<Map>) provinceOrderInfo.get("preFeeInfoRsp");
                    List<Map> newFeeInfo = new ArrayList<Map>();
                    if (0 != feeInfo.size()) {
                        for (Map fee : feeInfo) {
                            Map newFee = dealFee(fee);
                            newFeeInfo.add(newFee);
                        }
                        ret.put("feeInfo", newFeeInfo);
                    }
                }
                String totalFee = (String) provinceOrderInfo.get("totalFee");
                ret.put("totalFee", totalFee);
            }
        }
        return ret;
    }

    /**
     * 费用的处理
     */
    private Map dealFee(Map oldFeeInfo) {
        Map feeInfo = new HashMap();
        feeInfo.put("feeId", isNull((String) oldFeeInfo.get("feeTypeCode")));
        feeInfo.put("feeCategory", isNull((String) oldFeeInfo.get("feeMode")));
        feeInfo.put("feeDes", isNull((String) oldFeeInfo.get("feeTypeName")));
        feeInfo.put("maxRelief", isNull((String) oldFeeInfo.get("maxDerateFee")));
        feeInfo.put("origFee", isNull(oldFeeInfo.get("fee").toString()));
        return feeInfo;
    }

    // 判断是否为空
    private String isNull(String string) {
        return null == string ? "" : string;
    }

    /**
     * 后面使用outMap，inMap和msg仅仅为了取值赋值
     * @param outMap
     * @param inMap
     * @param msg
     */
    /* private void preSubmitMessage(Map outMap, Map inMap, Map msg) {
     * Map userInfo = ((ArrayList<Map>) inMap.get("userInfo")).get(0);
     * List<Map> resInfo = (ArrayList<Map>) userInfo.get("resInfo");
     * for (int i = 0; i < resInfo.size(); i++) {
     * if ("1".equals(resInfo.get(i).get("resTypeCode"))) {
     * Object usim = resInfo.get(i).get("resCode");
     * if (null == usim || "".equals(usim)) {
     * throw new EcAopServerBizException("9999", "获取原USIM卡信息失败");
     * }
     * Object imsi = resInfo.get(i).get("resInfo1");
     * Object startDate = resInfo.get(i).get("startDate");
     * inMap.put("simCard", usim);
     * inMap.put("imsi", imsi);
     * inMap.put("startDate", startDate);
     * }
     * }
     * outMap.put("base", preBase(outMap, inMap, msg));
     * outMap.put("ext", preExt(inMap, msg));
     * } */

    /**
     * 返回Map base
     * @param outMap
     * @param inMap
     * @param msg
     * @return
     */
    private Map preBase(Map outMap, Map inMap, Map msg) {
        Map base = new HashMap();
        Object tradeId = outMap.get("ordersId");
        base.put("subscribeId", tradeId);
        base.put("tradeId", tradeId);
        base.put("acceptDate", GetDateUtils.getDate());
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("inModeCode", "E");
        base.put("tradeTypeCode", "0141");
        Map userInfo = ((ArrayList<Map>) inMap.get("userInfo")).get(0);
        base.put("productId", userInfo.get("productId"));
        base.put("brandCode", userInfo.get("brandCode"));
        base.put("userId", userInfo.get("userId"));
        Map custInfo = ((ArrayList<Map>) inMap.get("custInfo")).get(0);
        base.put("custId", custInfo.get("custId"));
        base.put("usecustId", custInfo.get("custId"));
        Map acctInfo = ((ArrayList<Map>) inMap.get("acctInfo")).get(0);
        base.put("acctId", acctInfo.get("acctId"));
        base.put("userDiffCode", "00");
        base.put("netTypeCode", userInfo.get("serviceClassCode"));
        base.put("serinalNamber", msg.get("serialNumber"));
        base.put("custName", custInfo.get("custName"));
        base.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
        base.put("execTime", base.get("acceptDate"));
        Object operFee = msg.get("cardFee");
        base.put("operFee", null == operFee ? "0" : operFee);
        base.put("foregift", "0");
        base.put("advancePay", "0");
        base.put("cancelTag", "0");
        base.put("chkTag", "0");
        base.put("CANCEL_TAG", "1");
        return base;
    }

    /**
     * 返回Map ext
     * @param outMap
     * @param inMap
     * @param msg
     * @return
     */
    private Map preExt(Map inMap, Map msg) {
        Map ext = new HashMap();
        ext.put("tradeRes", preTradeRes(inMap, msg));
        ext.put("tradeItem", preTradeItem(inMap, msg));
        ext.put("tradeSubItem", preTradeSubItem(inMap, msg));
        return ext;
    }

    private Map preTradeRes(Map inMap, Map msg) {
        Map tradeRes = new HashMap();
        List<Map> item = new ArrayList<Map>();
        Map oldSimCard = new HashMap();
        oldSimCard.put("reTypeCode", "1");
        oldSimCard.put("resCode", ((String) msg.get("iccid")).substring(0, 19));// 2017-11-01截取19位的resCode
        oldSimCard.put("resInfo1", msg.get("imsi"));
        oldSimCard.put("resInfo4", "003");
        oldSimCard.put("resInfo5", "003");
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
        item.add(LanUtils.createTradeItem("CARD_DATA", msg.get("cardData")));
        item.add(LanUtils.createTradeItem("SIMCARD_OPERATE_FLAG", "0"));
        item.add(LanUtils.createTradeItem("REASON_CODE", "其他原因"));
        item.add(LanUtils.createTradeItem("SIM_CARD", msg.get("iccid").toString()));
        item.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", msg.get("channelType").toString()));
        item.add(LanUtils.createTradeItem("X_EXISTFLAG", "0"));
        item.add(LanUtils.createTradeItem("RemoteTag", "1"));
        // ------------------RLN2017062200058-关于AOP补换卡、终端销售接口支持行销支付的需求,修改北六侧(start)---------
        item.add(LanUtils.createTradeItem("MARKING_APP", "1".equals(msg.get("markingTag")) ? "1" : "0"));
        item.add(LanUtils.createTradeItem("MarketingChannelType",
                "0".equals(msg.get("saleModType")) ? "01" : "1".equals(msg.get("saleModType")) ? "02" : ""));
        // ------------------RLN2017062200058-关于AOP补换卡、终端销售接口支持行销支付的需求,修改北六侧(end)-----------
        if ("1".equals(msg.get("opeType"))) {
            item.add(LanUtils.createTradeItem("IS_BK", "1"));
        }
        item.add(LanUtils.createTradeItem("RSRV_STR1", ""));
        tradeItem.put("item", item);
        return tradeItem;
    }

    private Map preTradeSubItem(Map inMap, Map msg) {
        Map userInfo = ((ArrayList<Map>) inMap.get("userInfo")).get(0);
        Map tradeSubItem = new HashMap();
        List<Map> item = new ArrayList<Map>();
        item.add(new LanUtils().createTradeSubItem("othercontact", "NULL", userInfo.get("userId").toString()));
        tradeSubItem.put("item", item);
        return tradeSubItem;
    }

    /**
     * 20170609
     * by shenzy
     * 根据三户接口返回参数判断是否调用号卡中心成卡补换卡通知接口
     * @param msg
     * @throws Exception
     */
    public void callNumCenterNotifyCardChangeResult(Exchange exchange, Map input, Map msg) throws Exception {
        List<Map> userInfo = (List<Map>) input.get("userInfo");
        List<Map> resInfo = (List<Map>) userInfo.get(0).get("resInfo");
        List<Map> attrInfo = (List<Map>) userInfo.get(0).get("attrInfo");
        LanUtils lan = new LanUtils();
        if (IsEmptyUtils.isEmpty(resInfo))
            return;
        for (Map res : resInfo) {
            if (!"1".equals(res.get("resTypeCode")))
                continue;
            String startDate = (String) res.get("startDate");
            String endDate = (String) res.get("endDate");
            String resInfo2 = (String) res.get("resInfo2");
            msg.put("oldIccid", res.get("resCode"));
            // 三个条件判断当前使用的卡是否为裸成卡,是则调号卡中心
            if (Long.parseLong(startDate) < Long.parseLong(GetDateUtils.getDate())
                    && Long.parseLong(GetDateUtils.getDate()) < Long.parseLong(endDate) && "notRemote".equals(resInfo2)) {
                callNumCen(exchange, msg, "1");
                return;
            }
        }
        /* 判断三户 USER_INFO下的ATTR_INFO下面有没有IS_SCHOOL_BAT这个属性,
         * 有则调cbss的qryRemoteCardInfo接口,如果能查到记录,则说明是白卡,不调号卡,
         * 若查不到记录则说明是成卡,调号卡 */
        for (Map attr : attrInfo) {
            if (!"IS_SCHOOL_BAT".equals(attr.get("attrCode")))
                continue;
            msg.put("emptyCardId", msg.get("oldIccid"));
            Exchange qryExchange = ExchangeUtils.ofCopy(exchange, msg);
            lan.preData("ecaop.masb.smosa.qryRemoteCardInfo.ParametersMapping", qryExchange);
            CallEngine.wsCall(qryExchange, "ecaop.comm.conf.url.cbss.services.SimForNorthSer");
            lan.xml2Json("ecaop.trades.smosa.qryRemoteCardInfo.template", qryExchange);
            Map body = (Map) qryExchange.getOut().getBody();
            if (!IsEmptyUtils.isEmpty((Map) body.get("remoteCardInfo"))) // 若有记录,则为白卡,不调2.0
                return;
            callNumCen(exchange, msg, "1");
        }

    }

    /**
     * 调号卡成卡补换卡通知接口
     * @throws Exception
     */
    public void callNumCen(Exchange exchange, Map msg, String changeTag) throws Exception {
        Map preDataMap = NumFaceHeadHelper.creatHead();
        Map CardInfoReq = new HashMap();
        CardInfoReq.put("STAFF_ID", msg.get("operatorId"));
        CardInfoReq.put("PROVINCE_CODE", msg.get("province"));
        CardInfoReq.put("CITY_CODE", msg.get("city"));
        CardInfoReq.put("DISTRICT_CODE", msg.get("district"));
        CardInfoReq.put("CHANNEL_ID", msg.get("channelId"));
        CardInfoReq.put("CHANNEL_TYPE", msg.get("channelType"));
        CardInfoReq.put("SYS_CODE", "5600");// 操作系统编码
        CardInfoReq.put("REQ_NO", msg.get("ordersID"));
        // CardInfoReq.put("ICCID_NEW", msg.get("iccid"));
        CardInfoReq.put("ICCID_OLD", msg.get("oldIccid"));
        CardInfoReq.put("SERIAL_NUMBER", msg.get("serialNumber"));
        CardInfoReq.put("CHANGE_TYPE", changeTag);// 1:旧成卡->新白卡 2:旧成卡->新成卡 3:旧白卡->新成卡 4:旧esim成卡->新esim成卡
        CardInfoReq.put("TRADE_TYPE", "09");
        preDataMap.put("UNI_BSS_BODY", MapUtils.asMap("NOTIFY_CARD_CHANGE_RESULT_REQ", CardInfoReq));
        Exchange qryExchange = ExchangeUtils.ofCopy(preDataMap, exchange);
        CallEngine.numCenterCall(qryExchange, "ecaop.comm.conf.url.numbercenter.notifyCardChangeResult");
        Map retCardInfo = (Map) JSON.parse(qryExchange.getOut().getBody().toString());
        Map UNI_BSS_BODY = (Map) retCardInfo.get("UNI_BSS_BODY");
        if (null == UNI_BSS_BODY || UNI_BSS_BODY.isEmpty()) {
            throw new EcAopServerBizException("9999", "号卡中心未返回信息");
        }
        Map NOTIFY_CARD_CHANGE_RESULT_RSP = (Map) UNI_BSS_BODY.get("NOTIFY_CARD_CHANGE_RESULT_RSP");
        if (!"0000".equals(NOTIFY_CARD_CHANGE_RESULT_RSP.get("RESP_CODE"))) {
            throw new EcAopServerBizException("9999", NOTIFY_CARD_CHANGE_RESULT_RSP.get("RESP_DESC").toString());
        }
    }
}
