package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.n3r.ecaop.core.processor.TransReqParamsMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.GetThreePartInfoUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;

/**
 * 真实身份证信息同步接口cBSS分支
 */
@EcRocTag("certSub2CB")
public class CertSub2CbProcessor extends BaseAopProcessor implements ParamsAppliable {

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];
    private static final String[] PARAM_ARRAY = { "ecaop.trades.sccc.cancelPre.paramtersmapping" };// 预提交

    static String[] COPYARRAY = { "operatorId", "province", "city", "district", "channelId", "channelType" };

    @Override
    public void process(Exchange exchange) throws Exception {
        Map certInfo = exchange.getIn().getBody(Map.class);
        // 调北六客户资料校验
        exchange.setMethod("ecaop.trades.query.comm.cust.mcheck");
        exchange.setMethodCode("qccm");
        preParam(exchange.getIn().getBody(Map.class));
        TransReqParamsMappingProcessor trpmp = new TransReqParamsMappingProcessor();
        trpmp.process(exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.ec-aop.rest");
        String custId = dealCertRsp(exchange);
        // 调三户接口获取客户、产品、实名标志
        exchange.getIn().setBody(certInfo);
        Map threePartInfoMap = threePartInfo(exchange);
        // 地市编码转换
        boolean isString = certInfo.get("msg") instanceof String;
        Map msg = isString ? JSON.parseObject((String) certInfo.get("msg")) : (Map) certInfo.get("msg");
        Object orgCity = msg.get("city");
        msg.put("city", ChangeCodeUtils.changeEparchy(msg));
        certInfo.put("msg", msg);
        // 获取tradeid、itemid
        Map tradeMap = new HashMap();
        MapUtils.arrayPut(tradeMap, msg, COPYARRAY);
        tradeMap.put("city", orgCity);
        Exchange getItemIdExchange = ExchangeUtils.ofCopy(exchange, tradeMap);
        Exchange getTradeIdExchange = ExchangeUtils.ofCopy(exchange, tradeMap);
        // Exchange getCustIDExchange = ExchangeUtils.ofCopy(exchange, tradeMap);
        String tradeId = GetSeqUtil.getSeqFromCb(getTradeIdExchange, "seq_trade_id");
        String itemId = GetSeqUtil.getSeqFromCb(getItemIdExchange, "seq_item_id");
        // String custId = GetSeqUtil.getSeqFromCb(getCustIDExchange, "seq_cust_id");
        // 调预提交准备参数
        Map preDateMap = MapUtils.asMap("itemId", itemId, "tradeId", tradeId, "operatorId", msg.get("operatorId"),
                "city", msg.get("city"), "province", msg.get("province"), "newCustId", custId, "district",
                msg.get("district"), "channelId", msg.get("channelId"), "channelType", msg.get("channelType"),
                "number", msg.get("number"), "customerName", msg.get("customerName"), "certNum", msg.get("certNum"),
                "psptAddr", msg.get("psptAddr"), "appCode", exchange.getAppCode());
        preDateMap.putAll(threePartInfoMap);
        preSub4GData(preDateMap);
        Exchange exchange4CB = ExchangeUtils.ofCopy(exchange, preDateMap);
        Map preSubRetMap = Maps.newHashMap();
        try {
            new LanUtils().preData(pmp[0], exchange4CB);// "ecaop.trades.sccc.cancelPre.paramtersmapping"
            CallEngine.wsCall(exchange4CB, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            new LanUtils().xml2Json("ecaop.trades.sccc.cancelPre.template", exchange4CB);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用预提交失败！" + e.getMessage());
        }
        preSubRetMap = exchange4CB.getOut().getBody(Map.class);

        // 正式提交准备参数
        orderSubPreData(preSubRetMap, preDateMap);
        Exchange exchange4cBOrderSub = ExchangeUtils.ofCopy(exchange, preDateMap);
        // System.out.print("有没有feiyong！------------------------------------------------------------！" + preDateMap);
        try {
            new LanUtils().preData("ecaop.trades.mpsb.ParametersMapping", exchange4cBOrderSub);
            CallEngine.wsCall(exchange4cBOrderSub, "ecaop.comm.conf.url.cbss.services.orderSub");
            new LanUtils().xml2Json("ecaop.trades.mpsb.template", exchange4cBOrderSub);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用正式提交失败！" + e.getMessage());
        }
        Map retMap = new HashMap();
        exchange.getOut().setBody(retMap);

    }

    /**
     * 正式提交参数准备
     * @param preSubRetMap
     * @param preDateMap
     */
    private void orderSubPreData(Map preSubRetMap, Map preDateMap) {
        List<Map> rspInfo = (List<Map>) preSubRetMap.get("rspInfo");
        if (null == rspInfo || rspInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "cBSS统一预提交未返回应答信息");
        }
        String provOrderId = String.valueOf(rspInfo.get(0).get("provOrderId"));
        String orderNo = String.valueOf(preDateMap.get("tradeId"));
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
        payInfoMap.put("payType", "10");
        payInfoMap.put("subProvinceOrderId", provOrderId);
        payInfoList.add(payInfoMap);
        preDateMap.put("provOrderId", provOrderId);// 预提交返回订单
        preDateMap.put("orderNo", orderNo);// 外围系统订单
        preDateMap.put("origTotalFee", totalFee);
        preDateMap.put("subOrderSubReq", subOrderSubReqList);
        preDateMap.put("payInfo", payInfoList);

    }

    /**
     * 处理预提交返回费用项
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

    /**
     * 三户接口获取客户、产品、实名标志
     * @param exchange
     * @return
     * @throws Exception
     */
    private Map threePartInfo(Exchange exchange) throws Exception {
        Map exc = exchange.getIn().getBody(Map.class);
        boolean isString = exc.get("msg") instanceof String;
        Map msg = isString ? JSON.parseObject((String) exc.get("msg")) : (Map) exc.get("msg");
        msg.put("serialNumber", msg.get("number"));
        exc.put("msg", msg);
        exchange.getIn().setBody(exc);
        Map retMap = new GetThreePartInfoUtils().process(exchange);
        Map preDateMap = new HashMap();
        List<Map> retAcctList = (ArrayList<Map>) retMap.get("acctInfo");
        if (null == retAcctList || 0 == retAcctList.size()) {
            throw new EcAopServerBizException("9999", "三户信息校验接口:核心系统未返回账户信息");
        }
        List<Map> retUserList = (ArrayList<Map>) retMap.get("userInfo");
        if (null == retUserList || 0 == retUserList.size()) {
            throw new EcAopServerBizException("9999", "三户信息校验接口:核心系统未返回用户信息");
        }
        List<Map> reCusttList = (ArrayList<Map>) retMap.get("custInfo");
        if (null == reCusttList || 0 == reCusttList.size()) {
            throw new EcAopServerBizException("9999", "三户信息校验接口:核心系统未返回客户信息");
        }
        List<Map> paraList = (List<Map>) retMap.get("para");
        if (null == paraList || 0 == paraList.size()) {
            throw new EcAopServerBizException("9999", "三户信息校验接口:核心系统未返回客户认证信息");
        }
        Map custInfo = reCusttList.get(0);
        preDateMap.put("userId", retUserList.get(0).get("userId"));
        preDateMap.put("acctId", retAcctList.get(0).get("acctId"));
        preDateMap.put("productId", retUserList.get(0).get("productId"));
        for (int i = 0; i < paraList.size(); i++) {
            if ("CERT_TAG".equals(paraList.get(i).get("paraId"))) {
                preDateMap.put("checkType", paraList.get(i).get("paraValue"));
            }
        }
        preDateMap.putAll(custInfo);
        return preDateMap;
    }

    private void preSub4GData(Map inputMap) {
        Map ext = preExt(inputMap);
        inputMap.put("ext", ext);
        Map base = preBase(inputMap);
        inputMap.put("base", base);
        inputMap.put("ordersId", inputMap.get("tradeId"));
        inputMap.put("operTypeCode", "0");

    }

    private Map preBase(Map inputMap) {
        Map base = new HashMap();
        // TODO:base节点下ALL_INFO
        // base.put("TRADE_DEPART_ID", "50a0030");
        // base.put("standardKindCode", "无");
        // base.put("TRADE_INFO_TAG_SET", "");
        // base.put("ROUTE_EPARCHY", "");
        // base.put("NOT_OWE_TAG", "0");
        // base.put("RIGHT_CODE", "csChangeCustOwnerCustOnlyTrade");
        // base.put("TRADE_JUDGE_OWE_TAG", "1");
        // base.put("BLACK_USER_TAG", "0");
        // base.put("PRIORITY", "0");
        // base.put("OWE_FEE_RSRV_NUM3", "0");
        // base.put("OWE_FEE_RSRV_NUM2", "0");
        // base.put("OWE_FEE_RSRV_NUM1", "0");
        // base.put("ATTR_TYPE_CODE", "0");
        // base.put("TRADE_ATTR", "1");
        // base.put("TRADE_TYPE", "1");
        // base.put("TRADE_TAG_SET", "00104100000000000000");
        // base.put("OPEN_DATE", "20151111154013");
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
        base.put("netTypeCode", "0050");
        base.put("custName", inputMap.get("custName"));
        base.put("checktypeCode", "0");
        base.put("brandCode", "4G00");
        base.put("tradeStaffId", inputMap.get("operatorId"));
        base.put("acceptDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("productId", inputMap.get("productId"));
        base.put("inModeCode", "E");
        base.put("serinalNamber", inputMap.get("number"));
        base.put("ITEM_ID", "");
        base.put("areaCode", inputMap.get("city"));
        base.put("custId", inputMap.get("custId"));
        base.put("acctId", inputMap.get("acctId"));
        base.put("userId", inputMap.get("userId"));
        base.put("acceptMonth", RDate.getMonth());
        base.put("startAcycId", RDate.currentTimeStr("yyyyMM"));
        base.put("eparchyCode", inputMap.get("city"));
        base.put("subscribeId", inputMap.get("tradeId"));
        return base;
    }

    private Map preExt(Map inputMap) {
        Map ext = new HashMap();
        ext.put("tradeItem", preTradeItem(inputMap));
        ext.put("tradeSubItem", preTradeSubItem(inputMap));
        // FIXME:非要更新证件信息 by wangmc 20180522
        if (!IsEmptyUtils.isEmpty(inputMap.get("psptAddr"))) {
            ext.put("tradeCustPerson", preTradeCustPerson(inputMap));
        }
        return ext;
    }

    /**
     * FIXME: 更新证件信息,暂时只处理证件地址字段 by wangmc 20180522
     * @param inputMap
     * @return
     */
    private Object preTradeCustPerson(Map inputMap) {
        Map custInfo = (Map) inputMap.get("custInfo");
        List<Map> items = new ArrayList<Map>();
        Map item = MapUtils.asMap("X_DATATYPE", "NULL", "psptAddr", inputMap.get("psptAddr"), "custId",
                inputMap.get("newCustId"));
        String[] getByCustInfoKey = new String[] { "phone", "contact", "removeTag", "folkCode", "contactPhone",
                "custName" };
        MapUtils.arrayPut(item, custInfo, getByCustInfoKey);
        // 证件号码
        item.put("psptId", custInfo.get("certCode"));
        item.put("psptTypeCode", custInfo.get("certTypeCode"));
        items.add(item);
        Map tradeCustPerson = MapUtils.asMap("item", items);
        return tradeCustPerson;
    }

    private Map preTradeSubItem(Map inputMap) {
        Map tradeSubItem = new HashMap();
        LanUtils lan = new LanUtils();
        List<Map> items = new ArrayList<Map>();
        items.add(lan.createTradeSubItem("USER_NAME", inputMap.get("custName"), (String) inputMap.get("itemId")));
        // 实人认证新增非必传realPersonTag实人认证标识
        if (StringUtils.isNotEmpty((String) inputMap.get("realPersonTag"))) {
            items.add(lan.createTradeSubItem4("REAL_PERSON_TAG", "0", inputMap.get("realPersonTag"),
                    (String) inputMap.get("itemId")));
            items.add(lan.createTradeSubItem4("REAL_PERSON_TAG", "4", inputMap.get("realPersonTag"),
                    (String) inputMap.get("itemId")));
        }
        tradeSubItem.put("item", items);
        return tradeSubItem;
    }

    private Map preTradeItem(Map inputMap) {
        Map tradeItem = new HashMap();
        LanUtils lan = new LanUtils();
        List<Map> items = new ArrayList<Map>();
        items.add(lan.createAttrInfoNoTime("STANDARD_KIND_CODE", inputMap.get("city")));
        items.add(lan.createAttrInfoNoTime("ACCT_ONLY", "0"));
        items.add(lan.createAttrInfoNoTime("ORG_CUST_ID", inputMap.get("custId")));
        items.add(lan.createAttrInfoNoTime("ORG_USECUST_ID", inputMap.get("custId")));
        items.add(lan.createAttrInfoNoTime("CUST_ID", inputMap.get("newCustId")));
        items.add(lan.createAttrInfoNoTime("USECUST_ID", inputMap.get("newCustId")));
        items.add(lan.createAttrInfoNoTime("IS_INHERIT_SCORE", "0"));
        items.add(lan.createAttrInfoNoTime("USER_PASSWD", ""));
        // cb测试先让传0
        items.add(lan.createAttrInfoNoTime("CREATE_CUST", "0"));
        items.add(lan.createAttrInfoNoTime("BSS_NEED_CREATE_CUST", "0"));
        items.add(lan.createAttrInfoNoTime("CUST_TAG", "3"));
        // 实人认证新增非必传photoTag照片标识、onlineRealPersonTag线上实人认证标记
        if (StringUtils.isNotEmpty((String) inputMap.get("photoTag"))) {
            items.add(lan.createAttrInfoNoTime("PHOTO_TAG", inputMap.get("photoTag")));
        }
        if (StringUtils.isNotEmpty((String) inputMap.get("onlineRealPersonTag"))) {
            items.add(lan.createAttrInfoNoTime("ONLINE_REAL_PERSON_TAG", inputMap.get("onlineRealPersonTag")));
        }
        tradeItem.put("item", items);
        return tradeItem;
    }

    /**
     * 北六客户资料检验返回判断
     * @param exchange
     */
    private String dealCertRsp(Exchange exchange) {
        Map tempMap = new HashMap();
        Object body = exchange.getOut().getBody();
        if (body instanceof String) {
            tempMap = JSON.parseObject((String) body);
        }
        else {
            tempMap = exchange.getOut().getBody(Map.class);
        }
        System.out.println("fjksdkfjkjklfjkfjkjjnnnm" + tempMap);
        if ("0001".equals(tempMap.get("code"))) {
            throw new EcAopServerBizException("9999", "cBSS系统中无此证件信息，请到营业厅办理");
        }
        List<Map> custInfoList = (List<Map>) tempMap.get("custInfo");
        if (custInfoList.size() > 1) {
            throw new EcAopServerBizException("9999", "cBSS系统有多个客户，请到营业厅办理");
        }

        return String.valueOf(custInfoList.get(0).get("custId"));
    }

    /**
     * 客户资料校验准备参数
     * @param body
     */
    private void preParam(Map body) {
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        msg.put("checkType", "0");// 默认用证件校验
        // TODO:证件外围默认传18位身份证
        msg.put("certType", "02");
        msg.put("opeSysType", "2");
        body.put("msg", msg);

    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
