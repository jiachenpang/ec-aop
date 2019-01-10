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
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;

@EcRocTag("MainNumberCreate")
public class MainNumberCreate extends BaseAopProcessor implements ParamsAppliable {
    //     1.三户信息2.预提交3.正式提交
    private static final String[] PARAM_ARRAY = { "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.trades.sccc.cancelPre.paramtersmapping", "ecaop.trades.sccc.cancel.paramtersmapping",
            "ecaop.masb.chph.gifa.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];
    private final LanUtils lan = new LanUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        // TODO Auto-generated method stub、
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        Map threePartOut = callThreePart(exchange, msg);
        Map preSubmitMap = callPreSubmit(exchange, msg, threePartOut);
        Map submitMap = Maps.newHashMap();
        MapUtils.arrayPut(submitMap, msg, MagicNumber.COPYARRAY);
        preOrderSubMessage(preSubmitMap, submitMap, msg);
        Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMap);
        lan.preData(pmp[2], submitExchange);
        CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        lan.xml2Json("ecaop.trades.sccc.cancel.template", submitExchange);
    }

    /**
     * 调用CBSS三户接口、获取相关信息
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
        threePartMap.put("serialNumber", msg.get("serialNumberB"));
        threePartMap.put("getMode", "111001000000101311110000000000");
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        lan.preData(pmp[0], threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        return threePartExchange.getOut().getBody(Map.class);
    }

    /**
     * 调用预提交
     * 
     * @param exchange
     * @param msg
     * @return
     * @throws Exception
     */
    private Map callPreSubmit(Exchange exchange, Map msg, Map threePartOut) {
        System.out.println("请求tradeid开始了");
        String tradeId = (String) GetSeqUtil.getSeqFromCb(pmp[3], ExchangeUtils.ofCopy(exchange, msg),
                "seq_trade_id", 1).get(0);
        System.out.println("请求tradeid结束了");
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
        System.out.println("请求asMap开始了");
        Map preDateMap = MapUtils.asMap("tradeId", tradeId, "newCustId", custInfo.get("custId"), "productId",
                userInfo.get("productId"), "serviceClassCode", userInfo.get("serviceClassCode"), "district",
                userInfo.get("cityCode"), "custName", custInfo.get("custName"),
                "brandCode", userInfo.get("brandCode"), "city", userInfo.get("eparchyCode"), "custId",
                custInfo.get("custId"), "userId", userInfo.get("userId")
                , "acctId", acctInfo.get("acctId"));
        MapUtils.arrayPut(preDateMap, msg, MagicNumber.COPYARRAY);
        preDateMap.put("base", preBase(msg, preDateMap));
        preDateMap.put("ext", preExt(msg, preDateMap));
        preDateMap.put("ordersId", preDateMap.get("tradeId"));
        preDateMap.put("operTypeCode", "0");
        Exchange preSubmitExchange = ExchangeUtils.ofCopy(exchange, preDateMap);
        try {
            System.out.println("预提交请求开始调用++++++");
            lan.preData(pmp[1], preSubmitExchange);
            CallEngine.wsCall(preSubmitExchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            lan.xml2Json("ecaop.trades.sccc.cancelPre.template", preSubmitExchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用预提交失败！" + e.getMessage());
        }
        System.out.println("++++++++++++++++++预提交请求成功！");
        return preSubmitExchange.getOut().getBody(Map.class);
    }

    /**
     * 拼装BASE节点信息
     * @param inputMap
     * @param appCode
     * @return
     */
    private Map preBase(Map msg, Map inputMap) {
        Map base = new HashMap();
        base.put("advancePay", "0");
        base.put("userDiffCode", "00");
        base.put("acceptDate", GetDateUtils.getDate());
        base.put("inModeCode", "E");
        base.put("productId", inputMap.get("productId"));
        base.put("serinalNamber", msg.get("serialNumberB"));
        base.put("usecustId", inputMap.get("newCustId"));
        base.put("actorCertNum", "");
        base.put("remark", "");
        base.put("feeState", "");
        base.put("contactPhone", "");
        base.put("nextDealTag", "Z");
        base.put("contactAddress", "");
        base.put("olcomTag", "0");
        base.put("custId", inputMap.get("custId"));
        base.put("acctId", inputMap.get("acctId"));
        base.put("foregift", "0");
        base.put("execTime", GetDateUtils.getDate());
        base.put("termIp", "10.124.3.14");
        base.put("actorCertTypeId", "");
        base.put("chkTag", "0");
        base.put("tradeId", inputMap.get("tradeId"));
        base.put("actorPhone", "");
        base.put("operFee", "0");
        base.put("tradeTypeCode", "0250");
        base.put("cancelTag", "0");
        base.put("cityCode", inputMap.get("district"));
        base.put("userId", inputMap.get("userId"));
        base.put("eparchyCode", new ChangeCodeUtils().changeEparchyUnStatic(msg));
        base.put("netTypeCode", inputMap.get("serviceClassCode"));
        base.put("contact", "");
        base.put("custName", inputMap.get("custName"));
        base.put("feeStaffId", "");
        base.put("checktypeCode", "8");
        base.put("subscribeId", inputMap.get("tradeId"));
        base.put("brandCode", inputMap.get("brandCode"));
        base.put("actorName", "");
        return base;
    }

    /**
     * 拼装EXT节点信息
     * @param inMap
     * @return
     */
    private Map preExt(Map msg, Map preMap) {
        Map ext = new HashMap();
        ext.put("tradeItem", preTradeItem(msg));
        System.out.println("++trdeItem" + ext);
        ext.put("tradeRelation", preTradeRelation(msg, preMap));
        System.out.println("tradeRelation" + ext);
        return ext;
    }

    /**
     * 拼装tradeRelation节点
     * @param inMap
     * @return
     */
    private Map preTradeRelation(Map msg, Map preMap) {
        Map tradeRelation = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("startDate", GetDateUtils.getDate());
        item.put("modifyTag", "0");
        item.put("serialNumberA", msg.get("serialNumberA"));
        item.put("serialNumberB", msg.get("serialNumberB"));
        item.put("endDate", "2050-12-31 23:59:59");
        item.put("relationTypeCode", msg.get("relationTypeCode"));
        item.put("xDatatype", "NULL");
        item.put("roleCodeA", "0");
        item.put("roleCodeB", "1");
        item.put("idA", msg.get("userIdA"));
        item.put("idB", preMap.get("userId"));
        itemList.add(item);
        tradeRelation.put("item", itemList);
        return tradeRelation;
    }

    /**
     * 拼装tradeItem节点
     * @param inMap
     * @return
     */
    private Map preTradeItem(Map msg) {
        Map tradeItem = new HashMap();
        LanUtils lan = new LanUtils();
        List<Map> items = new ArrayList<Map>();
        items.add(lan.createAttrInfoNoTime("DEVELOP_STAFF_ID", msg.get("developStaffId")));
        items.add(lan.createAttrInfoNoTime("DEVELOP_DEPART_ID", msg.get("developDepartId")));
        tradeItem.put("item", items);
        return tradeItem;
    }

    /**
     * 正式提交
     * @param exchange
     * @param msg
     * @param preSubmitMap
     * @throws Exception
     */
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
                List<Map> fees = new ArrayList<Map>();
                if (null != feeInfos && 0 != feeInfos.size()) {
                    for (Map feeInfo1 : feeInfos) {
                        Map feeInfo = new HashMap();
                        String calculateId = GetSeqUtil.getSeqFromCb();
                        feeInfo.put("feeId", feeInfo1.get("feeTypeCode"));
                        feeInfo.put("feeCategory", feeInfo1.get("feeMode"));
                        feeInfo.put("feeDes", feeInfo1.get("feeTypeName"));
                        feeInfo.put("origFee", Integer.valueOf((String) feeInfo1.get("fee")));
                        feeInfo.put("realFee", Integer.valueOf((String) feeInfo1.get("oldFee")));
                        feeInfo.put("operateType", "1");
                        feeInfo.put("calculateTag", "N");
                        feeInfo.put("isPay", "1");
                        feeInfo.put("payTag", "1");
                        feeInfo.put("calculateId", calculateId);
                        feeInfo.put("calculateDate", GetDateUtils.getDate());
                        feeInfo.put("payId", calculateId);
                        fees.add(feeInfo);
                    }
                    subOrderSubReq.put("feeInfo", fees);
                }
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

    @Override
    public void applyParams(String[] params) {
        // TODO Auto-generated method stub
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }

    }

}
