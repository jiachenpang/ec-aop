package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("cancelUser")
public class CancelUserProcessors extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.trades.sccs.cancelPre.paramtersmapping",
        "ecaop.trades.sccs.cancel.paramtersmapping", "ecaop.masb.chph.gifa.ParametersMapping",
    "ecaop.trade.cbss.checkUserParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];
    LanUtils lan = new LanUtils();

    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = (Map) body.get("msg");
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        // 准备参数信息
        String tradeId = (String) GetSeqUtil.getSeqFromCb(pmp[2], ExchangeUtils.ofCopy(exchange, msg),
                "seq_trade_id", 1).get(0);
        String nextStartDate = GetDateUtils.getSpecifyDateTime(1, 1,
                GetDateUtils.transDate(GetDateUtils.getNextMonthFirstDayFormat(), 19), 9);
        msg.put("tradeId", tradeId);
        msg.put("nextStartDate", nextStartDate);
        Map preDataMap = preDataForPreCommit(exchange, msg);
        MapUtils.arrayPut(preDataMap, msg, MagicNumber.COPYARRAY);
        Exchange presubExchange = ExchangeUtils.ofCopy(exchange, preDataMap);
        // 调预提交接口
        lan.preData(pmp[0], presubExchange);
        CallEngine.wsCall(presubExchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.sccs.cancelPre.template", presubExchange);

        Map retMap = presubExchange.getOut().getBody(Map.class);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (null == rspInfoList || 0 == rspInfoList.size()) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }
        // 这两个信息要最终返回给接入系统，如不在此设置，可能会被后面的返回覆盖
        Map rspInfo = rspInfoList.get(0);
        String provOrderId = rspInfo.get("provOrderId").toString();
        String bssOrderId = rspInfo.get("bssOrderId").toString();
        // 调用cBSS的提交接口
        Map orderMap = preOrderSubParam(rspInfo);
        orderMap.put("orderNo", provOrderId);
        orderMap.put("provOrderId", bssOrderId);
        msg.putAll(orderMap);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        lan.preData(pmp[1], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        lan.xml2Json("ecaop.trades.sccs.cancel.template", exchange);

        //当为立即销户，还需要调用能力平台的号码冷冻接口
        if ("2".equals(msg.get("destroyUserTag"))) {
            callRelSelectionNum(exchange, msg, provOrderId);
        }
        Map outMap = new HashMap();
        //Map orderOutMap = exchange.getOut().getBody(Map.class);
        // 受理成功时，返回总部和省份订单
        outMap.put("provOrderId", provOrderId);
        outMap.put("bssOrderId", bssOrderId);
        exchange.getOut().setBody(outMap);
    }

    public void callRelSelectionNum(Exchange exchange, Map msg, String provOrderId) throws Exception {
        Map REQ = createREQ(msg);
        REQ.put("DESTROY_TYPE", "03");
        List list = new ArrayList<Map>();
        Map resource = MapUtils.asMap("SERIAL_NUMBER", msg.get("serialNumber"), "REQ_NO", provOrderId, "RECOVRY_FLAG",
                "1");
        list.add(resource);
        REQ.put("RESOURCES_INFO", list);
        Map req = createHeadAndAttached(exchange.getAppkey());
        req.put("UNI_BSS_BODY", MapUtils.asMap("LOGOUT_NUM_REQ", REQ));
        exchange.getIn().setBody(req);
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.numbercenter.logoutNum");
        dealNumberCenterReturn(exchange, "REL_SELECTION_NUM_RSP", "号码冷冻");
    }

    /**
     * 处理号卡中心返回报文
     *
     * @param exchange
     * @param rspKey XXX_XXX_RSP
     * @param kind 接口类型（中文名）
     */
    public void dealNumberCenterReturn(Exchange exchange, String rspKey, String kind) {
        String rsp = exchange.getOut().getBody().toString();
        System.out.println("号卡中心返回toString：" + rsp);
        Map out = (Map) JSON.parse(rsp);
        Map UNI_BSS_HEAD = (Map) out.get("UNI_BSS_HEAD");
        if (null != UNI_BSS_HEAD) {
            String code = (String) UNI_BSS_HEAD.get("RESP_CODE");
            if (!"0000".equals(code) && !"00000".equals(code)) {
                throw new EcAopServerBizException("9999", UNI_BSS_HEAD.get("RESP_DESC") + "");
            }
        }
        else {
            throw new EcAopServerBizException("9999", "调号卡中心" + kind + "接口返回异常!");
        }
        Map UNI_BSS_BODY = (Map) out.get("UNI_BSS_BODY");
        if (null != UNI_BSS_BODY) {
            Map rspMap = (Map) UNI_BSS_BODY.get(rspKey);
            if (null != rspMap) {
                String code = (String) rspMap.get("RESP_CODE");
                if (!"0000".equals(code)) {
                    throw new EcAopServerBizException("9999", "号卡中心" + kind + "接口返回：" + rspMap.get("RESP_DESC"));
                }
            }
        }
        else {
            throw new EcAopServerBizException("9999", "调号卡中心" + kind + "接口返回异常!");
        }
    }

    /**
     * 准备REQ中常用参数
     */
    public Map createREQ(Map msg) {
        Map REQ = new HashMap();
        REQ.put("STAFF_ID", msg.get("operatorId"));
        REQ.put("PROVINCE_CODE", msg.get("province"));
        REQ.put("CITY_CODE", msg.get("city"));
        REQ.put("CHANNEL_ID", msg.get("channelId"));
        REQ.put("SYS_CODE", "5600");
        String district = (String) msg.get("district");
        String channelType = (String) msg.get("channelType");
        if (null != district && !"".equals(district)) {
            REQ.put("DISTRICT_CODE", msg.get("district"));
        }
        if (null != channelType && !"".equals(channelType)) {
            REQ.put("CHANNEL_TYPE", msg.get("channelType"));
        }
        return REQ;
    }

    /**
     * 准备UNI_BSS_HEAD及UNI_BSS_ATTACHED
     *
     * @throws Exception
     * @return Map 含UNI_BSS_HEAD
     */
    public Map createHeadAndAttached(String appKey) throws Exception {
        return NumFaceHeadHelper.creatHead(appKey);
    }

    private Map preOrderSubParam(Map inMap) {
        Map outMap = new HashMap();
        List<Map> provinceOrderInfo = (ArrayList<Map>) inMap.get("provinceOrderInfo");
        if (null != provinceOrderInfo && 0 != provinceOrderInfo.size()) {
            outMap.put("subOrderSubReq", dealSubOrder(provinceOrderInfo));
        }
        outMap.put("origTotalFee", "0");
        outMap.put("operationType", "01");
        outMap.put("cancleTotalFee", provinceOrderInfo.get(0).get("totalFee"));
        return outMap;
    }

    private Map dealSubOrder(List<Map> provinceOrderInfo) {
        Map retMap = new HashMap();
        for (Map tempMap : provinceOrderInfo) {
            retMap.put("subOrderId", tempMap.get("subOrderId"));
            retMap.put("subProvinceOrderId", tempMap.get("subProvinceOrderId"));
            List<Map> feeList = (ArrayList<Map>) tempMap.get("preFeeInfoRsp");
            List<Map> retFee = new ArrayList<Map>();
            if (null != feeList && 0 != feeList.size()) {
                for (Map fee : feeList) {
                    Map tempFee = dealFeeInfo(fee);
                    retFee.add(tempFee);
                }
                retMap.put("feeInfo", retFee);
            }
        }
        return retMap;
    }

    private Map dealFeeInfo(Map inputMap) {
        Map retMap = new HashMap();
        retMap.put("feeCategory", inputMap.get("feeMode"));
        retMap.put("feeId", inputMap.get("feeTypeCode"));
        retMap.put("feeDes", inputMap.get("feeTypeName"));
        retMap.put("operateType", "1");
        retMap.put("origFee", inputMap.get("oldFee"));
        retMap.put("reliefFee", inputMap.get("maxDerateFee"));
        retMap.put("realFee", inputMap.get("oldFee"));
        return retMap;
    }

    private Map preDataForPreCommit(Exchange exchange, Map msg) throws Exception {
        threepartCheck(ExchangeUtils.ofCopy(exchange, msg), msg);
        Map base = preBaseData(msg);
        Map ext = preExtData(msg);
        Map preDataMap = new HashMap();
        preDataMap.put("ordersId", msg.get("ordersId"));
        preDataMap.put("operTypeCode", "0");
        preDataMap.put("base", base);
        preDataMap.put("ext", ext);
        return preDataMap;
    }

    private void threepartCheck(Exchange exchange, Map msg) {
        msg.put("serialNumber", msg.get("serialNumber"));
        msg.put("tradeTypeCode",
                IsEmptyUtils.isEmpty(msg.get("destroyUserTag")) || "1".equals(msg.get("destroyUserTag")) ? "0190"
                        : "0192");
        msg.put("getMode", "1111111111100013010000000100001");
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, msg);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[3], threePartExchange);
        try {
            CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "调用cbss三户接口出错:" + e.getMessage());
        }

        Map result = threePartExchange.getOut().getBody(Map.class);
        List<Map> custInfoList = (List<Map>) result.get("custInfo");
        List<Map> userInfoList = (List<Map>) result.get("userInfo");
        List<Map> acctInfoList = (List<Map>) result.get("acctInfo");
        // 需要通过userInfo的返回,拼装老产品信息
        Map custInfo = custInfoList.get(0);
        Map userInfo = userInfoList.get(0);
        Map acctInfo = acctInfoList.get(0);
        String eparchyCode = ChangeCodeUtils.changeCityCode(msg);
        msg.putAll(MapUtils.asMap("userId", userInfo.get("userId"), "custId", custInfo.get("custId"), "acctId",
                acctInfo.get("acctId"), "custName",
                custInfo.get("custName"), "brandCode", userInfo.get("brandCode"), "productId",
                userInfo.get("productId"),
                "eparchyCode", eparchyCode, "startDate", GetDateUtils.getDate(), "operTypeCode", "0"));
    }

    private Map preBaseData(Map msg) {
        String date = (String) msg.get("startDate");
        Map base = new HashMap();
        base.put("subscribeId", msg.get("ordersId"));
        base.put("tradeId", msg.get("tradeId"));
        base.put("tradeTypeCode",
                IsEmptyUtils.isEmpty(msg.get("destroyUserTag")) || "1".equals(msg.get("destroyUserTag")) ? "0190"
                        : "0192");
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("foregift", "0");
        base.put("execTime", date);
        base.put("acceptDate", date);
        base.put("chkTag", "0");
        base.put("operFee", "0");
        base.put("cancelTag", "0");
        base.put("netTypeCode", "0050");
        base.put("checktypeCode", "8");
        base.put("advancePay", "0");
        base.put("inModeCode", "E");
        base.put("custId", msg.get("custId"));
        base.put("custName", msg.get("custName"));
        base.put("acctId", msg.get("acctId"));
        base.put("serinalNamber", msg.get("serialNumber"));
        base.put("productId", msg.get("productId"));
        base.put("userDiffCode", "00");
        base.put("brandCode", msg.get("brandCode"));
        base.put("usecustId", msg.get("custId"));
        base.put("userId", msg.get("userId"));
        base.put("termIp", "10.124.0.48");
        base.put("cityCode", msg.get("city"));
        base.put("eparchyCode", msg.get("eparchyCode"));
        return base;
    }

    private Map preExtData(Map msg) throws Exception {
        Map ext = new HashMap();
        ext.put("tradeItem", preDataForTradeItem(msg));
        ext.put("tradeSubItem", preDataForTradeSubItem(msg));
        ext.put("tradeUser", preUserData(msg));
        return ext;
    }

    private Map preUserData(Map msg) {
        Map tradeUser = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("userId", msg.get("userId"));
        item.put("removeTag", "1");
        item.put("removeReasonCode", "D1");
        item.put("destoryTime", msg.get("nextStartDate"));
        item.put("preDestoryTime", msg.get("nextStartDate"));
        itemList.add(item);
        tradeUser.put("item", itemList);
        return tradeUser;
    }

    private Map preDataForTradeSubItem(Map msg) {
        List<Map> Item = new ArrayList<Map>();
        Map tradeSubItem = new HashMap();
        Item.add(lan.createTradeSubItemE("PRE_DESTROY_USER", "1", (String) msg.get("userId")));
        Item.add(lan.createTradeSubItemE("OLD_USER_STATE", "0", (String) msg.get("userId")));
        Item.add(lan.createTradeSubItemE(
                "PRE_DESTROY_TIME",
                IsEmptyUtils.isEmpty(msg.get("destroyUserTag")) || "1".equals(msg.get("destroyUserTag")) ? msg
                        .get("nextStartDate") : GetDateUtils.getDate(), (String) msg.get("userId")));
        Item.add(lan.createTradeSubItemE("USER_ID", (String) msg.get("userId"), (String) msg.get("userId")));
        tradeSubItem.put("item", Item);
        return tradeSubItem;
    }

    private Map preDataForTradeItem(Map msg) {
        List<Map> itemList = new ArrayList<Map>();
        Map tradeItem = new HashMap();
        //当外围传orderSource字段时，下发此属性，ATTR_CODE对应orderSource值，ATTR_VALUE为1
        if (!IsEmptyUtils.isEmpty(msg.get("orderSource"))) {
            if ("wxhEnd".equals(msg.get("orderSource"))) {
                //wxhEnd：沃小号平台（沃小号平台订单必传）
                itemList.add(LanUtils.createTradeItem(msg.get("orderSource") + "", "1"));
            }
        }
        itemList.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", msg.get("eparchyCode")));
        itemList.add(LanUtils.createTradeItem("GROUP_ID", "0"));
        itemList.add(LanUtils.createTradeItem("SHORT_CODE", "0"));
        tradeItem.put("item", itemList);
        return tradeItem;
    }

    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
