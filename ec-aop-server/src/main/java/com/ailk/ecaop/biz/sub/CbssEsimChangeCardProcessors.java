package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;

/**
 * esim成卡补换卡申请
 * 
 * @author YangZG
 *
 */
@EcRocTag("cbssEsimChangeCard")
@SuppressWarnings(value = { "unchecked", "rawtypes" })
public class CbssEsimChangeCardProcessors extends BaseAopProcessor implements ParamsAppliable {
    private final LanUtils lan = new LanUtils();
    private static final String[] PARAM_ARRAY = { "ecaop.trade.cbss.checkUserParametersMapping", "ecaop.trades.smeca.changedcardPre.paramtersmapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[2];
    @Override
    public void process(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        msg.putAll(preCheckData(msg));
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        // 调用三户接口
        exchange.setMethodCode("qctc");
        try {
            lan.preData(pmp[0], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            lan.xml2Json("ecaop.trades.cbss.threePart.template", exchange);
        } catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用三户接口失败！" + e.getMessage());
        }
        Map threepartMap = exchange.getOut().getBody(Map.class);
        List<Map> threeList = new ArrayList<Map>();
        List<Map> userInfoList = (ArrayList<Map>) threepartMap.get("userInfo");
        List<Map> custInfoList = (ArrayList<Map>) threepartMap.get("custInfo");
        List<Map> acctInfoList = (ArrayList<Map>) threepartMap.get("acctInfo");
        if (userInfoList.size() != 0) {
            threeList.add(userInfoList.get(0));
        }
        if (custInfoList.size() != 0) {
            threeList.add(custInfoList.get(0));
        }
        if (acctInfoList.size() != 0) {
            threeList.add(acctInfoList.get(0));
        }
        exchange.setMethodCode("smeca");
        msg.putAll(preCheckData(msg));
        msg.put("userInfoList", userInfoList);
        body.put("msg", msg);
        //调号卡成卡补换卡通知接口
        callNumCenterCardChangeNotify(exchange, msg);
        // 调用预提交
        msg.put("operTypeCode", "0");
        String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType", "opeSysType" };
        Map preSubmitMap = Maps.newHashMap();
        preSubmitMap.put("inModeCode", new ChangeCodeUtils().getInModeCode(exchange.getAppCode()));
        MapUtils.arrayPut(preSubmitMap, msg, copyArray);
        // 获取CB流水号
        String tradeId;
        try {
            tradeId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, preSubmitMap), "seq_trade_id");
            msg.put("tradeId", tradeId);// base节点里的tradeId是外围传入的ordersId还是获取CB的流水？
        } catch (Exception e) {
            throw new EcAopServerBizException("9999", "获取CB侧流水失败" + e.getMessage());
        }
        String eparchyCode = ChangeCodeUtils.changeEparchy(msg);
        msg.put("eparchyCode", eparchyCode);
        msg.put("ordersId", tradeId);
        msg.put("date", GetDateUtils.getDate());
        // 处理请求dealReq
        dealRequest(exchange, threeList, body, msg);
        try {
            lan.preData(pmp[1], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            lan.xml2Json("ecaop.trades.smeca.changedcardPre.template", exchange);
        } catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用预提交失败" + e.getMessage());
        }
        dealReturn(exchange);// 处理返回
    }

    /**
     * 准备预提交参数
     */
    private void dealRequest(Exchange exchange, List<Map> threeList, Map body, Map msg) {
        Map ext = new HashMap();

        // 准备base参数
        msg.put("base", preBaseData(msg, threeList, exchange.getAppCode()));
        // 准备ext参数
        ext.put("tradeItem", preTradeItem(msg));
        ext.put("tradeSubItem", preTradeSubItemData(msg));
        ext.put("tradeRes", preTradeRes(msg));
        msg.put("operTypeCode", "0");
        msg.put("ext", ext);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
    }

    private Map preCheckData(Map msg) {
        msg.put("tradeType", "1");
        msg.put("tradeTypeCode", "0141");
        msg.put("serviceClassCode", "0000");
        Map<String, Object> cardNumberInfo = new HashMap();
        cardNumberInfo.put("simCardNo", msg.get("newResourcesCode"));
        cardNumberInfo.put("serialNumber", msg.get("serialNumber"));
        msg.put("cardNumberInfo", cardNumberInfo);
        return msg;
    }

    /**
     * 
     * @param 处理返回
     */
    private void dealReturn(Exchange exchange) {
        Map out = exchange.getOut().getBody(Map.class);
        List<Map> rspInfo = (List) out.get("rspInfo");
        if (null == rspInfo || rspInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "cBSS统一预提交未返回应答信息");
        }
        Map realOut = new HashMap();
        realOut.put("essSubscribeId", (String) rspInfo.get(0).get("provOrderId"));
        Integer totalFee = 0;
        List feeList = new ArrayList();
        for (Map rspMap : rspInfo) {
            List<Map> provinceOrderInfo = (List) rspMap.get("provinceOrderInfo");
            if (null != provinceOrderInfo && provinceOrderInfo.size() > 0) {
                // 费用计算
                for (Map provinceOrder : provinceOrderInfo) {
                    totalFee = totalFee + Integer.valueOf(changeFenToLi(provinceOrder.get("totalFee")));
                    List<Map> preFeeInfoRsp = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                    if (null == preFeeInfoRsp || preFeeInfoRsp.isEmpty()) {
                        continue;
                    }
                    feeList.addAll(dealFee(preFeeInfoRsp));
                }

            }
        }
        realOut.put("feeInfo", feeList);
        realOut.put("totalFee", totalFee + "");
        exchange.getOut().setBody(realOut);
    }

    private String changeFenToLi(Object fee) {
        return null == fee || "".equals(fee + "") || "0".equals(fee + "") || "null".equals(fee + "") ? "0" : fee + "0";
    }

    private List<Map> dealFee(List<Map> feeList) {
        List<Map> retFeeList = new ArrayList<Map>();
        for (Map fee : feeList) {
            Map retFee = new HashMap();
            retFee.put("feeId", fee.get("feeTypeCode") + "");
            retFee.put("feeCategory", fee.get("feeMode") + "");
            retFee.put("feeDes", fee.get("feeTypeName") + "");
            if (null != fee.get("maxDerateFee"))
                retFee.put("maxRelief", changeFenToLi(fee.get("maxDerateFee")));// 非必返maxDerateFee
            retFee.put("origFee", changeFenToLi(fee.get("fee")));
            retFeeList.add(retFee);
        }
        return retFeeList;
    }

    /**
     * 
     * @param 拼装TRADE_RES节点
     * @return
     */
    private Map preTradeRes(Map msg) {
        try {
            Map tradeRes = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            List<Map> simCardDatas = (List<Map>) msg.get("simCardData");
            List<Map> userInfo = (List<Map>) msg.get("userInfoList");

            String oldResourcesCode = (String) msg.get("oldResourcesCode");
            Map oldCardMap = new HashMap();
            for (Map userMap : userInfo) {
                List<Map> resInfoList = (List<Map>) userMap.get("resInfo");
                for (Map resInfo : resInfoList) {
                    if (oldResourcesCode.equals((String) resInfo.get("resCode"))) {
                        oldCardMap.put("resInfo1", resInfo.get("resInfo1"));
                        oldCardMap.put("resInfo2", resInfo.get("resInfo2"));
                        oldCardMap.put("resInfo3", resInfo.get("resInfo3"));
                        oldCardMap.put("resInfo4", resInfo.get("resInfo4"));
                        oldCardMap.put("resInfo5", resInfo.get("resInfo5"));
                        oldCardMap.put("reTypeCode", resInfo.get("resTypeCode"));
                        oldCardMap.put("resCode", resInfo.get("resCode"));
                        oldCardMap.put("startDate", resInfo.get("startDate"));
                        oldCardMap.put("xDatatype", "NULL");
                        oldCardMap.put("modifyTag", "1");// 1修改
                        oldCardMap.put("resInfo6", "1234-12345678");
                        oldCardMap.put("endDate", msg.get("date"));
                        itemList.add(oldCardMap);// 传入旧卡失效信息
                    } 
                }
            }
            if (IsEmptyUtils.isEmpty(oldCardMap)) {
                throw new EcAopServerBizException("9999", ",三户未返回与传入旧卡卡号相匹配的旧卡卡号信息，请检查！");
            }
            if (null != simCardDatas && simCardDatas.size() > 0) { // 卡信息传了为成卡
                for (Map simCardData : simCardDatas) {
                    Map item = new HashMap();
                    item.put("xDatatype", "NULL");
                    item.put("modifyTag", "0");// 0新增
                    item.put("reTypeCode", "1");
                    item.put("resCode", simCardData.get("eiccid"));
                    item.put("resInfo1", simCardData.get("eimsi"));
                    item.put("resInfo2", "notRemote");
                    item.put("resInfo4", "1000101");
                    item.put("resInfo5", ChangeCodeUtils.changeCardType(simCardData.get("materialCode"))); // 号卡中心卡类型转换
                    item.put("resInfo6", "1234-12345678");// CB说写死
                    item.put("startDate", msg.get("date"));
                    item.put("endDate", "20501231235959");
                    itemList.add(item);
                }
            } else {// 白卡插一条RES_TYPE_CODE为1 的数据
                Map item = new HashMap();
                item.put("xDatatype", "NULL");
                item.put("reTypeCode", "1");
                item.put("resCode", "89860");
                item.put("modifyTag", "0");
                item.put("startDate", msg.get("date"));
                item.put("endDate", "20501231235959");
                itemList.add(item);
            }
            tradeRes.put("item", itemList);
            return tradeRes;
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_RES节点报错" + e.getMessage());
        }
    }

    /**
     * 
     * @param 准备TRADE_ITEM
     * @return
     */
    private Map preTradeItem(Map msg) {
        Map tradeItem = new HashMap();
        List<Map> items = new ArrayList<Map>();
        String cardType = (String) msg.get("cardType");
        if ("1".equals(cardType)) {
            items.add(LanUtils.createTradeItem("NOT_REMOTE", "1"));
            items.add(LanUtils.createTradeItem("REASON_CODE", "卡丢失"));
            items.add(LanUtils.createTradeItem("REASON_CODE_ID", "101"));
            items.add(LanUtils.createTradeItem("OLD_SIM", msg.get("oldResourcesCode")));
            items.add(LanUtils.createTradeItem("SIM_CARD", msg.get("newResourcesCode")));
            items.add(LanUtils.createTradeItem("IS_BK", "1"));
        }
        tradeItem.put("item", items);
        return tradeItem;
    }

    /**
     * 
     * @param 准备TRADE_SUB_ITEM
     * @return
     */
    private Map preTradeSubItemData(Map msg) {
        Map tradeSubItem = new HashMap();
        List<Map> items = new ArrayList<Map>();
        String cardType = (String) msg.get("cardType");
        List<Map> userInfo = (List<Map>) msg.get("userInfoList");
        String userId = null;
        for (Map userMap : userInfo) {
            userId = (String) userMap.get("userId");
        }
        if ("1".equals(cardType)) {
            if (!IsEmptyUtils.isEmpty(msg.get("simCardData"))) {
                List<Map> simCardData = (List<Map>) msg.get("simCardData");
                if (null != userId) {
                    for (Map simCard : simCardData) {
                        if (!IsEmptyUtils.isEmpty(simCard.get("eid"))) {
                            items.add(lan.createTradeSubItem("EID", simCard.get("eid"), userId));
                        }
                        if (!IsEmptyUtils.isEmpty(simCard.get("imei"))) {
                            items.add(lan.createTradeSubItem("IMEI", simCard.get("imei"), userId));
                        }
                        if (!IsEmptyUtils.isEmpty(simCard.get("terminalType"))) {
                            items.add(lan.createTradeSubItem("TERMINAL_TYPE", simCard.get("terminalType"), userId));
                        }
                        items.add(lan.createTradeSubItem("CARD_TYPE", "1", userId));
                        items.add(lan.createTradeSubItem("NEW_CARD_DATA", simCard.get("ki"), userId));
                        items.add(lan.createTradeSubItem("NUMERICAL_SELECTION", "2", userId));
                    }
                }
            } else {
                throw new EcAopServerBizException("9999", "卡类型为eSIM卡时，simCardData必传");
            }
        }
        tradeSubItem.put("item", items);
        return tradeSubItem;
    }

    /**
     * 
     * @param 拼装BASE节点
     * @return
     */
    private Map preBaseData(Map msg, List<Map> threeList, String appCode) {
        try {
            Map base = new HashMap();
            base.putAll(threeList.get(0));
            base.putAll(threeList.get(1));
            base.putAll(threeList.get(2));
            base.put("areaCode", ChangeCodeUtils.changeEparchy(MapUtils.asMap("province", msg.get("province"), "city", msg.get("city"))));
            base.put("subscribeId", msg.get("tradeId"));
            base.put("tradeLcuName", "TCS_ChangeResourceReg");
            base.put("infoTag", "11111100001000000000100000                ");
            base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
            base.put("tradeId", msg.get("tradeId"));
            base.put("tradeTypeCode", "0141");
            base.put("execTime", "20141117151815");// ////////////
            base.put("nextDealTag", "Z");
            base.put("olcomTag", "0");
            base.put("foregift", "0");
            base.put("rightCode", "csBuysimcardHKTrade");
            base.put("chkTag", "0");
            base.put("cancelTag", "0");
            base.put("operFee", "0");
            base.put("tradeTagSet", "00120000000000000000");
            base.put("blackUserTag", "0");
            base.put("netTypeCode", "0050");
            base.put("execTime", RDate.currentTimeStr("yyyyMMddHHmmss"));
            base.put("tradeDepartId", msg.get("channelId"));
            base.put("acceptDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
            base.put("acceptMonth", RDate.currentTimeStr("MM"));
            base.put("startAcycId", RDate.currentTimeStr("yyyyMM"));
            base.put("termIp", RDate.currentTimeStr("127.0.0.1"));
            base.put("tradeJudgeOweTag", "2");
            base.put("tradeStatus", "2");
            base.put("attrTypeCode", "0");
            base.put("tradeAttr", "1");
            base.put("tradeStaffId", msg.get("operatorId"));
            base.put("serinalNamber", msg.get("serialNumber"));
            base.put("routeEparchy", threeList.get(0).get("xEparchyCode"));
            return base;
        } catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装base节点报错");
        }
    }

    /**
     * 调号卡成卡补换卡通知接口
     * 
     */
    public void callNumCenterCardChangeNotify(Exchange exchange, Map msg) throws Exception {
        Map preDataMap = NumFaceHeadHelper.creatHead();
        Map CardInfoReq = new HashMap();
        CardInfoReq.put("STAFF_ID", msg.get("operatorId"));
        CardInfoReq.put("PROVINCE_CODE", msg.get("province"));
        CardInfoReq.put("CITY_CODE", msg.get("city"));
        CardInfoReq.put("DISTRICT_CODE", msg.get("district"));
        CardInfoReq.put("CHANNEL_ID", msg.get("channelId"));
        CardInfoReq.put("CHANNEL_TYPE", msg.get("channelType"));
        CardInfoReq.put("SYS_CODE", "5600");// 操作系统编码
        CardInfoReq.put("REQ_NO", msg.get("ordersId"));
        CardInfoReq.put("ICCID_NEW", msg.get("newResourcesCode"));
        CardInfoReq.put("ICCID_OLD", msg.get("oldResourcesCode"));
        CardInfoReq.put("SERIAL_NUMBER", msg.get("serialNumber"));
        CardInfoReq.put("CHANGE_TYPE", "4");// 1:旧成卡->新白卡 2:旧成卡->新成卡 3:旧白卡->新成卡 4:旧esim成卡->新esim成卡
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

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
