package com.ailk.ecaop.biz.sub.lan;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.lang.RDate;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.n3r.ecaop.core.processor.TransReqParamsMappingProcessor;

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
import com.ailk.ecaop.common.utils.YearPayEcsCheck;
import com.ailk.ecaop.common.utils.YearPayN6Utils;
import com.alibaba.fastjson.JSON;

public class YearPayEcs4N6Check implements YearPayEcsCheck {

    private final String ERROR_CODE = "8888";
    protected ParametersMappingProcessor[] pmp = null;

    public YearPayEcs4N6Check(ParametersMappingProcessor[] pmp) {
        this.pmp = pmp;
    }

    @Override
    public Map yearPayEcsCheck(Exchange exchange, Map msg) throws Exception {
        Map retMap = new HashMap();
        if ("1".equals(msg.get("changeTag")) || IsEmptyUtils.isEmpty(msg.get("changeTag"))) {
            new TransReqParamsMappingProcessor().process(exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.ec-aop.rest");
            Map result = JSON.parseObject(exchange.getOut().getBody(String.class));
            result.put("isNoChangeProduct", "0");
            return result;
        }
        else if ("2".equals(msg.get("changeTag"))) {
            Map threePartInfo = callThreePart(exchange, msg);
            Map arrearageFeeInfo = (Map) ((List<Map>) threePartInfo.get("userInfo")).get(0).get("arrearageFeeInfo");
            List<Map> arrearageMess = new ArrayList<Map>();
            if (!IsEmptyUtils.isEmpty(arrearageFeeInfo)) {
                // 1.13亦庄联调不是欠费不返回
                if (Integer.valueOf(arrearageFeeInfo.get("depositMoney").toString()) < 0) {
                    Map arrearageInfo = new HashMap();
                    arrearageInfo.put("arrearageNumber", msg.get("serialNumber"));
                    arrearageInfo.put("areaCode", msg.get("areaCode"));
                    arrearageInfo.put("arrearageType", "0");
                    arrearageMess.add(arrearageInfo);
                }
            }
            Map preSubmitMap = preSubmit(threePartInfo, exchange);
            MapUtils.arrayPut(preSubmitMap, msg, MagicNumber.COPYARRAY);
            Exchange preSubmitExchange = ExchangeUtils.ofCopy(exchange, preSubmitMap);
            LanUtils lan = new LanUtils();
            lan.preData(pmp[0], preSubmitExchange);
            CallEngine.wsCall(preSubmitExchange, "ecaop.comm.conf.url.OrdForNorthSer." + msg.get("province"));
            lan.xml2Json("ecaop.trades.spec.sUniTrade.template", preSubmitExchange);
            retMap = preSubmitExchange.getOut().getBody(Map.class);
            retMap.put("startDate", preSubmitMap.get("startDate"));
            retMap.put("endDate", preSubmitMap.get("endDate"));
            msg.put("startDate", preSubmitMap.get("startDate"));
            msg.put("endDate", preSubmitMap.get("endDate"));
            if (arrearageMess.size() > 0) {
                retMap.put("arrearageMess", arrearageMess);
            }
            return retMap;
        }
        else if ("3".equals(msg.get("changeTag"))) {
            throw new EcAopServerBizException(ERROR_CODE, "不支持非主产品变更,请核实");
        }
        else {
            throw new EcAopServerBizException(ERROR_CODE, "产品变更方式[" + msg.get("changeTag") + "]不在[1.趸交;2.变更产品;3.变更非主产品");
        }

    }

    /**
     * 需要准备的信息
     * Base
     * TF_B_TRADE_DISCNT
     * TF_B_TRADE_OTHER
     * TF_B_TRADE_PRODUCT
     * TF_B_TRADE_PRODUCT_TYPE
     * TF_B_TRADE_SUB_ITEM
     * TF_B_TRADE_SVC
     * TF_B_TRADE_USER
     * @param threePartInfo
     * @param exchange
     * @return
     * @throws Exception
     */
    public Map preSubmit(Map threePartInfo, Exchange exchange) throws Exception {
        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        Map preSubmitMap = new HashMap();
        MapUtils.arrayPut(preSubmitMap, msg, MagicNumber.COPYARRAY);
        List ordersIdList = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, preSubmitMap), "seq_trade_id", 2);
        String ordersId = ordersIdList.get(0).toString();
        msg.put("discntItemId", ordersIdList.get(1));
        // 为了防止CB订单重复,延后50年
        byte[] array = ordersId.getBytes();
        array[2] += 5;
        ordersId = new String(array);
        msg.put("ordersId", ordersId);
        // preSubmitMap.put("ordersId", orderNo);
        preSubmitMap.put("ordersId", ordersId);
        preSubmitMap.put("operTypeCode", "0");
        Object userId = new YearPayN6Utils().getInfoFromThreePart(threePartInfo, "userInfo").get("userId");
        msg.put("userId", userId);
        msg.put("cbssCity", new ChangeCodeUtils().changeEparchyUnStatic(msg));
        msg.put("eparchyCode", msg.get("cbssCity"));
        Map ext = preExt4PreSubmit(threePartInfo, exchange, msg);
        preSubmitMap.put("ext", ext);
        msg.put("brandCode", ext.get("brandCode"));
        // msg.put("", ext.get("productTypeCode"));
        msg.put("productId", ext.get("mproductId"));
        Map base = preBase4PreSubmit(threePartInfo, msg);
        preSubmitMap.put("base", base);
        preSubmitMap.put("startDate", ext.get("startDate"));
        preSubmitMap.put("endDate", ext.get("endDate"));
        return preSubmitMap;
    }

    private Map preExt4PreSubmit(Map threePartInfo, Exchange exchange, Map msg) throws Exception {
        Map ext = new HashMap();
        String methodCode = exchange.getMethodCode();
        msg.put("methodCode", methodCode);
        List<Map> productInfo = (List<Map>) msg.get("productInfo");
        if (IsEmptyUtils.isEmpty(productInfo)) {
            throw new EcAopServerBizException("8888", "发起方未下发产品信息");
        }
        List<Map> oldTradeOther = new ArrayList<Map>();
        List<Map> oldTradeProduct = new ArrayList<Map>();
        List<Map> oldTradeProductType = new ArrayList<Map>();
        List<Map> newProductList = new ArrayList<Map>();
        Object oldProductId = null;
        for (Map product : productInfo) {
            Object optType = product.get("optType");
            if ("1".equals(optType)) {
                oldProductId = product.get("oldProductId");
                Map backProdct = new YearPayN6Utils().dealBackProduct(product, threePartInfo);
                oldTradeOther.add((Map) backProdct.get("tradeOther"));
                oldTradeProductType.add((Map) backProdct.get("tradeProductType"));
                oldTradeProduct.add((Map) backProdct.get("tradeProduct"));
                ext.put("tradeOther", MapUtils.asMap("item", oldTradeOther));
            }
            else {
                product.put("productId", product.get("oldProductId"));
                product.put("firstMonBillMode", "02");
                newProductList.add(product);
                msg.put("newProductId", product.get("oldProductId"));
                System.out.println("liujiadi==========1:" + newProductList);
            }
        }
        if (!IsEmptyUtils.isEmpty(newProductList)) {
            ext.putAll(TradeManagerUtils.preProductInfo(newProductList, "00" + msg.get("province"), msg));
            System.out.println("liujiadi==========2:" + ext);
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
                msg.put("EPARCHY_CODE", msg.get("cbssCity"));
                msg.put("PRODUCT_ID", ext.get("mproductId"));
                disMap = TradeManagerUtils.preProductInfoByBroadDiscntId(msg);
                discntList.add(disMap);
            }

            if (null != discntList && !discntList.isEmpty()) {
                msg.put("discntList", discntList);
            }
            System.out.println("liujiadi==========7:" + discntList);
            ext.put("tradeDiscnt", new YearPayN6Utils().preDiscntData(msg));
            System.out.println("liujiadi==========8:" + ext);
        }
        YearPayN6Utils ypN6U = new YearPayN6Utils();
        Map userInfo = ypN6U.getInfoFromThreePart(threePartInfo, "userInfo");
        List<Map> tradeProductTypeItem = (List<Map>) ((Map) ext.get("tradeProductType")).get("item");
        oldTradeProductType.addAll(tradeProductTypeItem);
        List<Map> tradeProductItem = (List<Map>) ((Map) ext.get("tradeProduct")).get("item");

        // 增加辽宁单独的处理产品节点结束时间的方法,需要是订购产品开始时间前一秒.. by wangmc 20180808
        if ("91".equals(msg.get("province"))) {
            msg.put("apptx", exchange.getIn().getBody(Map.class).get("apptx"));
            dealBackProductEndDate(oldTradeProduct, oldTradeProductType, oldTradeOther, tradeProductItem, msg,
                    oldProductId);
        }

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
        // 增加辽宁单独的处理服务节点开始时间的方法,服务的开始时间与订购产品的开始时间保持一致by wangmc 20180809
        if ("91".equals(msg.get("province"))) {
            dealSvcStartDate(tradeSvc, tradeProductItem, msg);
        }
        oldTradeSvcItem.addAll(tradeSvc);
        for (Map prod : oldTradeProduct) {
            prod.put("userId", msg.get("userId"));
        }
        // 取出发展人信息
        if (!IsEmptyUtils.isEmpty((List<Map>) msg.get("recomInfo"))) {
            List<Map> recomInfo = (List<Map>) msg.get("recomInfo");
            for (Map comInfo : recomInfo) {
                if (StringUtils.isNotEmpty((String) comInfo.get("recomPersonId"))) {
                    msg.put("recomPersonId", comInfo.get("recomPersonId"));// 发展人
                }
                if (StringUtils.isNotEmpty((String) comInfo.get("recomDepartId"))) {
                    msg.put("recomDepartId", comInfo.get("recomDepartId"));// 发展人渠道
                }
            }
        }
        Map tradeItem = new YearPayN6Utils().preTradeItem(msg);
        List<Map> tradeItemList = (List<Map>) tradeItem.get("item");
        Map tradeItemMap = new HashMap();
        tradeItemMap.put("xDatatype", "NULL");
        tradeItemMap.put("attrCode", "ECS_ORDER_ID");
        tradeItemMap.put("attrValue", msg.get("orderNo"));
        tradeItemMap.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
        tradeItemMap.put("endDate", "20501231235959");

        // RSD2017092000008-AOP宽带趸交和续费接口支持微信和支付宝支付方式需求 新增行销标示
        String markingTag = (String) msg.get("markingTag");
        if (StringUtils.isNotEmpty(markingTag) && "1".equals(markingTag)) {
            Map itemMap = new HashMap();
            itemMap.put("xDatatype", "NULL");
            itemMap.put("attrCode", "MARKING_APP");
            itemMap.put("attrValue", markingTag);
            tradeItemMap.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
            tradeItemMap.put("endDate", "20501231235959");
            tradeItemList.add(itemMap);
        }
        tradeItemList.add(tradeItemMap);
        tradeItem.put("item", tradeItemList);
        ext.put("tradeItem", tradeItem);
        msg.put("brandCode", ext.get("brandCode"));
        ext.put("tradeSubItem", new YearPayN6Utils().preTradeSubItem(msg, threePartInfo));
        // 传入主产品的品牌
        ext.put("tradeUser", new YearPayN6Utils().preTradeUserItem(msg));
        ext.put("tradeProductType", MapUtils.asMap("item", oldTradeProductType));
        ext.put("tradeProduct", MapUtils.asMap("item", oldTradeProduct));
        ext.put("tradeSvc", MapUtils.asMap("item", oldTradeSvcItem));
        // 获取资费的生效时间,默认只有一个开户时选择的资费
        List<Map> oldDiscntInfo = (List<Map>) userInfo.get("discntInfo");
        Map tradeDiscnt = (Map) ext.get("tradeDiscnt");
        List<Map> tradeDiscntItemList = (List<Map>) tradeDiscnt.get("item");
        ext.put("tradeDiscnt", MapUtils.asMap("item", tradeDiscntItemList));
        List<Map> itemInfo = (List<Map>) tradeDiscnt.get("item");
        ext.put("startDate", itemInfo.get(0).get("startDate"));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        int brandNumber = Integer.valueOf((String) ((List<Map>) msg.get("productInfo")).get(0).get("brandNumber"));
        String endDate = sdf.format(RDate.addMonths(sdf.parse(ext.get("startDate").toString()), brandNumber));
        ext.put("endDate", DateUtils.addSeconds(endDate, -1));
        if ("17".equals(msg.get("province"))) {
            Map disMap = new HashMap();
            Object delayDiscntId = ((Map) broadDiscntInfo.get(0).get("broadDiscntAttr")).get("delayDiscntId");
            msg.put("elementId", delayDiscntId);
            msg.put("PROVINCE_CODE", "00" + msg.get("province"));
            msg.put("EPARCHY_CODE", msg.get("cbssCity"));
            msg.put("PRODUCT_ID", ext.get("mproductId"));
            disMap = TradeManagerUtils.preProductInfoByBroadDiscntId(msg);
            ext.put("tradeDiscnt", preForTradeDiscnt(msg, disMap, ext));
        }
        return ext;
    }

    /**
     * 增加辽宁单独的处理服务节点开始时间的方法,服务的开始时间与订购产品的开始时间保持一致by wangmc 20180809
     * @param tradeSvc
     * @param tradeProductItem
     * @param msg
     */
    private void dealSvcStartDate(List<Map> tradeSvc, List<Map> tradeProductItem, Map msg) {
        String newProductStartDate = getNewProductStartDate(tradeProductItem, msg);
        if (null != newProductStartDate) {
            for (Map svc : tradeSvc) {
                svc.put("startDate", newProductStartDate);
            }
        }
    }

    /**
     * 获取订购产品的开始时间,给服务节点使用,与产品开始时间保持一致
     * @param tradeProductItem
     * @param msg
     * @return
     */
    private String getNewProductStartDate(List<Map> tradeProductItem, Map msg) {
        Object newProductId = msg.get("newProductId");
        if (IsEmptyUtils.isEmpty(tradeProductItem) || IsEmptyUtils.isEmpty(newProductId)) {
            return null;
        }
        String newProductStartDate = "";
        for (Map item : tradeProductItem) {
            if (newProductId.equals(item.get("productId"))) {
                newProductStartDate = (String) item.get("startDate");
            }
        }
        if (IsEmptyUtils.isEmpty(newProductStartDate)) {
            return null;
        }
        return newProductStartDate;
    }

    /**
     * 专门用来处理辽宁退订产品的结束时间的方法,退订产品节点(产品类型节点,tradeOther节点)的结束时间为订购产品开始时间的前一秒
     * @param oldTradeProduct-退订产品节点
     * @param oldTradeProductType-退订产品类型节点
     * @param oldTradeOther
     * @param tradeProductItem-订购产品节点
     * @param msg
     * @param oldProductId
     */
    private void dealBackProductEndDate(List<Map> oldTradeProduct, List<Map> oldTradeProductType,
            List<Map> oldTradeOther, List<Map> tradeProductItem, Map msg, Object oldProductId) {
        Object newProductId = msg.get("newProductId");
        if (IsEmptyUtils.isEmpty(newProductId) || IsEmptyUtils.isEmpty(tradeProductItem)
                || IsEmptyUtils.isEmpty(oldTradeProduct)) {
            return;
        }
        // 从订购产品中获取订购产品的开始时间
        String newProductStartDate = getNewProductStartDate(tradeProductItem, msg);
        if (IsEmptyUtils.isEmpty(newProductStartDate)) {
            return;
        }
        // 根据订购产品的开始时间往前计算1秒,获得退订产品的结束时间,并覆盖原结束时间
        String oldProductEndDate = "";
        try {
            oldProductEndDate = GetDateUtils.getBefDate(newProductStartDate, -1);
        }
        catch (Exception e) {
            System.out.println(msg.get("apptx") + ",时间格式转换失败,原时间:" + newProductStartDate);
            e.printStackTrace();
        }
        if (IsEmptyUtils.isEmpty(oldProductEndDate)) {
            return;
        }
        // 放tradeProduct节点
        for (Map item : oldTradeProduct) {
            if (oldProductId.equals(item.get("productId"))) {
                item.put("endDate", oldProductEndDate);
            }
        }
        // 放traedProductType节点
        for (Map item : oldTradeProductType) {
            if (oldProductId.equals(item.get("productId"))) {
                item.put("endDate", oldProductEndDate);
            }
        }
        // tradeOther节点的结束时间也要是这个值.由于是引用,所以虽然这个值已经放到ext了,但是这里改变依然能改变ext节点中tradeOther的值
        oldTradeOther.get(0).put("endDate", oldProductEndDate);
    }

    private Map preForTradeDiscnt(Map msg, Map disMap, Map ext) {
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
        item.put("productId", disMap.get("productId"));
        item.put("packageId", disMap.get("packageId"));
        item.put("discntCode", disMap.get("discntCode"));
        item.put("specTag", "0");
        item.put("relationTypeCode", "");
        item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
        item.put("itemId", msg.get("itemId"));
        item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
        item.put("modifyTag", "0");
        disItem.add(item);
        tradeDiscnt.put("item", disItem);
        return tradeDiscnt;
    }

    private Map preBase4PreSubmit(Map threePartInfo, Map msg) {
        Map base = new HashMap();
        Object cbssCity = msg.get("cbssCity");
        Object tradeId = msg.get("ordersId");
        base.put("subscribeId", tradeId);
        base.put("areaCode", cbssCity);
        base.put("cityCode", msg.get("district"));
        base.put("tradeStaffId", msg.get("operatorId"));
        base.put("routeEparchy", cbssCity);
        base.put("tradeId", tradeId);
        base.put("acceptDate", GetDateUtils.getDate());
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("inModeCode", "E");
        base.put("tradeTypeCode", "0127");
        base.put("productId", msg.get("productId"));
        base.put("brandCode", msg.get("brandCode"));
        YearPayN6Utils ypu = new YearPayN6Utils();
        Map userInfo = ypu.getInfoFromThreePart(threePartInfo, "userInfo");
        base.put("userId", userInfo.get("userId"));
        base.put("usecustId", userInfo.get("usecustId"));
        base.put("userDiffCode", userInfo.get("userDiffCode"));
        base.put("netTypeCode", "0040");// 暂时写死
        Map custInfo = ypu.getInfoFromThreePart(threePartInfo, "custInfo");
        base.put("custId", custInfo.get("custId"));
        base.put("custName", custInfo.get("custName"));
        msg.put("custName", custInfo.get("custName"));
        Map acctInfo = ypu.getInfoFromThreePart(threePartInfo, "acctInfo");
        base.put("acctId", acctInfo.get("acctId"));
        base.put("serialNumber", msg.get("serialNumber"));
        base.put("eparchyCode", cbssCity);
        base.put("cityCode", msg.get("district"));
        base.put("execTime", GetDateUtils.getDate());
        base.put("cancelTag", "0");
        base.put("feeState", "1");
        base.put("termIp", "0.0.0.0");
        base.put("operFee", "0");
        base.put("foregift", "0");
        base.put("advancePay", "0");
        base.put("chkTag", "0");// 0-未审核
        base.put("mainDiscntCode", ((List<Map>) msg.get("broadDiscntInfo")).get(0).get("broadDiscntId"));// 主资费编码
        base.put("feeStaffId", "");
        base.put("checkTypeCode", "");
        base.put("actorName", "");
        base.put("actorCertTypeId", "");
        base.put("actorPhone", "");
        base.put("actorCertNum", "");
        base.put("contact", "");
        base.put("contactPhone", "");
        base.put("contactAddress", "");
        base.put("remark", "总部ECS订单");
        return base;
    }

    @Override
    public Map yearPayEcsSub(Exchange exchange, Map preSubmitReturn, Map msg) throws Exception {
        Map submitMap = new HashMap();
        MapUtils.arrayPut(submitMap, msg, MagicNumber.COPYARRAY);
        Object orderNo = msg.get("orderNo");
        submitMap.put("orderNo", orderNo);
        List<Map> rspInfoList = (List<Map>) preSubmitReturn.get("rspInfo");
        if (IsEmptyUtils.isEmpty(rspInfoList)) {
            throw new EcAopServerBizException("8888", "北六预提交接口未返回订单编号");
        }
        Map rspInfo = rspInfoList.get(0);
        Map retMap = dealSubmit(submitMap, orderNo, rspInfo, msg);
        Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[4], submitExchange);
        CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.osn.services.ordser");
        lan.xml2Json("ecaop.masb.odsb.template", submitExchange);
        Map outMap = new HashMap();
        outMap.put("code", "0000");
        outMap.put("detail", "OK");
        outMap.putAll(retMap);
        if (!IsEmptyUtils.isEmpty(preSubmitReturn.get("arrearageMess"))) {
            outMap.put("arrearageMess", preSubmitReturn.get("arrearageMess"));
        }
        return outMap;
    }

    public Map dealSubmit(Map submitMap, Object orderNo, Map rspInfo, Map msg) {
        Object custName = msg.get("custName");
        Object productId = null;
        Object discntId = null;
        List<Map> productInfoList = (List<Map>) msg.get("productInfo");
        List<Map> broadDiscntInfo = (List<Map>) msg.get("broadDiscntInfo");
        if (null != productInfoList && !productInfoList.isEmpty()) {
            for (Map tempMap : productInfoList) {
                if ("0".equals(tempMap.get("optType"))) {// 订购的产品
                    productId = tempMap.get("oldProductId");
                }
            }
        }
        if (null != broadDiscntInfo && !broadDiscntInfo.isEmpty()) {
            discntId = broadDiscntInfo.get(0).get("broadDiscntId");
        }
        Map retMap = new HashMap();
        submitMap.put("operationType", "02");
        Object provOrderId = rspInfo.get("bssOrderId");
        submitMap.put("provOrderId", provOrderId);
        submitMap.put("orderNo", rspInfo.get("provOrderId"));
        List<Map> subOrderSubReq = new ArrayList<Map>();
        Map subOrderSub = new HashMap();
        subOrderSub.put("subProvinceOrderId", rspInfo.get("provOrderId"));
        subOrderSub.put("subOrderId", orderNo);
        List<Map> provinceOrderInfo = (List<Map>) rspInfo.get("provinceOrderInfo");
        if (IsEmptyUtils.isEmpty(provinceOrderInfo)) {
            subOrderSub.put("subProvinceOrderId", provOrderId);
            subOrderSubReq.add(subOrderSub);
            submitMap.put("subOrderSubReq", subOrderSubReq);
            submitMap.put("cancleTotalFee", "0");
        }
        else {
            List<Map> fee = new ArrayList<Map>();
            List<Map> retFee = new ArrayList<Map>();
            int totalFee = 0;
            for (Map provinceOrder : provinceOrderInfo) {
                List<Map> feeInfo = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                if (IsEmptyUtils.isEmpty(feeInfo)) {
                    continue;
                }
                for (Map feeMap : feeInfo) {
                    Map temp = new HashMap();
                    temp.put("feeCategory", feeMap.get("feeMode"));
                    temp.put("feeId", feeMap.get("feeTypeCode"));
                    temp.put("feeDes", feeMap.get("feeTypeName"));
                    temp.put("operateType", "2");
                    temp.put("origFee", feeMap.get("fee"));
                    temp.put("realFee", feeMap.get("fee"));
                    temp.put("calculateTag", "N");
                    fee.add(temp);
                }
                // 北六预提交不返回feeTypeName
                for (Map feeMap : feeInfo) {
                    if (null == feeMap.get("feeTypeName") || "".equals(feeMap.get("feeTypeName"))) {
                        feeMap.put("feeTypeName", "趸交费用");
                    }
                }
                retFee.addAll(feeInfo);
                totalFee += Integer.valueOf(provinceOrder.get("totalFee").toString());
            }
            subOrderSub.put("feeInfo", fee);
            subOrderSub.put("subProvinceOrderId", provOrderId);
            subOrderSubReq.add(subOrderSub);
            submitMap.put("subOrderSubReq", subOrderSubReq);
            submitMap.put("cancleTotalFee", totalFee);
            retMap.put("feeInfo", retFee);// 预提交返回的费用
            retMap.put("totalFee", String.valueOf(totalFee));
            retMap.put("provOrderId", provOrderId);
            retMap.put("custName", custName);
            retMap.put("productType", "1");
            retMap.put("productName", productId);
            retMap.put("discntName", discntId);
            retMap.put("startDate", msg.get("startDate"));
            retMap.put("endDate", msg.get("endDate"));
        }
        submitMap.put("origTotalFee", "0");
        return retMap;
    }

    /**
     * 调用三户接口,获取预提交的必要信息
     * @param exchange
     * @param msg
     * @return
     * @throws Exception
     */
    public Map callThreePart(Exchange exchange, Map msg) throws Exception {
        preThreePartInfo(exchange, msg);
        Map threePartMap = MapUtils.asMap("getMode", "101001101010001001000000000000", "serialNumber",
                msg.get("serialNumber"), "tradeTypeCode", "0127", "serviceClassCode", "0040");
        Object queryType = msg.get("queryType");
        if ("0".equals(queryType)) {
            threePartMap.put("serviceClassCode", "0040");
        }
        MapUtils.arrayPut(threePartMap, msg, MagicNumber.COPYARRAY);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[1], threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.UsrForNorthSer." + msg.get("province"));
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        Map retMap = threePartExchange.getOut().getBody(Map.class);
        List<Map> custInfoMap = (List<Map>) retMap.get("custInfo");
        if (null == custInfoMap || custInfoMap.isEmpty()) {
            throw new EcAopServerBizException("9999", "三户未返回客户信息");
        }
        msg.put("custName", custInfoMap.get(0).get("custName"));
        return retMap;
    }

    private Map preThreePartInfo(Exchange exchange, Map msg) throws Exception {
        Map threePartMap = MapUtils.asMap("getMode", "1111110010000011111", "tradeTypeCode", "9999");
        int queryType = Integer.valueOf(msg.get("queryType").toString());
        threePartMap.put("serviceClassCode", "0040");
        Object serialNumber = msg.get("serialNumber");
        switch (queryType) {
        case 0:
            break;
        case 1:
            Map acctNbrInfo = MapUtils.asMap("routeTag", "0", "inTagSet", "110", "acctNbr", serialNumber);
            MapUtils.arrayPut(acctNbrInfo, msg, MagicNumber.COPYARRAY);
            serialNumber = getSeialNumberByAcctNbr(acctNbrInfo, exchange);
            break;
        case 2:
            Map qryInfo = MapUtils.asMap("serviceClassCode", "0030");
            qryInfo.putAll(threePartMap);
            serialNumber = getSeialNumberByShareNbr(qryInfo, exchange);
            break;
        case 3:
            break;
        default:
            throw new EcAopServerBizException(ERROR_CODE, "queryType:'" + queryType + "'不在[0,1,2,3]范围内");
        }
        threePartMap.put("serialNumber", serialNumber);
        msg.put("serialNumber", serialNumber);
        MapUtils.arrayPut(threePartMap, msg, MagicNumber.COPYARRAY);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[1], threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.UsrForNorthSer." + msg.get("province"));
        lan.xml2Json("ecaop.trades.n6.threePart.template", threePartExchange);
        return threePartExchange.getOut().getBody(Map.class);
    }

    private Object getSeialNumberByAcctNbr(Map acctNbrInfo, Exchange exchange) throws Exception {
        Exchange acctNbrExchange = ExchangeUtils.ofCopy(exchange, acctNbrInfo);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[2], acctNbrExchange);
        CallEngine.wsCall(acctNbrExchange, "ecaop.comm.conf.url.UsrForNorthSer." + acctNbrInfo.get("province"));
        lan.xml2Json("ecaop.trades.spec.queryInfoByVague.template", acctNbrExchange);
        Map acctNbrRet = acctNbrExchange.getOut().getBody(Map.class);
        List<Map> queryInfoByVagueInfoRsp = (List<Map>) acctNbrRet.get("queryInfoByVagueInfoRsp");
        if (null == queryInfoByVagueInfoRsp || 0 == queryInfoByVagueInfoRsp.size()) {
            throw new EcAopServerBizException(ERROR_CODE, "省份queryVagueInfo接口未返回宽带号码信息");
        }
        return queryInfoByVagueInfoRsp.get(0).get("serialNumber");
    }

    /**
     * 通过共线号码获取宽带号码
     * @param inMap
     * @param exchange
     * @return
     * @throws Exception
     */
    private Object getSeialNumberByShareNbr(Map inMap, Exchange exchange) throws Exception {
        Exchange acctNbrExchange = ExchangeUtils.ofCopy(exchange, inMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[3], acctNbrExchange);
        CallEngine.wsCall(acctNbrExchange, "ecaop.comm.conf.url.UsrForNorthSer." + inMap.get("province"));
        lan.xml2Json("ecaop.trades.cbss.threePart.template", acctNbrExchange);
        Map threePartRet = acctNbrExchange.getOut().getBody(Map.class);
        List<Map> userInfoList = (List<Map>) threePartRet.get("userInfo");
        if (null == userInfoList || 0 == userInfoList.size()) {
            throw new EcAopServerBizException(ERROR_CODE, "省份三户接口未返回宽带号码信息");
        }
        Object userIdA = "";
        for (Map userInfo : userInfoList) {
            List<Map> uuInfoList = (List<Map>) userInfo.get("uuInfo");
            if (null == uuInfoList || 0 == uuInfoList.size()) {
                continue;
            }
            for (Map uuInfo : uuInfoList) {
                if ("X3".equals(uuInfo.get("relationTypeCode"))) {
                    userIdA = uuInfo.get("userIdA");
                    break;
                }
            }
        }
        if ("".equals(userIdA)) {
            throw new EcAopServerBizException(ERROR_CODE, "查不到共线信息");
        }
        Map relationInfo = MapUtils.asMap("xGetmode", "3", "userIdA", userIdA, "relationTypeCode", "X3");
        MapUtils.arrayPut(relationInfo, inMap, MagicNumber.COPYARRAY);
        Exchange relationInfoExhcnage = ExchangeUtils.ofCopy(exchange, relationInfo);
        lan.preData(pmp[5], relationInfoExhcnage);
        CallEngine.wsCall(relationInfoExhcnage, "ecaop.comm.conf.url.UsrForNorthSer." + inMap.get("province"));
        lan.xml2Json("ecaop.trades.spec.qryRelationInfo.template", relationInfoExhcnage);
        Map relationRet = relationInfoExhcnage.getOut().getBody(Map.class);
        List<Map> qryRelationInfoList = (List<Map>) relationRet.get("qryRelationInfoRsp");
        if (null == qryRelationInfoList || 0 == qryRelationInfoList.size()) {
            throw new EcAopServerBizException(ERROR_CODE, "您输入的帐号不存在,请核实");
        }
        for (Map qryRelationInfo : qryRelationInfoList) {
            if ("2".equals(qryRelationInfo.get("roleCodeB")))
                return qryRelationInfo.get("serialNumberB");
        }
        throw new EcAopServerBizException(ERROR_CODE, "根据共线号码,位获取到宽带号码");
    }
}
