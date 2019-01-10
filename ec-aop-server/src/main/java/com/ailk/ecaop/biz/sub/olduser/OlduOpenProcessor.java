package com.ailk.ecaop.biz.sub.olduser;

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

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.GetThreePartInfoUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;

@EcRocTag("olduOpenProcessor")
public class OlduOpenProcessor extends BaseAopProcessor implements ParamsAppliable {

    static String[] COPYARRAY = { "operatorId", "province", "city", "district", "channelId", "channelType",
            "eModeCode", "recomPersonId", "recomPersonName", "recomDepartId" };

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];
    private static final String[] PARAM_ARRAY = { "ecaop.trades.sccc.cancelPre.paramtersmapping", }; // 预提交

    @Override
    public void process(Exchange exchange) throws Exception {
        String bizKey = exchange.getBizkey();
        String methodCode = exchange.getMethodCode();
        Map inputInfo = exchange.getIn().getBody(Map.class);
        // 调三户接口获取相关信息
        Map threePartInfoMap = threePartInfo(exchange);
        exchange.getIn().setBody(inputInfo);
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) exchange.getIn().getBody(Map.class).get("msg"));
        }
        else {
            msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        }
        Object orgCity = msg.get("city");
        msg.put("city", ChangeCodeUtils.changeEparchy(msg));
        body.put("msg", msg);
        // 生成itemId和tradeID
        Map tradeMap = new HashMap();
        MapUtils.arrayPut(tradeMap, msg, COPYARRAY);
        tradeMap.put("city", orgCity);
        Exchange getItemIdExchange = ExchangeUtils.ofCopy(exchange, tradeMap);
        Exchange getTradeIdExchange = ExchangeUtils.ofCopy(exchange, tradeMap);
        String tradeId = GetSeqUtil.getSeqFromCb(getTradeIdExchange, "seq_trade_id");
        String itemId = GetSeqUtil.getSeqFromCb(getItemIdExchange, "seq_item_id");
        List<Map> productList = (List<Map>) msg.get("productInfo");
        List<Map> activityList = (List<Map>) msg.get("activityInfo");
        // 获取主产品
        if (null != productList && !productList.isEmpty()) {
            for (int pro = 0; pro < productList.size(); pro++) {
                if ("1".equals(productList.get(pro).get("productMode"))) {
                    msg.put("mainProduct", productList.get(pro).get("productId"));
                }
            }
        }
        // 获取速率
        List<Map> paraList = (List<Map>) msg.get("para");
        if (null != paraList && !paraList.isEmpty()) {
            for (int p = 0; p < paraList.size(); p++) {
                if ("speed".equals(paraList.get(p).get("paraId"))) {
                    msg.put("phoneSpeedLevel", paraList.get(p).get("paraValue"));
                }
            }
        }
        // 预提交准备请求参数
        Map preDateMap = MapUtils.asMap("custId", msg.get("custId"), "serialNumber", msg.get("serialNumber"),
                "productInfo", productList, "activityInfo", activityList, "itemId", itemId, "tradeId", tradeId, "city",
                msg.get("city"), "province", msg.get("province"), "district", msg.get("district"), "bizKey", bizKey,
                "methodCode", methodCode, "phoneSpeedLevel", msg.get("phoneSpeedLevel"), "eparchyCode",
                msg.get("city"), "eModeCode", msg.get("eModeCode"), "mainProduct", msg.get("mainProduct"),
                "markingTag", msg.get("markingTag"), "saleModType", msg.get("saleModType"));
        MapUtils.arrayPut(preDateMap, msg, COPYARRAY);
        preDateMap.putAll(threePartInfoMap);
        preDate4CB(preDateMap, exchange.getAppCode());
        // 调CB统一预提交接口
        Exchange exchange4CB = ExchangeUtils.ofCopy(exchange, preDateMap);
        Map retMap = Maps.newHashMap();
        try {
            new LanUtils().preData(pmp[0], exchange4CB);
            CallEngine.wsCall(exchange4CB, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            new LanUtils().xml2Json("ecaop.trades.sccc.cancelPre.template", exchange4CB);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用预提交失败！" + e.getMessage());
        }
        // 对预提交返回做处理
        Map PreSubOut = exchange4CB.getOut().getBody(Map.class);
        List<Map> rspInfo = (List<Map>) PreSubOut.get("rspInfo");
        if (null == rspInfo || rspInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "cBSS统一预提交未返回应答信息");
        }
        // IS_REMOTE传1，为正式提交准备
        // TradeManagerUtils.insert2CBSSTrade(preDateMap);
        retMap.put("provOrderId", rspInfo.get(0).get("provOrderId"));
        retMap.put("bssOrderId", rspInfo.get(0).get("bssOrderId"));
        Integer totalFee = 0;
        for (Map rspMap : rspInfo) {
            List<Map> provinceOrderInfo = (List<Map>) rspMap.get("provinceOrderInfo");
            if (null == provinceOrderInfo || provinceOrderInfo.isEmpty()) {
                continue;
            }
            // TODO:费用计算
            for (Map provinceOrder : provinceOrderInfo) {
                totalFee = totalFee + Integer.valueOf(provinceOrder.get("totalFee").toString());
                List<Map> preFeeInfoRsp = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                if (null == preFeeInfoRsp || preFeeInfoRsp.isEmpty()) {
                    continue;
                }
                List<Map> feeList = dealFee(preFeeInfoRsp);
                retMap.put("feeInfo", feeList);
            }
            retMap.put("totalFee", totalFee);
        }
        exchange.getOut().setBody(retMap);
    }

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
            retFee.put("maxRelief", fee.get("maxDerateFee"));
            retFee.put("origFee", fee.get("fee"));
            retFeeList.add(retFee);
        }
        return retFeeList;

    }

    private Map threePartInfo(Exchange exchange) throws Exception {
        String date = GetDateUtils.getDate();
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
        preDateMap.put("acctId", retAcctList.get(0).get("acctId"));
        preDateMap.put("userId", retUserList.get(0).get("userId"));
        preDateMap.put("custName", reCusttList.get(0).get("custName"));
        // 老产品相关信息
        preDateMap.put("oldProductId", retUserList.get(0).get("productId"));
        preDateMap.put("productTypeCode", retUserList.get(0).get("productTypeCode"));
        preDateMap.put("brandCode", retUserList.get(0).get("brandCode"));
        List<Map> threePartProductList = (List<Map>) retUserList.get(0).get("productInfo");
        String startDate = null;
        for (int i = 0; i < threePartProductList.size(); i++) {
            String productInactiveTime = (String) threePartProductList.get(i).get("productInactiveTime");
            String productMode = (String) threePartProductList.get(i).get("productMode");
            if (0 < productInactiveTime.compareTo(date) && "50".equals(productMode)) {
                throw new EcAopServerBizException("9999", "该用户存在生效合约，请使用续约接口进行操作");
            }
            if (preDateMap.get("oldProductId").equals(threePartProductList.get(i).get("productId"))) {
                startDate = (String) threePartProductList.get(i).get("productActiveTime");
                preDateMap.put("startDate", startDate);
            }
        }
        if (null == startDate || "".equals(startDate)) {
            throw new EcAopServerBizException("9999", "三户信息校验接口未返回老产品生效时间");
        }
        return preDateMap;
    }

    private void preDate4CB(Map inputMap, String appCode) {
        inputMap.put("ordersId", inputMap.get("tradeId"));
        inputMap.put("operTypeCode", "0");
        Map base = preMixBase(inputMap, appCode);
        Map ext = preMixExt(inputMap, appCode);
        inputMap.put("ext", ext);
        inputMap.put("base", base);

    }

    private Map preMixExt(Map inputMap, String appCode) {
        Map ext = new HashMap();
        // 主产品不变更，只参加活动以及附加产品
        if (inputMap.get("oldProductId").equals(inputMap.get("mainProduct"))) {
            ext.put("tradeItem", preTradeItem(inputMap, appCode));

            inputMap.remove("oldProductId");
            // 删除主产品信息，保留附加产品
            List<Map> productInfoList = (List<Map>) inputMap.get("productInfo");
            List<Map> productList = new ArrayList<Map>();
            for (int j = 0; j < productInfoList.size(); j++) {
                if ("1".equals(productInfoList.get(j).get("productMode"))) {
                    productList.add(productInfoList.get(j));
                }

            }
            productInfoList.removeAll(productList);
            if (null != productInfoList && !productInfoList.isEmpty()) {

                productInfoList.get(0).put("productMode", "1");
            }
            ext.putAll(TradeManagerUtils.newPreProductInfo(productInfoList, "00" + inputMap.get("province"), inputMap));
            // tradeSubItem必须放到处理完产品之后处理，应为要用到活动资费对应的itemid
            ext.put("tradeSubItem", preTradeSubItem(inputMap));

        }
        else {
            ext.put("tradeOther", preTradeOtherItem(inputMap));
            ext.put("tradeUser", preTradeUserItem(inputMap));
            ext.put("tradeItem", preTradeItem(inputMap, appCode));
            List<Map> productList = (List<Map>) inputMap.get("productInfo");
            for (int i = 0; i < productList.size(); i++) {
                productList.get(i).put("firstMonBillMode", "02");// 给外围产品默认02
                if ("0".equals(productList.get(i).get("productMode"))) {
                    productList.get(i).put("productMode", "1");
                }
                else {
                    productList.get(i).put("productMode", "0");
                }
            }
            // TODO:将活动以及活动下的自选包纳入产品中
            if (null != productList && !productList.isEmpty()) {
                ext.putAll(TradeManagerUtils.newPreProductInfo(productList, "00" + inputMap.get("province"), inputMap));
            }
            ext.put("tradeSubItem", preTradeSubItem(inputMap));
        }

        return ext;
    }

    private Map preTradeSubItem(Map inputMap) {
        Map tradeSubItem = new HashMap();
        LanUtils lan = new LanUtils();
        List<Map> item = new ArrayList<Map>();
        String provinceCode = (String) inputMap.get("province");
        System.out.println("asdhfhfjgkglljagagiwdasdguagduhk" + provinceCode);
        Map itemMap = MapUtils.asMap("startDate", GetDateUtils.getNextMonthFirstDayFormat(), "endDate",
                "20501231000000", "attrTypeCode", "3", "attrCode", "tradeId", "attrValue", inputMap.get("tradeId"));
        // 通过省份编码区分itemId By zs
        if ("36|38|13".contains(provinceCode)) {
            itemMap.put("itemId", inputMap.get("discntItemId"));
        }
        else {
            itemMap.put("itemId", inputMap.get("userId"));
        }
        item.add(itemMap);
        String recomPersonId = (String) inputMap.get("recomPersonId");
        if (StringUtils.isNotEmpty(recomPersonId)) {
            item.add(lan.createTradeSubItem1((String) inputMap.get("itemId"), "developerStaffId", recomPersonId,
                    GetDateUtils.getNextMonthFirstDayFormat()));
        }
        String recomDepartId = (String) inputMap.get("recomDepartId");
        if (StringUtils.isNotEmpty(recomDepartId)) {
            item.add(lan.createTradeSubItem1((String) inputMap.get("itemId"), "developDepartId", recomDepartId,
                    GetDateUtils.getNextMonthFirstDayFormat()));
        }
        String recomPersonName = (String) inputMap.get("recomPersonName");
        if (StringUtils.isNotEmpty(recomPersonName)) {
            item.add(lan.createTradeSubItem1((String) inputMap.get("itemId"), "developerStaffName", recomPersonName,
                    GetDateUtils.getNextMonthFirstDayFormat()));
        }
        tradeSubItem.put("item", item);
        return tradeSubItem;
    }

    private Map preTradeItem(Map inputMap, String appCode) {
        Map TradeItem = new HashMap();
        LanUtils lan = new LanUtils();
        List<Map> items = new ArrayList<Map>();
        if (new ChangeCodeUtils().isWOPre(appCode) && !IsEmptyUtils.isEmpty(inputMap.get("orderNo"))) {
            items.add(LanUtils.createTradeItem("WORK_TRADE_ID", inputMap.get("orderNo")));
        }
        String recomPersonId = (String) inputMap.get("recomPersonId");
        if (StringUtils.isNotEmpty(recomPersonId)) {
            items.add(LanUtils.createTradeItem("DEVELOP_STAFF_ID", recomPersonId));
        }
        String recomDepartId = (String) inputMap.get("recomDepartId");
        if (StringUtils.isNotEmpty(recomDepartId)) {
            items.add(LanUtils.createTradeItem("DEVELOP_DEPART_ID", recomDepartId));
        }
        items.add(lan.createAttrInfoNoTime("STANDARD_KIND_CODE", inputMap.get("city")));
        // 新增行销标示
        String markingTag = (String) inputMap.get("markingTag");
        if (StringUtils.isNotEmpty(markingTag) && "1".equals(markingTag)) {
            items.add(LanUtils.createTradeItem("MARKING_APP", "1"));
        }
        // 新增扣款标识
        String deductionTag = (String) inputMap.get("deductionTag");
        if ("0".equals(deductionTag)) {
            items.add(LanUtils.createTradeItem("FEE_TYPE", "1"));
        }
        // 行销新增销售模式类型
        String saleModType = (String) inputMap.get("saleModType");
        if (StringUtils.isNotEmpty(saleModType)) {
            String marketingChannelType = "0".equals(saleModType) ? "01" : "02";
            items.add(LanUtils.createTradeItem("MarketingChannelType", marketingChannelType));
        }
        if (StringUtils.isNotEmpty((String) inputMap.get("eModeCode"))) {
            items.add(lan.createTradeItem("E_IN_MODE", inputMap.get("eModeCode")));
        }

        TradeItem.put("item", items);
        return TradeItem;
    }

    private Map preTradeUserItem(Map inputMap) {
        Map TradeUser = new HashMap();
        List<Map> item = new ArrayList<Map>();
        List<Map> productList = (List<Map>) inputMap.get("productInfo");
        if (productList != null) {
            for (Map productMap : productList) {
                if ("1".equals(productMap.get("productMode"))) {
                    Map itemMap = MapUtils.asMap("userId", inputMap.get("userId"), "productId",
                            productMap.get("productId"), "xDatatype", "NULL", "brandCode", "4G00", "netTypeCode", "50");
                    item.add(itemMap);
                }
            }
        }
        TradeUser.put("item", item);
        return TradeUser;
    }

    private Map preTradeOtherItem(Map inputMap) {
        Map TradeOther = new HashMap();
        List<Map> item = new ArrayList<Map>();
        String monLastTime = GetDateUtils.getMonthLastDayFormat();
        Map itemMap = MapUtils.asMap("xDatatype", null, "rsrvValueCode", "NEXP", "rsrvValue", inputMap.get("userId"),
                "rsrvStr1", inputMap.get("oldProductId"), "rsrvStr2", "00", "rsrvStr3", "-9", "rsrvStr4", "4G000001",
                "rsrvStr5", "4G000001", "rsrvStr6", "-1", "rsrvStr7", "0", "rsrvStr8", "", "rsrvStr9", "4G00",
                "rsrvStr10", inputMap.get("serialNumber"), "modifyTag", "1", "startDate", inputMap.get("startDate"),
                "endDate", monLastTime);
        item.add(itemMap);

        TradeOther.put("item", item);

        return TradeOther;
    }

    private Map preMixBase(Map inputMap, String appCode) {
        Map base = new HashMap();
        Map product = new HashMap();
        List<Map> productInfo = (List<Map>) inputMap.get("productInfo");
        if (null != productInfo && !productInfo.isEmpty()) {
            product = productInfo.get(0);
            base.put("productId", product.get("productId"));
        }
        base.put("advancePay", "0");
        base.put("userDiffCode", "00");
        base.put("acceptDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
        base.put("serinalNamber", inputMap.get("serialNumber"));
        base.put("usecustId", inputMap.get("custId"));
        base.put("actorCertNum", "");
        base.put("remark", "");
        base.put("feeState", "");
        base.put("contactPhone", "");
        base.put("nextDealTag", "Z");
        base.put("contactAddress", "");
        base.put("olcomTag", "0");
        base.put("custId", inputMap.get("custId"));
        base.put("acctId", inputMap.get("acctId"));
        base.put("userId", inputMap.get("userId"));
        base.put("custName", inputMap.get("custName"));
        base.put("foregift", "0");
        base.put("execTime", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("termIp", "10.124.0.7");
        base.put("actorCertTypeId", "");
        base.put("chkTag", "0");
        base.put("tradeId", inputMap.get("tradeId"));
        base.put("actorPhone", "");
        base.put("operFee", "0");
        base.put("cancelTag", "0");
        base.put("tradeTypeCode", "0120");
        base.put("cityCode", inputMap.get("district"));
        base.put("eparchyCode", inputMap.get("city"));
        base.put("netTypeCode", "50");
        base.put("contact", "");
        base.put("feeStaffId", "");
        base.put("checktypeCode", "0");
        base.put("subscribeId", inputMap.get("tradeId"));
        base.put("brandCode", "4G00");
        base.put("actorName", "");
        return base;
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
