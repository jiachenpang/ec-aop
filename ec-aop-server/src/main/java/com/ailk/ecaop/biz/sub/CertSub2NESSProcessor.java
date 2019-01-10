package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.extractor.TransIdFromRedisValueExtractor;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.n3r.ecaop.core.processor.TransReqParamsMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.DateUtils;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("certSub2NESS")
public class CertSub2NESSProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.trade.n6.checkUserParametersMapping",
            "ecaop.masb.chph.gifa.ParametersMapping",
            "ecaop.trades.sccc.cancelPre.paramtersmapping",
            "ecaop.trades.sccc.cancel.paramtersmapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];

    @Override
    public void process(Exchange exchange) throws Exception {
        // 1、三户 2、客户资料校验 3、获取ID、预提交、正式提交
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        // checkInputParam(msg);
        Map threePartOut = callThreePart(exchange, msg);
        List<Map> userInfoList = (List<Map>) threePartOut.get("userInfo");
        msg.put("serviceClassCode", userInfoList.get(0).get("serviceClassCode"));
        msg.put("brandCode", userInfoList.get(0).get("brandCode"));
        callCheckCustInfo(exchange, msg);
        Map preSubmitMap = callPreSubmit(exchange, msg, threePartOut);
        callSubmit(exchange, msg, preSubmitMap);

    }

    /**
     * 校验入参是否合法
     * 
     * @param msg
     */
    private void checkInputParam(Map msg) {
        String startDate = msg.get("startDate") + "235959";
        String endDate = msg.get("endDate") + "000000";
        String now = DateUtils.getDate();
        if (now.compareTo(endDate) > 0 || now.compareTo(startDate) < 0) {
            throw new EcAopServerBizException("9999", "证件有效期不合法,请校验!");
        }
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
        threePartMap.put("getMode", "111001000000101311110000000000");
        threePartMap.put("tradeTypeCode", "9999");
        threePartMap.put("channelType", msg.get("channelId"));
        Object areaCode = msg.get("areaCode");
        if (null != areaCode && !"".equals(areaCode)) {
            threePartMap.put("serialNumber", areaCode + msg.get("number").toString());
        }
        else {
            threePartMap.put("serialNumber", msg.get("number"));
        }
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.UsrForNorthSer." + msg.get("province"));
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        return threePartExchange.getOut().getBody(Map.class);
    }

    /**
     * 客户资料校验
     * 
     * @param exchange
     * @param msg
     * @return
     * @throws Exception
     */
    private Map callCheckCustInfo(Exchange exchange, Map msg) throws Exception {
        Map custCheckMap = new HashMap();
        MapUtils.arrayPut(custCheckMap, msg, MagicNumber.COPYARRAY);
        custCheckMap.put("checkType", "0");
        custCheckMap.put("certType", "1");// CBSS侧18位身份证为1
        custCheckMap.put("certNum", msg.get("certNum"));
        custCheckMap.put("serType", "1");
        Exchange custCheckExchange = ExchangeUtils.ofCopy(exchange, custCheckMap);
        new TransReqParamsMappingProcessor().process(custCheckExchange);
        CallEngine.aopCall(custCheckExchange, "ecaop.comm.conf.url.ec-aop.rest");
        return JSON.parseObject(custCheckExchange.getOut().getBody(String.class));
    }

    private Map callPreSubmit(Exchange exchange, Map msg, Map threePartOut) {
        Map tradeMap = new HashMap();
        MapUtils.arrayPut(tradeMap, msg, MagicNumber.COPYARRAY);
        Exchange getItemIdExchange = ExchangeUtils.ofCopy(exchange, tradeMap);
        Exchange getTradeIdExchange = ExchangeUtils.ofCopy(exchange, tradeMap);

        // String tradeId = GetSeqUtil.getSeqFromCb(getTradeIdExchange, "seq_trade_id");
        // String itemId = GetSeqUtil.getSeqFromCb(getItemIdExchange, "seq_item_id");
        String tradeId = (String) (creatTransIDO(exchange));
        String itemId = (String) (creatTransIDO(exchange));
        Map custInfo = ((List<Map>) threePartOut.get("custInfo")).get(0);
        Map userInfo = ((List<Map>) threePartOut.get("userInfo")).get(0);
        Map acctInfo = ((List<Map>) threePartOut.get("acctInfo")).get(0);
        String custId = (String) custInfo.get("custId");
        String userId = (String) userInfo.get("userId");
        String acctId = (String) acctInfo.get("acctId");
        Map preDateMap = MapUtils.asMap("itemId", itemId, "tradeId", tradeId, "newCustId", custInfo.get("custId"));

        MapUtils.arrayPut(preDateMap, msg, MagicNumber.COPYARRAY);
        MapUtils.arrayPut(preDateMap, msg, new String[] { "number", "customerName", "certNum", "psptAddr",
                "serviceClassCode", "brandCode" });
        preDateMap.put("serinalNamber", msg.get("number"));
        preDateMap.put("city", userInfo.get("eparchyCode"));
        preDateMap.put("district", userInfo.get("cityCode"));
        preDateMap.put("custId", custId);
        preDateMap.put("userId", userId);
        preDateMap.put("acctId", acctId);
        preDateMap.put("operatorId", msg.get("operatorId"));
        preDateMap.put("base", preBase(preDateMap, exchange.getAppCode()));
        preDateMap.put("ext", preExt(preDateMap));

        preDateMap.put("ordersId", preDateMap.get("tradeId"));
        preDateMap.put("operTypeCode", "0");
        Exchange preSubmitExchange = ExchangeUtils.ofCopy(exchange, preDateMap);
        try {
            new LanUtils().preData("ecaop.trades.spec.sUniTrade.paramtersmapping", preSubmitExchange);
            CallEngine.wsCall(preSubmitExchange, "ecaop.comm.conf.url.OrdForNorthSer." + msg.get("province"));
            new LanUtils().xml2Json("ecaop.trades.spec.sUniTrade.template", preSubmitExchange);

        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用预提交失败！" + e.getMessage());
        }
        return preSubmitExchange.getOut().getBody(Map.class);
    }

    private Object creatTransIDO(Exchange exchange) {
        String str[] = { "@50"
        };
        TransIdFromRedisValueExtractor transId = new TransIdFromRedisValueExtractor();
        transId.applyParams(str);
        return transId.extract(exchange);
    }

    private Map preBase(Map inputMap, String appCode) {
        Map base = new HashMap();
        base.put("userDiffCode", "00");
        base.put("inModeCode", "0");
        base.put("usecustId", inputMap.get("newCustId"));
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("foregift", "0");
        base.put("execTime", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("termIp", "10.124.0.7");
        base.put("chkTag", "0");
        base.put("tradeId", inputMap.get("tradeId"));
        base.put("cancelTag", "0");
        base.put("operFee", "0");
        base.put("tradeTypeCode", "0102");
        base.put("areaCode", inputMap.get("city"));
        base.put("cityCode", inputMap.get("district"));
        base.put("eparchyCode", inputMap.get("city"));
        base.put("netTypeCode", inputMap.get("serviceClassCode"));
        base.put("custName", inputMap.get("custName"));
        base.put("checktypeCode", "0");
        base.put("brandCode", inputMap.get("brandCode"));
        base.put("tradeStaffId", inputMap.get("operatorId"));
        base.put("acceptDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("productId", inputMap.get("productId"));
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
        base.put("serialNumber", inputMap.get("serinalNamber"));
        base.put("ITEM_ID", "");
        base.put("areaCode", inputMap.get("city"));
        base.put("custId", inputMap.get("custId"));
        base.put("acctId", inputMap.get("acctId"));
        base.put("userId", inputMap.get("userId"));// TODO
        base.put("acceptMonth", RDate.getMonth());
        base.put("startAcycId", RDate.currentTimeStr("yyyyMM"));

        base.put("subscribeId", inputMap.get("tradeId"));
        return base;
    }

    private Map preExt(Map inMap) {
        Map ext = new HashMap();
        ext.put("tradeItem", preTradeItem(inMap));
        ext.put("tradeSubItem", preTradeSubItem(inMap));
        return ext;
    }

    private Map preTradeSubItem(Map inMap) {
        Map tradeSubItem = new HashMap();
        LanUtils lan = new LanUtils();
        List<Map> items = new ArrayList<Map>();
        items.add(lan.createTradeSubItem("USER_NAME", inMap.get("custName"), (String) inMap.get("itemId")));
        tradeSubItem.put("item", items);
        return tradeSubItem;
    }

    private Map preTradeItem(Map inMap) {
        Map tradeItem = new HashMap();
        LanUtils lan = new LanUtils();
        List<Map> items = new ArrayList<Map>();
        items.add(lan.createAttrInfoNoTime("STANDARD_KIND_CODE", inMap.get("city")));
        items.add(lan.createAttrInfoNoTime("ACCT_ONLY", "0"));
        items.add(lan.createAttrInfoNoTime("ORG_CUST_ID", inMap.get("custId")));
        items.add(lan.createAttrInfoNoTime("ORG_USECUST_ID", inMap.get("custId")));
        items.add(lan.createAttrInfoNoTime("CUST_ID", inMap.get("newCustId")));
        items.add(lan.createAttrInfoNoTime("USECUST_ID", inMap.get("newCustId")));
        items.add(lan.createAttrInfoNoTime("IS_INHERIT_SCORE", "0"));
        items.add(lan.createAttrInfoNoTime("USER_PASSWD", ""));
        items.add(lan.createAttrInfoNoTime("CREATE_CUST", "0"));
        items.add(lan.createAttrInfoNoTime("BSS_NEED_CREATE_CUST", "0"));
        items.add(lan.createAttrInfoNoTime("CUST_TAG", "3"));
        tradeItem.put("item", items);
        return tradeItem;
    }

    private void callSubmit(Exchange exchange, Map msg, Map preSubmitMap) throws Exception {
        List<Map> rspInfo = (List<Map>) preSubmitMap.get("rspInfo");
        if (null == rspInfo || rspInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "cBSS统一预提交未返回应答信息");
        }
        String provOrderId = String.valueOf(rspInfo.get(0).get("provOrderId"));
        String orderNo = String.valueOf(preSubmitMap.get("tradeId"));
        Integer totalFee = 0;
        List<Map> subOrderSubReqList = new ArrayList<Map>();
        List<Map> payInfoList = new ArrayList<Map>();
        for (Map rsp : rspInfo) {
            List<Map> provinceOrderInfo = (List<Map>) rsp.get("provinceOrderInfo");
            if (null == provinceOrderInfo || 0 == provinceOrderInfo.size()) {
                continue;
            }
            for (Map preovinceOrder : provinceOrderInfo) {
                totalFee = totalFee + Integer.valueOf(preovinceOrder.get("totalFee").toString());
                String subProvinceOrderId = String.valueOf(preovinceOrder.get("subProvinceOrderId"));
                String subOrderId = String.valueOf(preovinceOrder.get("subOrderId"));
                List<Map> preFeeInfoRsp = (List<Map>) preovinceOrder.get("preFeeInfoRsp");
                List<Map> feeList = dealFee(preFeeInfoRsp);
                Map subOrderMap = MapUtils.asMap("subProvinceOrderId", subProvinceOrderId, "subOrderId", subOrderId,
                        "feeInfo", feeList);
                subOrderSubReqList.add(subOrderMap);
            }
        }
        // 正式提交支付信息节点
        Map payInfoMap = new HashMap();
        payInfoMap.put("payMoney", totalFee);
        payInfoMap.put("payType", "00");
        payInfoMap.put("subProvinceOrderId", provOrderId);
        payInfoMap.put("operatorId", msg.get("operatorId"));
        payInfoList.add(payInfoMap);
        preSubmitMap.put("operatorId", msg.get("operatorId"));
        preSubmitMap.put("city", msg.get("city"));
        preSubmitMap.put("provOrderId", provOrderId);// 预提交返回订单
        preSubmitMap.put("orderNo", provOrderId);// 外围系统订单
        preSubmitMap.put("province", msg.get("province"));
        preSubmitMap.put("channelId", msg.get("channelId"));
        preSubmitMap.put("channelType", msg.get("channelId"));
        preSubmitMap.put("origTotalFee", totalFee);
        preSubmitMap.put("subOrderSubReq", subOrderSubReqList);
        preSubmitMap.put("payInfo", payInfoList);
        Exchange submitExchange = ExchangeUtils.ofCopy(exchange, preSubmitMap);
        new LanUtils().preData("ecaop.trades.tscs.ParametersMapping", submitExchange);
        CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.OrderSub." + msg.get("province"));
        new LanUtils().xml2Json("ecaop.trades.mpsb.template", submitExchange);
        Message out = new DefaultMessage();
        List para = new ArrayList();
        Map paraMap = new HashMap();
        paraMap.put("paraId", "Ok");
        paraMap.put("paraValue", "订单成功");
        para.add(paraMap);
        out.setBody(para);
        exchange.setOut(out);
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
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
