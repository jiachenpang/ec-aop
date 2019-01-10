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
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("cancelUserToN6")
public class CancelUserToN6Processors extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.trade.n6.checkUserParametersMapping",
            "ecaop.masb.sbac.N6.sglUniTradeParametersMapping", "ecaop.trades.tscs.ParametersMapping",
            "ecaop.trades.seqid.ParametersMapping" };
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
        /*String nextStartDate = GetDateUtils.getSpecifyDateTime(1, 1,
                GetDateUtils.transDate(GetDateUtils.getNextMonthFirstDayFormat(), 19), 9);
        msg.put("nextStartDate", nextStartDate);*/
        Map preDataMap = preDataForPreCommit(exchange, msg);
        MapUtils.arrayPut(preDataMap, msg, MagicNumber.COPYARRAY);
        Exchange presubExchange = ExchangeUtils.ofCopy(exchange, preDataMap);
        // 调预提交接口
        lan.preData(pmp[1], presubExchange);
        CallEngine.wsCall(presubExchange,
                "ecaop.comm.conf.url.n6ess.services.AOPOrdForNorthSer" + "." + msg.get("province"));
        lan.xml2Json("ecaop.masb.sbac.sglUniTradeTemplate", presubExchange);

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
        lan.preData(pmp[2], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.OrderSub." + msg.get("province"));
        lan.xml2Json("ecaop.trades.mpsb.template", exchange);

        Map outMap = new HashMap();
        //Map orderOutMap = exchange.getOut().getBody(Map.class);
        // 受理成功时，返回总部和省份订单
        outMap.put("provOrderId", provOrderId);
        outMap.put("bssOrderId", bssOrderId);
        exchange.getOut().setBody(outMap);
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
        msg.put("tradeTypeCode", "0192");
        msg.put("getMode", "1111111111100013010000000100001");
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, msg);
        try {
            lan.preData(pmp[0], threePartExchange);
            CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.UsrForNorthSer" + "." + msg.get("province"));
            lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "调用三户接口出错:" + e.getMessage());
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
        String tradeId = GetSeqUtil.getSeqFromN6ess(pmp[3], exchange, "TRADE_ID", eparchyCode);
        msg.putAll(MapUtils.asMap("tradeId", tradeId, "userId", userInfo.get("userId"), "custState",
                custInfo.get("custState"), "custId",
                custInfo.get("custId"), "acctId",
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
        base.put("tradeTypeCode", "0192");
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("foregift", "0");
        base.put("execTime", date);
        base.put("acceptDate", date);
        base.put("chkTag", "0");
        base.put("operFee", "0");
        base.put("cancelTag", "0");
        base.put("netTypeCode", "0033");
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
        base.put("termIp", "132.35.87.196");
        base.put("cityCode", msg.get("city"));
        base.put("eparchyCode", msg.get("eparchyCode"));
        return base;
    }

    private Map preExtData(Map msg) throws Exception {
        Map ext = new HashMap();
        ext.put("tradeItem", preDataForTradeItem(msg));
        ext.put("tradeSubItem", preDataForTradeSubItem(msg));
        ext.put("tradeUser", preUserData(msg));
        ext.put("tradeCustomer", preCustData(msg));
        return ext;
    }

    private Map preUserData(Map msg) {
        Map tradeUser = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("userId", msg.get("userId"));
        item.put("removeTag", "2");
        item.put("removeReasonCode", "");
        item.put("destoryTime", GetDateUtils.getDate());
        item.put("preDestoryTime", GetDateUtils.getDate());
        item.put("inDate", GetDateUtils.getDate());
        item.put("openDate", GetDateUtils.getDate());
        itemList.add(item);
        tradeUser.put("item", itemList);
        return tradeUser;
    }

    private Map preCustData(Map msg) {
        Map tradeUser = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("sustId", msg.get("custId"));
        item.put("custState", msg.get("custState"));
        item.put("inDate", GetDateUtils.getDate());
        item.put("openDate", GetDateUtils.getDate());
        itemList.add(item);
        tradeUser.put("item", itemList);
        return tradeUser;
    }

    private Map preDataForTradeSubItem(Map msg) {
        List<Map> Item = new ArrayList<Map>();
        Map tradeSubItem = new HashMap();
        Item.add(lan.createTradeSubItemE("PRE_DESTROY_USER", "1", (String) msg.get("userId")));
        Item.add(lan.createTradeSubItemE("othercontact", "", (String) msg.get("userId")));
        Item.add(lan.createTradeSubItemE("LINK_NAME", msg.get("custName"), (String) msg.get("userId")));
        Item.add(lan.createTradeSubItemE("LINK_PHONE", (String) msg.get("serialNumber"), (String) msg.get("userId")));
        tradeSubItem.put("item", Item);
        return tradeSubItem;
    }

    private Map preDataForTradeItem(Map msg) {
        List<Map> itemList = new ArrayList<Map>();
        Map tradeItem = new HashMap();
        /*当外围传orderSource字段时，下发此属性，ATTR_CODE对应orderSource值，ATTR_VALUE为1
        if (!IsEmptyUtils.isEmpty(msg.get("orderSource"))) {
            if ("wxhEnd".equals(msg.get("orderSource"))) {
                //wxhEnd：沃小号平台（沃小号平台订单必传）
                itemList.add(LanUtils.createTradeItem(msg.get("orderSource") + "", "1"));
            }
        }*/
        itemList.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", msg.get("eparchyCode")));
        itemList.add(LanUtils.createTradeItem("GROUP_ID", "0"));
        itemList.add(LanUtils.createTradeItem("SHORT_CODE", "0"));
        itemList.add(LanUtils.createTradeItem("IMMEDIACY_INFO", "0"));
        itemList.add(LanUtils.createTradeItem("SO_TYPE", "0"));
        itemList.add(LanUtils.createTradeItem("SERIAL_NUMBER", msg.get("serialNumber")));
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
