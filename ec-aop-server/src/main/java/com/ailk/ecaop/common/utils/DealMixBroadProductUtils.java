package com.ailk.ecaop.common.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.user.CheckUserTransferFixedAllProcessor;
import com.ailk.ecaop.common.helper.MagicNumber;

@SuppressWarnings(value = { "unchecked", "rawtypes" })
public class DealMixBroadProductUtils {

    public Map preMixBroadProduct(Map inMap, Exchange exchange, ParametersMappingProcessor[] pmp) {
        if (null == inMap.get("certType") || "".equals(inMap.get("certType"))) {
            throw new EcAopServerBizException("9999", "固网成员下,证件类型为空,请检查");
        }
        if (null == inMap.get("certNum") || "".equals(inMap.get("certNum"))) {
            throw new EcAopServerBizException("9999", "固网成员下,证件号码为空,请检查");
        }
        Object installMode = inMap.get("installMode");
        Map tradeMap = (Map) inMap.get("tradeMap");
        if ("0".equals(inMap.get("custType"))) {
            inMap.put("custId",
                    GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, tradeMap), "seq_cust_id", 1).get(0));
        }
        if (!"1".equals(installMode)) {
            inMap.put("userId",
                    GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, tradeMap), "seq_user_id", 1).get(0));
        }
        // inMap.put("acctId", GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, tradeMap), "seq_acct_id"));

        // 转换productMode 为了用公用类 2016-05-27 lixl
        Map broadBandTemplate = (Map) inMap.get("broadBandTemplate");
        List<Map> productInfo = (List<Map>) broadBandTemplate.get("broadBandProduct");
        // 固网新装和纳入只要有活动都要处理 RBJ2018072600033 zhaok
        List<Map> activityInfo = (List<Map>) broadBandTemplate.get("activityInfo");
        if (!IsEmptyUtils.isEmpty(activityInfo) && ("0".equals(installMode) || "1".equals(installMode))) {
            inMap.put("activityInfo", activityInfo);
        }
        ELKAopLogger.logStr("固网新装和纳入处理活动 ： " + inMap + " , broadBandTemplate :" + broadBandTemplate);
        for (Map prod : productInfo) {
            if ("1".equals(prod.get("productMode"))) {
                prod.put("productMode", "0");
            }
            else if ("2".equals(prod.get("productMode"))) {
                prod.put("productMode", "1");
            }
        }

        if ("0".equals(installMode)) {
            Map broadBandInfo = (Map) inMap.get("broadBandTemplate");
            String isSerial = (String) broadBandInfo.get("isSerial");
            if (isSerial != null && isSerial != "" && isSerial == "1") {
                String shareSerialNumber = (String) broadBandInfo.get("shareSerialNumber");
                if (shareSerialNumber != null && shareSerialNumber != "") {
                    Map threePartMap = new HashMap();
                    MapUtils.arrayPut(threePartMap, inMap, MagicNumber.COPYARRAY);
                    threePartMap.put("tradeTypeCode", "9999");
                    threePartMap.put("serialNumber", shareSerialNumber);
                    threePartMap.put("getMode", "1111111111100013010000000100001");
                    Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
                    LanUtils lan = new LanUtils();
                    lan.preData(pmp[1], threePartExchange);
                    try {
                        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
                        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
                    }
                    catch (Exception e) {
                        throw new EcAopServerBizException("9999", e.getMessage());
                    }
                    Map out = threePartExchange.getOut().getBody(Map.class);
                    String code = (String) out.get("code");
                    if (!"0000".equals(code)) {
                        throw new EcAopServerBizException("9999", "共线号码异常,请检查");
                    }
                    inMap.put("shareSerialNumber", shareSerialNumber);
                }
                else {
                    throw new EcAopServerBizException("9999", "请输入共线号码");
                }
            }

            return preBroadOpenData(inMap, exchange);
        }
        else if ("1".equals(installMode)) {
            Map threePartMap = new HashMap();
            MapUtils.arrayPut(threePartMap, inMap, MagicNumber.COPYARRAY);
            threePartMap.put("tradeTypeCode", "9999");
            threePartMap.put("serialNumber", inMap.get("serialNumber"));
            threePartMap.put("getMode", "1111111111100013010000000100001");
            Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
            LanUtils lan = new LanUtils();
            lan.preData(pmp[1], threePartExchange);
            try {
                CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
                lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
            }
            catch (Exception e) {
                throw new EcAopServerBizException("9999", e.getMessage());
            }
            Map out = threePartExchange.getOut().getBody(Map.class);
            List<Map> userInfo = (ArrayList<Map>) out.get("userInfo");
            List<Map> custInfo = (ArrayList<Map>) out.get("custInfo");
            List<Map> oldproductinfo = (ArrayList<Map>) userInfo.get(0).get("productInfo");
            String oldproductid = userInfo.get(0).get("productId").toString();
            for (Map oldprod : oldproductinfo) {
                if (oldproductid.equals(oldprod.get("productId"))) {
                    inMap.put("oldStartDate", oldprod.get("productActiveTime"));
                    break;
                }
            }
            inMap.put("userId", userInfo.get(0).get("userId"));
            inMap.put("oldProduct", userInfo.get(0).get("productId"));// 不能改成oldProductId 切记
            inMap.put("oldProductTypeCode", userInfo.get(0).get("productTypeCode"));
            inMap.put("oldBrandCode", userInfo.get(0).get("brandCode"));
            inMap.put("cityCode", userInfo.get(0).get("cityCode"));
            List<Map> discntInfo = (List<Map>) userInfo.get(0).get("discntInfo");
            inMap.put("OldDiscntInfo", discntInfo);
            inMap.put("oldServiceInfo", userInfo.get(0).get("svcInfo"));
            if (discntInfo != null && discntInfo.size() > 0) {
                inMap.put("oldItemId", discntInfo.get(0).get("itemId"));
            }
            inMap.put("custId", custInfo.get(0).get("custId"));
            inMap.put("customerName", custInfo.get(0).get("custName"));
            return preBroadChangeData(inMap, exchange);
        }
        else {
            return preBroadTransData(inMap, exchange, pmp);
        }
    }

    /**
     * 准备固网、宽带新装参数
     * @param inMap
     * @param exchange
     * @return
     */
    private Map preBroadOpenData(Map inMap, Exchange exchange) {
        Map retMap = new HashMap();
        MixOpenUtils mou = new MixOpenUtils();
        Map ext = mou.preExt(inMap, exchange);
        Map base = mou.preBase(inMap, ext);
        retMap.put("base", base);
        retMap.put("ext", ext);
        retMap.put("operTypeCode", "0");
        retMap.put("ordersId", inMap.get("subscribeId"));
        retMap.put("serviceClassCode", "00CP");
        retMap.put("serinalNamber", ((Map) inMap.get("broadBandTemplate")).get("acctSerialNumber"));
        return retMap;
    }

    private Map preBroadChangeData(Map inMap, Exchange exchange) {
        Map retMap = new HashMap();
        MixOpenUtils mou = new MixOpenUtils();
        Map ext = mou.preExt(inMap, exchange);
        Map base = mou.preBase(inMap, ext);
        retMap.put("base", base);
        retMap.put("ext", ext);
        retMap.put("operTypeCode", "0");
        retMap.put("ordersId", inMap.get("subscribeId"));
        retMap.put("serinalNamber", ((Map) inMap.get("broadBandTemplate")).get("acctSerialNumber"));
        retMap.put("serviceClassCode", "00CP");
        return retMap;
    }

    private Map preBroadTransData(Map inMap, Exchange exchange, ParametersMappingProcessor[] pmp) {
        Map retMap = new HashMap();
        MixOpenUtils mou = new MixOpenUtils();
        inMap.putAll(check2324(inMap, exchange));
        putItem(inMap, createItemId(inMap, exchange, 3, pmp));
        Map ext = mou.preExt(inMap, exchange);
        Map base = mou.preBase(inMap, ext);
        retMap.put("base", base);
        retMap.put("ext", ext);
        retMap.put("operTypeCode", "0");
        retMap.put("ordersId", inMap.get("subscribeId"));
        retMap.put("serinalNamber", ((Map) inMap.get("broadBandTemplate")).get("acctSerialNumber"));
        retMap.put("serviceClassCode", "00CP");
        return retMap;
    }

    private Map check2324(Map inMap, Exchange exchange) {
        CheckUserTransferFixedAllProcessor cu = new CheckUserTransferFixedAllProcessor();
        Map tradeMap = (Map) inMap.get("tradeMap");
        tradeMap.put("tradeTypeCode", "0010");
        tradeMap.put("numId", inMap.get("serialNumber"));
        Map broadBandTemplate = (Map) inMap.get("broadBandTemplate");
        tradeMap.put("serClassCode",
                new MixOpenUtils().CommodityType2NetTypeCode(broadBandTemplate.get("broadBandType")));
        Exchange exchangePre = ExchangeUtils.ofCopy(exchange, tradeMap);
        try {
            cu.applyParams(null);
            cu.process(exchangePre);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用23转4开户处理申请异常:" + e.getMessage());
        }
        Map bodyBack = exchangePre.getOut().getBody(Map.class);
        return bodyBack;
    }

    private List<String> createItemId(Map inMap, Exchange exchange, int count, ParametersMappingProcessor[] pmp) {
        Map tradeMap = (Map) inMap.get("tradeMap");
        Exchange getItemIdExchange = ExchangeUtils.ofCopy(exchange, tradeMap);
        List<String> itemIdList = GetSeqUtil.getSeqFromCb(pmp[0], getItemIdExchange, "seq_trade_id", count);
        return itemIdList;
    }

    private void putItem(Map inMap, List<String> itemList) {
        for (int i = 0; i < itemList.size(); i++) {
            inMap.put("itemId" + (i + 1), itemList.get(i));
        }
    }
}
