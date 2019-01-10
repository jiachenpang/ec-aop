package com.ailk.ecaop.biz.year;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.lang.RDate;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.DateUtils;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.common.utils.YearPayN6Utils;
import com.alibaba.fastjson.JSON;

public class YearForHLJ {

    protected ParametersMappingProcessor[] pmp = null;

    public YearForHLJ(ParametersMappingProcessor[] pmp) {
        this.pmp = pmp;
    }

    private final LanUtils lan = new LanUtils();
    private String apptx;
    private final String ERROR_CODE = "8888";

    public void process(Exchange exchange, Map msg, Map threePartInfo) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        apptx = (String) body.get("apptx");
        boolean flag = true;
        if ("1".equals(msg.get("changeTag")) || IsEmptyUtils.isEmpty(msg.get("changeTag"))) {
            flag = false;
            // 虚拟用户预提交
            Map mixMap = preMixNumberInfo(exchange, msg, flag);
            // 成员用户预提交
            Map phoneMap = prePhoneNumberInfo(exchange, msg, threePartInfo, flag);
            // 正式提交
            orderSub(mixMap, phoneMap, exchange, msg);
        }
        else if ("2".equals(msg.get("changeTag"))) {
            // 虚拟用户预提交
            Map mixMap = preMixNumberInfo(exchange, msg, flag);
            // 成员用户预提交
            Map phoneMap = prePhoneNumberInfo(exchange, msg, threePartInfo, flag);
            // 正式提交接口
            orderChgSub(exchange, mixMap, phoneMap, msg);
        }
        else if ("3".equals(msg.get("changeTag"))) {
            throw new EcAopServerBizException(ERROR_CODE, "不支持非主产品变更,请核实");
        }
        else {
            throw new EcAopServerBizException(ERROR_CODE, "产品变更方式[" + msg.get("changeTag") + "]不在[1.趸交;2.变更产品;3.变更非主产品");
        }
    }

    private void orderSub(Map mixMap, Map phoneMap, Exchange exchange, Map msg) throws Exception {
        Map submitMap = new HashMap();
        MapUtils.arrayPut(submitMap, msg, MagicNumber.COPYARRAY);
        Object orderNo = msg.get("orderNo");
        submitMap.put("orderNo", orderNo);
        dealSubmit(submitMap, mixMap, phoneMap, msg);
        List<Map> payList = new ArrayList<Map>();
        Map pay = new HashMap();
        if (!IsEmptyUtils.isEmpty((List<Map>) msg.get("payInfo"))) {
            List<Map> payInfoList = (List<Map>) msg.get("payInfo");
            for (Map payInfo : payInfoList) {
                pay.put("payType", ChangeCodeUtils.changePayType4N6odsb(payInfo.get("payType")));
                pay.put("payMoney", submitMap.get("origTotalFee"));
                pay.put("subProvinceOrderId", msg.get("provOrderId"));
            }
            payList.add(pay);
            submitMap.put("payInfo", payList);
        }
        Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMap);
        lan.preData(pmp[4], submitExchange);
        try {
            CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.osn.services.ordser");
            lan.xml2Json("ecaop.masb.odsb.template", submitExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        if ("bymc".equals(msg.get("tradeMethod"))) {
            Map outMap = new HashMap();
            outMap.put("code", "0000");
            outMap.put("detail", "OK");
            outMap.put("feeInfo", msg.get("retFee"));// 预提交返回的费用
            outMap.put("arrearageMess", msg.get("arrearageMess"));
            outMap.put("totalFee", String.valueOf(submitMap.get("origTotalFee")));
            outMap.put("provOrderId", msg.get("provOrderId"));
            outMap.put("provOrderId2", msg.get("provOrderId2"));
            outMap.put("custName", msg.get("custName"));
            outMap.put("productType", "1");
            outMap.put("productName", msg.get("productName"));
            outMap.put("discntName", msg.get("discntName"));
            outMap.put("startDate", msg.get("yyStartDate"));
            outMap.put("endDate", msg.get("yyEndDate"));
            Message out = new DefaultMessage();
            out.setBody(outMap);
            exchange.setOut(out);
        }
        else {
            Map outMap = new HashMap();
            outMap.put("code", "0000");
            outMap.put("detail", "OK");
            outMap.put("orderNo", orderNo);
            outMap.put("provOrderId", msg.get("provOrderId"));
            outMap.put("provOrderId2", msg.get("provOrderId2"));
            Message out = new DefaultMessage();
            out.setBody(outMap);
            exchange.setOut(out);
        }
    }

    private void orderChgSub(Exchange exchange, Map mixMap, Map phoneMap, Map msg) {
        Map submitMap = new HashMap();
        MapUtils.arrayPut(submitMap, msg, MagicNumber.COPYARRAY);
        Object orderNo = msg.get("orderNo");
        submitMap.put("orderNo", orderNo);
        dealSubmit(submitMap, mixMap, phoneMap, msg);
        List<Map> payList = new ArrayList<Map>();
        Map pay = new HashMap();
        if (!IsEmptyUtils.isEmpty((List<Map>) msg.get("payInfo"))) {
            List<Map> payInfoList = (List<Map>) msg.get("payInfo");
            for (Map payInfo : payInfoList) {
                pay.put("payType", ChangeCodeUtils.changePayType4N6odsb(payInfo.get("payType")));
                pay.put("payMoney", submitMap.get("origTotalFee"));
                pay.put("subProvinceOrderId", msg.get("provOrderId"));
            }
            payList.add(pay);
            submitMap.put("payInfo", payList);
        }
        Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[4], submitExchange);
        try {
            CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.osn.services.ordser");
            lan.xml2Json("ecaop.masb.odsb.template", submitExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        if ("bymc".equals(msg.get("tradeMethod"))) {
            Map outMap = new HashMap();
            outMap.put("code", "0000");
            outMap.put("detail", "OK");
            outMap.put("feeInfo", msg.get("retFee"));// 预提交返回的费用
            outMap.put("arrearageMess", msg.get("arrearageMess"));
            outMap.put("totalFee", String.valueOf(submitMap.get("origTotalFee")));
            outMap.put("provOrderId", msg.get("provOrderId"));
            outMap.put("provOrderId2", msg.get("provOrderId2"));
            outMap.put("custName", msg.get("custName"));
            outMap.put("productType", "1");
            outMap.put("productName", msg.get("productName"));
            outMap.put("discntName", msg.get("discntName"));
            outMap.put("startDate", phoneMap.get("startDate"));
            outMap.put("endDate", phoneMap.get("endDate"));
            Message out = new DefaultMessage();
            out.setBody(outMap);
            exchange.setOut(out);
        }
        else {
            Map outMap = new HashMap();
            outMap.put("code", "0000");
            outMap.put("detail", "OK");
            outMap.put("orderNo", orderNo);
            outMap.put("provOrderId", msg.get("provOrderId"));
            outMap.put("provOrderId2", msg.get("provOrderId2"));
            Message out = new DefaultMessage();
            out.setBody(outMap);
            exchange.setOut(out);
        }
    }

    /**
     * 整理正式提交的參數
     */
    private Map dealSubmit(Map submitMap, Map mixMap, Map phoneMap, Map msg) {
        List<Map> rspInfo = (ArrayList<Map>) mixMap.get("rspInfo");
        List<Map> phoneRspInfo = (ArrayList<Map>) phoneMap.get("rspInfo");
        Object provOrderId = "";
        Object provOrderId2 = rspInfo.get(0).get("bssOrderId");
        submitMap.put("provOrderId", provOrderId2);
        submitMap.put("orderNo", msg.get("orderNo"));
        if ("bymc".equals(msg.get("tradeMethod"))) {
            submitMap.put("operationType", "02");// 02-订单取消
        }
        List<Map> subOrderSubReq = new ArrayList<Map>();
        Integer totalFee = 0;
        List<Map> provinceOrderInfo = (ArrayList<Map>) rspInfo.get(0).get("provinceOrderInfo");
        if (!IsEmptyUtils.isEmpty(provinceOrderInfo)) {
            totalFee = Integer.valueOf(provinceOrderInfo.get(0).get("totalFee").toString());
        }
        subOrderSubReq.add(dealFeelInfo(rspInfo));
        for (Map rspMap : phoneRspInfo) {
            List<Map> provinceOrder = (List) rspMap.get("provinceOrderInfo");
            if (null != provinceOrder && provinceOrder.size() > 0) {
                for (Map orderInfo : provinceOrder) {
                    if (provOrderId2.equals(orderInfo.get("subProvinceOrderId"))) {
                        continue;
                    }
                    totalFee = totalFee + Integer.valueOf(orderInfo.get("totalFee").toString());
                    provOrderId = orderInfo.get("subOrderId");

                }
            }
        }
        List<Map> feeList = dealFee(phoneRspInfo);
        subOrderSubReq.add(dealFeelInfo(phoneRspInfo));
        msg.put("retFee", feeList);
        msg.put("provOrderId", provOrderId);
        msg.put("provOrderId2", provOrderId2);
        submitMap.put("subOrderSubReq", subOrderSubReq);
        submitMap.put("origTotalFee", totalFee);
        submitMap.put("cancleTotalFee", "0");
        return submitMap;
    }

    /**
     * 成员预提交
     */

    private Map prePhoneNumberInfo(Exchange exchange, Map msg, Map threePartInfo, Boolean flag) throws Exception {
        Exchange copyExchange = ExchangeUtils.ofCopy(exchange, msg);
        Map ext = new HashMap();
        List<Map> paraList = (List<Map>) threePartInfo.get("para");
        if (IsEmptyUtils.isEmpty(paraList)) {
            throw new EcAopServerBizException("9999", "北六ESS未返回PARA信息");
        }
        Object prodTariffCode = "";// 当前主产品资费编码
        Object prodTariffDesc = "";// 当前主产品资费名称
        Object bDiscntCode = "";// 资费编码
        String cycle = "12";
        String itemId = "";
        String itemId1 = (String) GetSeqUtil
                .getSeqFromCb(pmp[6], ExchangeUtils.ofCopy(exchange, msg), "seq_item_id", 1).get(0);
        msg.put("discntItemId", itemId1);
        for (Map para : paraList) {
            if ("PROD_TARIFF_CODE".equals(para.get("paraId"))) {
                prodTariffCode = para.get("paraValue");
            }
            if ("PROD_TARIFF_DESCRIBE".equals(para.get("paraId"))) {
                prodTariffDesc = para.get("paraValue");
            }
        }
        List<Map> userInfo = (List<Map>) threePartInfo.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfo)) {
            throw new EcAopServerBizException("9999", "用户信息未返回");
        }
        for (Map user : userInfo) {
            List<Map> discntInfo = (List<Map>) user.get("discntInfo");
            for (Map discnt : discntInfo) {
                if (!prodTariffCode.equals(discnt.get("discntCode"))) {
                    continue;
                }
                itemId = (String) discnt.get("itemId");
                bDiscntCode = discnt.get("discntCode");
                List<Map> attrInfo = (ArrayList<Map>) discnt.get("attrInfo");
                if (!IsEmptyUtils.isEmpty(attrInfo)) {
                    for (Map attr : attrInfo) {
                        if ("cycle".equals(attr.get("attrCode"))) {
                            cycle = (String) attr.get("attrValue");
                            msg.put("property_cycle", cycle);
                        }
                        else if ("fixedHire".equals(attr.get("attrCode"))) {
                            msg.put("fixedHire", attr.get("attrValue"));
                        }
                        else if ("cycleNum".equals(attr.get("attrCode"))) {
                            msg.put("property_cycleNum", attr.get("attrValue"));
                        }
                        else if ("aDiscntCode".equals(attr.get("attrCode"))) {
                            msg.put("use_aDiscntCode", attr.get("attrValue"));
                        }
                    }
                }

                String startDate = discnt.get("startDate").toString();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                String yyEndDate = sdf.format(RDate.addMonths(sdf.parse(startDate), Integer.valueOf(cycle.toString())));
                String yyStartDate = yyEndDate.substring(0, 8) + "000000";
                yyEndDate = sdf.format(RDate.addMonths(sdf.parse(yyStartDate), Integer.valueOf(cycle.toString())));
                yyEndDate = DateUtils.addSeconds(yyEndDate, -1);
                msg.put("itemId", itemId);
                msg.put("bDiscntCode", bDiscntCode);
                msg.put("discntPropertyEndDate", discnt.get("endDate"));// 资费属性的结束时间
                msg.put("oldStartData", startDate);
                msg.put("packageId", discnt.get("packageId"));
                msg.put("itemId1", itemId1);
                msg.put("discntName", prodTariffDesc);
                msg.put("yyStartDate", yyStartDate);
                msg.put("yyEndDate", yyEndDate);
            }
            List<Map> attrInfo = (List<Map>) user.get("attrInfo");
            if (!IsEmptyUtils.isEmpty(attrInfo)) {
                for (Map attr : attrInfo) {
                    if ("XZ_1".equals(attr.get("attrCode"))) {
                        msg.put("XZ_1", attr.get("attrValue"));
                    }
                    else if ("RWLX_27".equals(attr.get("attrCode"))) {
                        msg.put("RWLX_27", attr.get("attrValue"));
                    }
                    else if ("PROTOCOL_ID".equals(attr.get("attrCode"))) {
                        msg.put("PROTOCOL_ID", attr.get("attrValue"));
                    }
                    else if ("LINK_NAME".equals(attr.get("attrCode"))) {
                        msg.put("LINK_NAME", attr.get("attrValue"));
                    }
                    else if ("CONTACT_CONFIRM".equals(attr.get("attrCode"))) {
                        msg.put("CONTACT_CONFIRM", attr.get("attrValue"));
                    }
                    else if ("LINK_PHONE".equals(attr.get("attrCode"))) {
                        msg.put("LINK_PHONE", attr.get("attrValue"));
                    }
                    else if ("SL_5".equals(attr.get("attrCode"))) {
                        msg.put("SL_5", attr.get("attrValue"));
                    }
                }
            }
        }
        if (flag) {
            ELKAopLogger.logStr("appTx: " + apptx + "  进来了 ");
            preExt4PreSubmit(ext, threePartInfo, msg);
        }
        ext.put("tradeItem", preDataForTradeItem(msg, true, flag));
        ext.put("tradeSubItem", preDataForTradeSubItem(msg, flag));
        msg.put("ext", ext);
        Map base = preBaseData(msg, exchange.getAppCode(), false, flag);
        msg.put("base", base);
        msg.put("operTypeCode", "0");
        msg.put("ordersId", msg.get("orderId"));
        ELKAopLogger.logStr("调用成员预提交的msg: appTx: " + apptx + ", msg= " + msg);
        lan.preData(pmp[0], copyExchange);
        CallEngine.wsCall(copyExchange, "ecaop.comm.conf.url.OrdForNorthSer." + msg.get("province"));
        lan.xml2Json("ecaop.trades.spec.sUniTrade.template", copyExchange);

        Map retMap = copyExchange.getOut().getBody(Map.class);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (IsEmptyUtils.isEmpty(rspInfoList)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }
        retMap.put("startDate", ext.get("startDate"));
        retMap.put("endDate", ext.get("endDate"));
        return retMap;
    }

    /**
     * 虚拟预提交
     */
    private Map preMixNumberInfo(Exchange exchange, Map msg, Boolean flag) throws Exception {
        Exchange copyExchange = ExchangeUtils.ofCopy(exchange, msg);
        Map ext = new HashMap();
        ext.put("tradeUser", preDataForTradeUser(msg));
        ext.put("tradeItem", preDataForTradeItem(msg, false, flag));
        msg.put("ext", ext);
        Map base = preBaseData(msg, exchange.getAppCode(), true, flag);
        msg.put("base", base);
        msg.put("operTypeCode", "0");
        msg.put("ordersId", msg.get("orderId"));
        ELKAopLogger.logStr("调用虚拟预提交的msg: appTx: " + apptx + ", msg= " + msg);
        lan.preData(pmp[0], copyExchange);
        CallEngine.wsCall(copyExchange, "ecaop.comm.conf.url.OrdForNorthSer." + msg.get("province"));
        lan.xml2Json("ecaop.trades.spec.sUniTrade.template", copyExchange);

        Map retMap = copyExchange.getOut().getBody(Map.class);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (IsEmptyUtils.isEmpty(rspInfoList)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }
        return retMap;
    }

    private List<Map> dealFee(List<Map> rspInfo) {
        List<Map> retFeeList = new ArrayList<Map>();
        Map fee = new HashMap();
        for (Map rspMap : rspInfo) {
            List<Map> provinceOrderInfo = (List) rspMap.get("provinceOrderInfo");
            if (null != provinceOrderInfo && provinceOrderInfo.size() > 0) {
                for (Map provinceOrder : provinceOrderInfo) {
                    List<Map> feeInfo = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                    if (!IsEmptyUtils.isEmpty(feeInfo)) {
                        for (Map info : feeInfo) {
                            fee.put("operateType", info.get("operateType"));
                            fee.put("feeMode", info.get("feeMode"));
                            fee.put("feeTypeCode", info.get("feeTypeCode"));
                            fee.put("feeTypeName", info.get("feeTypeName"));
                            fee.put("maxDerateFee", info.get("maxDerateFee"));
                            fee.put("fee", info.get("fee"));
                        }
                        retFeeList.add(fee);
                    }
                }
            }
        }
        return retFeeList;
    }

    private Map dealFeelInfo(List<Map> rspInfo) {
        Map subOrderSubMap = new HashMap();
        String subProvinceOrderId = (String) rspInfo.get(0).get("bssOrderId");
        String subOrderId = (String) rspInfo.get(0).get("provOrderId");
        for (Map rspMap : rspInfo) {
            List<Map> provinceOrderInfo = (List) rspMap.get("provinceOrderInfo");
            if (null != provinceOrderInfo && provinceOrderInfo.size() > 0) {
                for (Map provinceOrder : provinceOrderInfo) {
                    List<Map> feeInfo = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                    if (!IsEmptyUtils.isEmpty(feeInfo)) {
                        for (Map fee : feeInfo) {
                            fee.put("feeCategory", fee.get("feeMode"));
                            fee.put("feeId", fee.get("feeTypeCode"));
                            fee.put("feeDes", fee.get("feeTypeName"));
                            fee.put("origFee", fee.get("oldFee"));
                            fee.put("realFee", fee.get("oldFee"));
                            fee.put("isPay", "0");
                            fee.put("calculateTag", "N");
                            fee.put("payTag", "1");
                            fee.put("calculateId", GetSeqUtil.getSeqFromCb());
                            fee.put("calculateDate", DateUtils.getDate());
                        }
                        subProvinceOrderId = (String) provinceOrder.get("subProvinceOrderId");
                        subOrderId = (String) provinceOrder.get("subOrderId");
                        subOrderSubMap.put("feeInfo", feeInfo);
                    }
                }
            }
        }
        subOrderSubMap.put("subProvinceOrderId", subProvinceOrderId);
        subOrderSubMap.put("subOrderId", subOrderId);
        return subOrderSubMap;
    }

    private Map preExt4PreSubmit(Map ext, Map threePartInfo, Map msg) throws Exception {
        ELKAopLogger.logStr("处理产品前的msg: appTx: " + apptx + ", msg= " + msg);
        List<Map> productInfo = (List<Map>) msg.get("productInfo");
        if (IsEmptyUtils.isEmpty(productInfo)) {
            throw new EcAopServerBizException("8888", "发起方未下发产品信息");
        }
        List<Map> oldTradeOther = new ArrayList<Map>();
        List<Map> oldTradeProduct = new ArrayList<Map>();
        List<Map> oldTradeProductType = new ArrayList<Map>();
        List<Map> newProductList = new ArrayList<Map>();
        String oldProductId = "";
        for (Map product : productInfo) {
            Object optType = product.get("optType");
            if ("1".equals(optType)) {
                oldProductId = (String) product.get("oldProductId");
                msg.put("oldProductId", oldProductId);
                Map backProdct = new YearPayN6Utils().dealBackProduct(product, threePartInfo);
                oldTradeOther.add((Map) backProdct.get("tradeOther"));
                oldTradeProductType.add((Map) backProdct.get("tradeProductType"));
                oldTradeProduct.add((Map) backProdct.get("tradeProduct"));
                ext.put("tradeOther", MapUtils.asMap("item", oldTradeOther));
            }
            else {
                msg.put("newProductId", product.get("oldProductId"));
                product.put("productId", product.get("oldProductId"));
                product.put("firstMonBillMode", "02");
                newProductList.add(product);
            }
        }
        Object oldProductStartDate = GetDateUtils.getDate();
        List<Map> threeInfo = (List<Map>) threePartInfo.get("userInfo");
        for (Map user : threeInfo) {
            oldProductStartDate = user.get("openDate");
            List<Map> threeProduct = (List<Map>) user.get("productInfo");
            if (IsEmptyUtils.isEmpty(threeProduct)) {
                continue;
            }
            for (Map prod : threeProduct) {
                if (oldProductId.equals(prod.get("productId"))) {
                    oldProductStartDate = prod.get("productActiveTime");
                    break;
                }
            }
        }
        msg.put("oldProductStartDate", oldProductStartDate);
        if (!IsEmptyUtils.isEmpty(newProductList)) {
            ext.putAll(TradeManagerUtils.preProductInfo(newProductList, "00" + msg.get("province"), msg));
        }
        // 宽带需要传入资费节点,重新塞入discnt节点,作为新增资费下发
        List<Map> discntList = new ArrayList<Map>();
        if (null != msg.get("discntList")) {
            discntList = (List<Map>) msg.get("discntList");
        }
        List<Map> broadDiscntInfo = (List<Map>) msg.get("broadDiscntInfo");
        if (null != broadDiscntInfo && !broadDiscntInfo.isEmpty()) {
            for (int j = 0; j < broadDiscntInfo.size(); j++) {
                Map disMap = new HashMap();
                Object broadDiscntId = broadDiscntInfo.get(j).get("broadDiscntId");
                msg.put("elementId", broadDiscntId);
                msg.put("PROVINCE_CODE", "00" + msg.get("province"));
                msg.put("EPARCHY_CODE", msg.get("eparchyCode"));
                msg.put("PRODUCT_ID", ext.get("mproductId"));
                disMap = TradeManagerUtils.preProductInfoByBroadDiscntId(msg);
                discntList.add(disMap);
            }

            if (null != discntList && !discntList.isEmpty()) {
                msg.put("discntList", discntList);
            }
            ext.put("tradeDiscnt", new YearPayN6Utils().preDiscntData(msg));
        }
        YearPayN6Utils ypN6U = new YearPayN6Utils();
        Map userInfo = ypN6U.getInfoFromThreePart(threePartInfo, "userInfo");
        List<Map> tradeProductTypeItem = (List<Map>) ((Map) ext.get("tradeProductType")).get("item");
        oldTradeProductType.addAll(tradeProductTypeItem);
        List<Map> tradeProductItem = (List<Map>) ((Map) ext.get("tradeProduct")).get("item");
        oldTradeProduct.addAll(tradeProductItem);
        List<Map> svcInfo = null;// (List<Map>) userInfo.get("svcInfo");
        List<Map> oldTradeSvcItem = new ArrayList<Map>();
        if (!IsEmptyUtils.isEmpty(svcInfo)) {
            for (Map svc : svcInfo) {
                oldTradeSvcItem.add(ypN6U.preTradeSvcBackItem(userInfo, svc));
            }
        }
        List<Map> tradeSvc = (List<Map>) ((Map) ext.get("tradeSvc")).get("item");
        for (Map svc : tradeSvc) {
            svc.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
        }
        oldTradeSvcItem.addAll(tradeSvc);
        for (Map prod : oldTradeProduct) {
            prod.put("userId", msg.get("userId"));
        }
        ext.put("tradeProductType", MapUtils.asMap("item", oldTradeProductType));
        ext.put("tradeProduct", MapUtils.asMap("item", oldTradeProduct));
        ext.put("tradeSvc", MapUtils.asMap("item", oldTradeSvcItem));
        Map tradeDiscnt = (Map) ext.get("tradeDiscnt");
        List<Map> tradeDiscntItemList = (List<Map>) tradeDiscnt.get("item");
        ext.put("tradeDiscnt", MapUtils.asMap("item", tradeDiscntItemList));
        List<Map> itemInfo = (List<Map>) tradeDiscnt.get("item");
        ext.put("startDate", itemInfo.get(0).get("startDate"));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        int brandNumber = Integer.valueOf((String) ((List<Map>) msg.get("productInfo")).get(0).get("brandNumber"));
        String endDate = sdf.format(RDate.addMonths(sdf.parse(ext.get("startDate").toString()), brandNumber));
        ext.put("endDate", DateUtils.addSeconds(endDate, -1));
        // 变更资费时只下发订购退订的资费节点
        if (oldProductId.equals(msg.get("newProductId"))) {
            ext.remove("tradeSvc");
            ext.remove("tradeOther");
            ext.remove("tradeProduct");
            ext.remove("tradeProductType");
            ext.remove("tradeSp");
            ext.put("tradeDiscnt", preForTradeDiscnt(msg, ext));
        }
        ELKAopLogger.logStr("处理产品后的ext: appTx: " + apptx + ", ext= " + ext);
        return ext;
    }

    private Map preForTradeDiscnt(Map msg, Map ext) {
        List<Map> disItem = new ArrayList<Map>();
        if (!IsEmptyUtils.isEmpty(ext.get("tradeDiscnt"))) {
            disItem = (List<Map>) ((Map) ext.get("tradeDiscnt")).get("item");
        }
        Map item = new HashMap();
        Map tradeDiscnt = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("id", msg.get("userId"));
        item.put("idType", "1");
        item.put("userIdA", "-1");
        item.put("productId", msg.get("oldProductId"));
        item.put("packageId", msg.get("packageId"));
        item.put("discntCode", msg.get("bDiscntCode"));
        item.put("specTag", "0");
        item.put("relationTypeCode", "");
        item.put("startDate", msg.get("oldStartData"));
        item.put("itemId", msg.get("itemId"));
        item.put("endDate", GetDateUtils.getMonthLastDayFormat());
        item.put("modifyTag", "1");
        disItem.add(item);
        tradeDiscnt.put("item", disItem);
        return tradeDiscnt;
    }

    private Map preDataForTradeSubItem(Map msg, Boolean flag) {
        List<Map> Item = new ArrayList<Map>();
        Map tradeSubItem = new HashMap();
        String startDate = GetDateUtils.getDate();
        String endDate = (String) msg.get("discntPropertyEndDate");
        String disItemId = (String) msg.get("itemId1");
        String itemId = (String) msg.get("itemId");
        String userId = (String) msg.get("userId");
        if (flag) {
            String delayType = "";
            String expireDealMode = "b";
            String delayDiscntId = "";
            List<Map> broadDiscntInfo = (List<Map>) msg.get("broadDiscntInfo");
            if (null != broadDiscntInfo && !broadDiscntInfo.isEmpty()) {
                delayType = (String) ((Map) broadDiscntInfo.get(0).get("broadDiscntAttr")).get("delayType");
                expireDealMode = "1".equals(delayType) ? "a" : "3".equals(delayType) ? "t" : "b";
                msg.put("expireDealMode", expireDealMode);
                msg.put("broadDiscntId", broadDiscntInfo.get(0).get("broadDiscntId"));
                delayDiscntId = (String) ((Map) broadDiscntInfo.get(0).get("broadDiscntAttr")).get("delayDiscntId");
                msg.put("delayDiscntId", delayDiscntId);
            }
            String brandNumber = (String) ((List<Map>) msg.get("productInfo")).get(0).get("brandNumber");
            preTradeSubItemNotSD(Item, msg, disItemId, lan, brandNumber, delayType, expireDealMode, delayDiscntId);
            Item.add(lan.createTradeSubItem("BOOKTIME", "", itemId));
            Item.add(lan.createTradeSubItem("LINK_PHONE", msg.get("contactPerson"), itemId));
            Item.add(lan.createTradeSubItem("COUNTRYFLAG", "", itemId));
            Item.add(lan.createTradeSubItem("CONTACT_CONFIRM", "1", itemId));
            Item.add(lan.createTradeSubItem("SL_5", getSpeed(msg), itemId));
            Item.add(lan.createTradeSubItem("LINK_NAME", msg.get("customerName"), itemId));
            Item.add(lan.createTradeSubItem("BILLINGRATE", msg.get("speedLevel"), itemId));
            Item.add(lan.createTradeSubItem("REMARK", "", itemId));
            Item.add(lan.createTradeSubItem("TIME_SHARING", "0", itemId));

        }
        else {
            // Item.add(lan.createTradeSubItemC(itemId, "fixedHire", msg.get("fixedHire"), startDate, endDate));
            Item.add(lan.createTradeSubItemC(itemId, "expireDealMode", "a", startDate, endDate));
            Item.add(lan.createTradeSubItemC(itemId, "bDiscntCode", msg.get("bDiscntCode"), startDate, endDate));
            Item.add(lan.createTradeSubItemC(itemId, "callBack", "0", startDate, endDate));
            Item.add(lan.createTradeSubItemC(itemId, "adStart", "0", startDate, endDate));
            Item.add(lan.createTradeSubItemC(itemId, "cycleFee", "990", startDate, endDate));
            Item.add(lan.createTradeSubItemC(itemId, "recharge", "0", startDate, endDate));
            Item.add(lan.createTradeSubItemC(itemId, "adEnd", "", startDate, endDate));
            Item.add(lan.createTradeSubItemC(itemId, "bcafixedhireflag", "0", startDate, endDate));
            Item.add(lan.createTradeSubItemC(itemId, "agrnotifynumber", "", startDate, endDate));
            Item.add(lan.createTradeSubItemC(itemId, "cycle", noValueRetDefault(msg, "property_cycle", "12"),
                    startDate, endDate));
            Item.add(lan.createTradeSubItemC(itemId, "cycleNum", noValueRetDefault(msg, "property_cycleNum", "1"),
                    startDate, endDate));
            Item.add(lan.createTradeSubItemC(itemId, "expireDealDiscnt", msg.get("use_aDiscntCode"), startDate, endDate));
            Item.add(lan.createTradeSubItemB2(userId, "XZ_1", msg.get("XZ_1")));
            Item.add(lan.createTradeSubItemB2(userId, "RWLX_27", msg.get("RWLX_27")));
            Item.add(lan.createTradeSubItemB2(userId, "PROTOCOL_ID", msg.get("PROTOCOL_ID")));
            Item.add(lan.createTradeSubItemB2(userId, "SL_5", msg.get("SL_5")));
            Item.add(lan.createTradeSubItemB2(userId, "CONTACT_CONFIRM", msg.get("CONTACT_CONFIRM")));
            Item.add(lan.createTradeSubItemB2(userId, "LINK_PHONE", msg.get("LINK_PHONE")));
        }
        tradeSubItem.put("item", Item);
        return tradeSubItem;
    }

    /**
     * 从msg中获取值,没有则取默认值
     * @param msg
     * @param key
     * @param defaultValue
     * @return
     */
    private Object noValueRetDefault(Map msg, String key, Object defaultValue) {
        return IsEmptyUtils.isEmpty(msg.get(key)) ? defaultValue : msg.get(key);
    }

    private String getSpeed(Map msg) {
        Map speedMap = (Map) JSON.parse(EcAopConfigLoader.getStr("ecaop.global.param.N6broad.speedLevel."
                + msg.get("province")));
        Object speed = speedMap.get(msg.get("speedLevel"));
        if (IsEmptyUtils.isEmpty(speed)) {
            throw new EcAopServerBizException(ERROR_CODE, "省份下发速率:" + msg.get("speedLevel") + " 未配置转换关系,请确认");
        }
        return (String) speed;
    }

    private void preTradeSubItemNotSD(List<Map> item, Map msg, String disItemId, LanUtils lan, String brandNumber,
            String delayType, String expireDealMode, String delayDiscntId) {
        String chgStartDate = GetDateUtils.getNextMonthFirstDayFormat();
        String endDate = "20501231235959";
        if ("a".equals(expireDealMode)) {
            item.add(lan.createTradeSubItemC(disItemId, "bDiscntCode", msg.get("bDiscntCode"), chgStartDate, endDate));
        }
        else if ("b".equals(expireDealMode)) {
            item.add(lan.createTradeSubItemC(disItemId, "bDiscntCode", delayDiscntId, chgStartDate, endDate));
        }
        else {
            item.add(lan.createTradeSubItemC(disItemId, "bDiscntCode", "", chgStartDate, endDate));
        }
        item.add(lan.createTradeSubItemC(disItemId, "expireDealMode", expireDealMode, chgStartDate, endDate));
        item.add(lan.createTradeSubItemC(disItemId, "bcafixedhireflag", "0", chgStartDate, endDate));
        item.add(lan.createTradeSubItemC(disItemId, "agrnotifynumber", "", chgStartDate, endDate));
        item.add(lan.createTradeSubItemC(disItemId, "aDiscntCode", delayDiscntId, chgStartDate, endDate));
        item.add(lan.createTradeSubItemC(disItemId, "effectMode", delayType, chgStartDate, endDate));
        item.add(lan.createTradeSubItemC(disItemId, "adStart", "0", chgStartDate, endDate));
        item.add(lan.createTradeSubItemC(disItemId, "cycleNum", "1", chgStartDate, endDate));
        item.add(lan.createTradeSubItemC(disItemId, "cycleFee", "990", chgStartDate, endDate));
        item.add(lan.createTradeSubItemC(disItemId, "cycle", brandNumber, chgStartDate, endDate));
        item.add(lan.createTradeSubItemC(disItemId, "fixedHire", brandNumber, chgStartDate, endDate));
        item.add(lan.createTradeSubItemC(disItemId, "recharge", "", chgStartDate, endDate));
        item.add(lan.createTradeSubItemC(disItemId, "callBack", "0", chgStartDate, endDate));
        item.add(lan.createTradeSubItemC(disItemId, "adEnd", "", chgStartDate, endDate));
    }

    private Map preDataForTradeItem(Map msg, Boolean isCY, Boolean flag) throws Exception {
        List<Map> Item = new ArrayList<Map>();
        Map tradeItem = new HashMap();
        Item.add(LanUtils.createTradeItem("DEVELOP_STAFF_ID", msg.get("operatorId")));
        Item.add(LanUtils.createTradeItem("DEVELOP_DEPART_ID", msg.get("channelId")));
        Item.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", msg.get("channelType")));
        if (isCY) {
            Item.add(LanUtils.createTradeItem("ALONE_TCS_COMP_INDEX", "1"));
        }
        else {
            Item.add(LanUtils.createTradeItem("ALONE_TCS_COMP_INDEX", "2"));
        }
        if (isCY) {
            Item.add(LanUtils.createTradeItem("IS_SAME_CUST", msg.get("custId")));
            Item.add(LanUtils.createTradeItem("IMMEDIACY_INFO", "0"));
            Item.add(LanUtils.createTradeItem("OPER_CODE", "3"));
            Item.add(LanUtils.createTradeItem("COMP_DEAL_STATE", "4"));
            Item.add(LanUtils.createTradeItem("ROLE_CODE_B", "1"));
            Item.add(LanUtils.createTradeItem("MARKING_TAG", "0"));
            Item.add(LanUtils.createTradeItem("WORK_STAFF_ID", ""));
            Item.add(LanUtils.createTradeItem("WORK_TRADE_ID", ""));
            Item.add(LanUtils.createTradeItem("WORK_DEPART_ID", ""));
            Item.add(LanUtils.createTradeItem("SUB_TYPE", "0"));

        }
        if (flag && isCY) {
            Item.add(LanUtils.createTradeItem("ECS_SINGLE_FLAG", "1"));
        }
        tradeItem.put("item", Item);
        return tradeItem;
    }

    // 拼装TRADE_USER
    private Map preDataForTradeUser(Map msg) {
        try {
            Map tradeUser = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("eparchyCode", msg.get("eparchyCode"));
            item.put("cityCode", msg.get("district"));
            item.put("productTypeCode", "COMP");
            item.put("userPasswd", "123456");
            item.put("userTypeCode", "0");
            item.put("scoreValue", "0");
            item.put("creditClass", "0");
            item.put("basicCreditValue", "0");
            item.put("creditValue", "0");
            item.put("acctTag", "0");
            item.put("prepayTag", "0");
            item.put("inDate", GetDateUtils.getDate());
            item.put("openDate", GetDateUtils.getDate());
            item.put("openMode", "0");
            item.put("openDepartId", msg.get("channelId"));
            item.put("openStaffId", msg.get("operatorId"));
            item.put("inDepartId", msg.get("channelId"));
            item.put("inStaffId", msg.get("operatorId"));
            item.put("removeTag", "0");
            item.put("userStateCodeset", "0");
            item.put("mputeMonthFee", "0");
            item.put("developDate", GetDateUtils.getDate());
            item.put("developStaffId", msg.get("recomPersonId"));
            item.put("developDepartId", msg.get("channelId"));
            item.put("inNetMode", "0");
            itemList.add(item);
            tradeUser.put("item", itemList);
            return tradeUser;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_USER节点报错" + e.getMessage());
        }
    }

    private Map preBaseData(Map msg, String appCode, Boolean isMixNum, Boolean flag) {
        String date = GetDateUtils.getDate();
        Map base = new HashMap();
        base.put("tradeLcuName", "TCS_CompTradeMoveReg");
        base.put("startDate", date);
        base.put("olcomTag", "0");
        base.put("areaCode", msg.get("eparchyCode"));
        base.put("foregift", "0");
        base.put("execTime", date);
        base.put("acceptDate", date);
        base.put("chkTag", "0");
        base.put("operFee", "0");
        base.put("cancelTag", "0");
        base.put("endAcycId", "203701");
        base.put("startAcycId", RDate.currentTimeStr("yyyyMM"));
        base.put("acceptMonth", RDate.currentTimeStr("MM"));
        base.put("advancePay", "0");
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
        base.put("tradeStaffId", msg.get("operatorId"));
        base.put("checkTypeCode", "8");
        base.put("termIp", "10.253.0.2");
        base.put("eparchyCode", msg.get("eparchyCode"));
        base.put("cityCode", msg.get("district"));
        base.put("tradeDepartId", msg.get("departId"));
        base.put("productId", msg.get("productId"));
        base.put("custId", msg.get("custId"));
        base.put("usecustId", msg.get("custId"));
        base.put("custName", msg.get("custName"));
        base.put("acctId", msg.get("acctId"));
        base.put("feeState", "0");
        base.put("actorName", "");
        base.put("actorCertTypeId", "");
        base.put("actorPhone", "");
        base.put("actorCertNum", "");
        base.put("contact", "");
        base.put("contactPhone", "");
        base.put("contactAddress", "");
        base.put("remark", "总部ECS订单");
        base.put("nextDealTag", "Z");
        base.put("subscribeId", msg.get("orderId"));
        if (isMixNum) {
            base.put("productId", msg.get("mixProduct"));
            base.put("userId", msg.get("mixUserId"));
            base.put("tradeId", msg.get("orderId"));
            base.put("userDiffCode", "8421");
            base.put("brandCode", "COMP");
            base.put("tradeTypeCode", "0110");
            base.put("netTypeCode", "00CP");
            base.put("serialNumber", msg.get("mixNumber"));
        }
        else {
            base.put("productId", msg.get("productId"));
            base.put("userId", msg.get("userId"));
            base.put("tradeId", msg.get("tradeId"));
            base.put("userDiffCode", "00");
            base.put("brandCode", "FTTH");
            base.put("tradeTypeCode", "0340");
            base.put("netTypeCode", "0040");
            base.put("serialNumber", msg.get("serialNumber"));
            if (flag) {
                base.put("productId", msg.get("newProductId"));
            }
        }
        return base;
    }
}
