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
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.google.common.collect.Maps;

@EcRocTag("RealpersonSub2Ness")
public class RealpersonSub2NessProcessor extends BaseAopProcessor implements ParamsAppliable {
    private static final String[] PARAM_ARRAY = { "ecaop.trade.n6.checkUserParametersMapping",
            "ecaop.trades.spec.sUniTrade.paramtersmapping", "ecaop.trades.scrat.ParametersMapping",
            "ecaop.masb.chph.gifa.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];
    private final LanUtils lan = new LanUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        // TODO Auto-generated method stub、
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map threePartOut = callThreePart(exchange, msg);
        Map preSubmitMap = callPreSubmit(exchange, msg, threePartOut);
        Map submitMap = Maps.newHashMap();
        MapUtils.arrayPut(submitMap, msg, MagicNumber.COPYARRAY);
        preOrderSubMessage(preSubmitMap, submitMap, msg);
        Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMap);
        new LanUtils().preData(pmp[2], submitExchange);
        CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.OrderSub." + msg.get("province"));
        new LanUtils().xml2Json("ecaop.trades.mpsb.template", submitExchange);
        Map retMap = new HashMap();
        retMap.put("provOrderId", submitMap.get("provOrderId"));
        exchange.getOut().setBody(retMap);
    }

    /**
     * 调用北六ESS三户接口、获取相关信息
     * 
     * @param exchange
     * @param msg
     * @return
     * @throws Exception
     */
    private Map callThreePart(Exchange exchange, Map msg) throws Exception {
        Map threePartMap = new HashMap();
        MapUtils.arrayPut(threePartMap, msg, MagicNumber.COPYARRAY);
        threePartMap.put("tradeTypeCode", "9999");
        threePartMap.put("serialNumber", msg.get("number"));
        threePartMap.put("getMode", "111001000000101311110000000000");
        threePartMap.put("channelType", msg.get("channelId"));
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        lan.preData(pmp[0], threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.UsrForNorthSer." + msg.get("province"));
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        return threePartExchange.getOut().getBody(Map.class);
    }

    private Map callPreSubmit(Exchange exchange, Map msg, Map threePartOut) {

        String tradeId = (String) GetSeqUtil.getSeqFromCb(pmp[3], exchange, "seq_trade_id", 1).get(0);
        String itemId = (String) GetSeqUtil.getSeqFromCb(pmp[3], exchange, "seq_item_id", 1).get(0);
        Map custInfo = ((List<Map>) threePartOut.get("custInfo")).get(0);
        Map userInfo = ((List<Map>) threePartOut.get("userInfo")).get(0);
        Map acctInfo = ((List<Map>) threePartOut.get("acctInfo")).get(0);
        if (IsEmptyUtils.isEmpty(custInfo)) {
            throw new EcAopServerBizException("9999", "客户信息未返回");
        }
        if (IsEmptyUtils.isEmpty(userInfo)) {
            throw new EcAopServerBizException("9999", "用户信息未返回");
        }
        if (IsEmptyUtils.isEmpty(acctInfo)) {
            throw new EcAopServerBizException("9999", "账户信息为空");
        }
        Map preDateMap = MapUtils.asMap("tradeId", tradeId, "itemId", itemId, "newCustId", custInfo.get("custId"),
                "productId",
                userInfo.get("productId"), "brandCode", userInfo.get("brandCode"), "custId",
                custInfo.get("custId"), "userId", userInfo.get("userId")
                , "acctId", acctInfo.get("acctId"));
        MapUtils.arrayPut(preDateMap, msg, MagicNumber.COPYARRAY);
        preDateMap.put("serinalNamber", msg.get("number"));
        preDateMap.put("city", userInfo.get("eparchyCode"));
        preDateMap.put("district", userInfo.get("cityCode"));
        preDateMap.put("custId", custInfo.get("custId"));
        preDateMap.put("userId", userInfo.get("userId"));
        preDateMap.put("acctId", acctInfo.get("acctId"));
        preDateMap.put("base", preBase(preDateMap, msg));
        preDateMap.put("ext", preExt(preDateMap, msg));
        preDateMap.put("ordersId", preDateMap.get("tradeId"));
        preDateMap.put("operTypeCode", "0");
        Exchange preSubmitExchange = ExchangeUtils.ofCopy(exchange, preDateMap);
        try {
            new LanUtils().preData(pmp[1], preSubmitExchange);
            CallEngine.wsCall(preSubmitExchange, "ecaop.comm.conf.url.OrdForNorthSer." + msg.get("province"));
            new LanUtils().xml2Json("ecaop.trades.spec.sUniTrade.template", preSubmitExchange);

        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用预提交失败！" + e.getMessage());
        }
        return preSubmitExchange.getOut().getBody(Map.class);
    }

    private Map preBase(Map inputMap, Map msg) {
        Map base = new HashMap();
        base.put("subscribeId", inputMap.get("tradeId"));
        base.put("tradeId", inputMap.get("tradeId"));
        base.put("startDate", GetDateUtils.getDate());
        base.put("endDate", "20501231000000");
        base.put("acceptDate", GetDateUtils.getDate());
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("inModeCode", "0");
        base.put("tradeTypeCode", "0103");
        base.put("productId", inputMap.get("productId"));
        base.put("brandCode", inputMap.get("brandCode"));
        base.put("custId", inputMap.get("custId"));
        base.put("acctId", inputMap.get("acctId"));
        base.put("userId", inputMap.get("userId"));// TODO
        base.put("usecustId", inputMap.get("custId"));
        base.put("userDiffCode", "00");
        base.put("netTypeCode", "0040");
        base.put("serinalNamber", msg.get("number"));
        base.put("custName", inputMap.get("custName"));
        base.put("termIp", "127.0.0.1");
        base.put("eparchyCode", msg.get("city"));
        base.put("cityCode", msg.get("district"));
        base.put("execTime", GetDateUtils.getDate());
        base.put("operFee", "0");
        base.put("foregift", "0");
        base.put("advancePay", "0");
        base.put("feeState", "");
        base.put("feeStaffId", "");
        base.put("cancelTag", "0");
        base.put("checktypeCode", "");
        base.put("chkTag", "0");
        base.put("actorName", "");
        base.put("actorCertTypeId", "");
        base.put("actorPhone", "");
        base.put("actorCertNum", "");
        base.put("contact", "");
        base.put("contactPhone", "");
        base.put("contactAddress", "");
        base.put("remark", "");
        base.put("mainDiscntCode", "");// 暂时为空
        return base;
    }

    private Map preExt(Map inMap, Map msg) {
        Map ext = new HashMap();
        ext.put("tradeItem", preTradeItem(msg));
        ext.put("tradeSubItem", preTradeSubItem(inMap, msg));
        return ext;
    }

    private Map preTradeSubItem(Map inMap, Map msg) {
        Map tradeSubItem = new HashMap();
        LanUtils lan = new LanUtils();
        List<Map> items = new ArrayList<Map>();
        String certTypeCode = (String) inMap.get("certTypeCode");
        if ("07|13|14|15|17|18|21|20|23".contains(certTypeCode)) {
            items.add(lan.createTradeSubItem("REAL_PERSON_TAG", msg.get("realPersonTag"),
                    (String) inMap.get("itemId")));
        }
        else {
            items.add(lan.createTradeSubItem("REAL_PERSON_TAG", msg.get("realPersonTag"),
                    (String) inMap.get("itemId")));
            items.add(lan.createTradeSubItem4("REAL_PERSON_TAG", msg.get("REAL_PERSON_TAG"),
                    (String) inMap.get("itemId")));
        }

        tradeSubItem.put("item", items);
        return tradeSubItem;
    }

    private Map preTradeItem(Map msg) {
        Map tradeItem = new HashMap();
        LanUtils lan = new LanUtils();
        List<Map> items = new ArrayList<Map>();
        items.add(lan.createAttrInfoNoTime("PHOTO_TAG", msg.get("photoTag")));
        items.add(lan.createAttrInfoNoTime("PHOTO_TAG", msg.get("photoTag")));
        tradeItem.put("item", items);
        return tradeItem;
    }

    private void preOrderSubMessage(Map preSubmitRetMap, Map submitMap, Map msg) throws Exception {
        List<Map> rspInfo = (ArrayList<Map>) preSubmitRetMap.get("rspInfo");
        if (rspInfo == null || rspInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "cBSS统一预提交未返回应答信息");
        }
        // 准备正式提交参数
        List<Map> provinceOrderInfo = (ArrayList<Map>) rspInfo.get(0).get("provinceOrderInfo");
        List<Map> OrderSubReq = new ArrayList<Map>();
        // submitMap请求参数Map
        submitMap.put("provOrderId", rspInfo.get(0).get("bssOrderId"));
        submitMap.put("orderNo", rspInfo.get(0).get("provOrderId"));
        submitMap.put("operationType", "01");
        submitMap.put("noteType", "1");
        Map subOrderSubReq = new HashMap();
        subOrderSubReq.put("subProvinceOrderId", submitMap.get("provOrderId"));
        subOrderSubReq.put("subOrderId", submitMap.get("orderNo"));
        int totalFee = 0;
        if (null != provinceOrderInfo && provinceOrderInfo.size() > 0) {
            for (Map provinceOrder : provinceOrderInfo) {
                List<Map> feeInfos = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                totalFee = totalFee + Integer.valueOf(provinceOrder.get("totalFee").toString());
                List<Map> fees = dealFee(feeInfos);
                subOrderSubReq.put("feeInfo", fees);
            }

        }
        submitMap.put("origTotalFee", totalFee);
        submitMap.put("cancleTotalFee", 0);
        OrderSubReq.add(subOrderSubReq);
        List<Map> pay = new ArrayList<Map>();
        Map payInfo = new HashMap();
        payInfo.put("payType", "10");
        payInfo.put("payMoney", totalFee);
        payInfo.put("subProvinceOrderId", submitMap.get("provOrderId"));
        pay.add(payInfo);
        submitMap.put("payInfo", pay);
        submitMap.put("subOrderSubReq", OrderSubReq);

    }

    /**
     * 处理预提交返回费用项
     * 
     * @param feeList
     * @return
     */
    private List<Map> dealFee(List<Map> feeList) {
        if (null == feeList || 0 == feeList.size()) {
            return new ArrayList<Map>();
        }
        List<Map> retFeeList = new ArrayList<Map>();
        for (Map fee : feeList) {
            Map retFee = new HashMap();
            retFee.put("feeId", fee.get("feeTypeCode"));
            retFee.put("feeCategory", fee.get("feeMode"));
            retFee.put("feeDes", fee.get("feeTypeName"));
            retFee.put("origFee", fee.get("fee"));
            retFeeList.add(retFee);
        }
        return retFeeList;
    }

    @Override
    public void applyParams(String[] params) {
        // TODO Auto-generated method stub
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }

    }

}
