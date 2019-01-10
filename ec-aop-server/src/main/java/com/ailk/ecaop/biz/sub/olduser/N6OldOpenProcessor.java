package com.ailk.ecaop.biz.sub.olduser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.ProductManagerUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;

/**
 * 存费送飞北六E
 * @author Administrator
 */
@EcRocTag("n6OldOpenProcessor")
public class N6OldOpenProcessor extends BaseAopProcessor {

    static String[] COPYARRAY = { "operatorId", "province", "city", "district", "channelId", "channelType", "eModeCode" };

    @Override
    public void process(Exchange exchange) throws Exception {
        String bizKey = exchange.getBizkey();
        String methodCode = exchange.getMethodCode();
        Map inputInfo = exchange.getIn().getBody(Map.class);
        Map body = exchange.getIn().getBody(Map.class);
        String appCode = exchange.getAppCode();
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) exchange.getIn().getBody(Map.class).get("msg"));
        }
        else {
            msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        }
        // 调三户接口获取相关信息1
        Map threePartInfoMap = new HashMap();// threePartInfo(exchange, msg);
        exchange.getIn().setBody(inputInfo);
        Object orgCity = msg.get("city");

        // 生成itemId和tradeID
        Map tradeMap = new HashMap();
        String eparchyCode = ChangeCodeUtils.changeEparchy(msg);
        // msg.put("city", ChangeCodeUtils.changeEparchy(msg));
        // body.put("msg", msg);
        MapUtils.arrayPut(tradeMap, msg, COPYARRAY);
        tradeMap.put("city", orgCity);
        String tradeId = "";// GetSeqUtil.getSeqFromN6ess(exchange, "TRADE_ID", eparchyCode);
        exchange.getIn().setBody(body);
        String itemId = "";// GetSeqUtil.getSeqFromN6ess(exchange, "ITEM_ID", eparchyCode);
        exchange.getIn().setBody(body);
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
                eparchyCode, "province", msg.get("province"), "district", msg.get("district"), "bizKey", bizKey,
                "methodCode", methodCode, "phoneSpeedLevel", msg.get("phoneSpeedLevel"), "eparchyCode", eparchyCode,
                "eModeCode", msg.get("eModeCode"), "mainProduct", msg.get("mainProduct"), "eparchyCodeNew",
                eparchyCode, "appCode", appCode);
        MapUtils.arrayPut(preDateMap, msg, COPYARRAY);
        preDateMap.putAll(threePartInfoMap);
        preDate4CB(preDateMap, eparchyCode, msg, itemId);
        Exchange newExchange = ExchangeUtils.ofCopy(exchange, preDateMap);
        // Map test = newExchange.getIn().getBody(Map.class);
        // System.out.print("*************************老用户校验发请求body**********************" + test + "**************");
        Map retMap = Maps.newHashMap();
        // 调北六预提交
        try {
            new LanUtils().preData("ecaop.trades.mofc.N6.cancelPre.paramtersmapping", newExchange);
            CallEngine.wsCall(newExchange,
                    "ecaop.comm.conf.url.n6ess.services.AOPOrdForNorthSer" + "." + msg.get("province"));
            new LanUtils().xml2Json("ecaop.trades.sccc.cancelPre.template", newExchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用预提交失败！" + e.getMessage());
        }

        // 对预提交返回做处理
        Map PreSubOut = newExchange.getOut().getBody(Map.class);
        List<Map> rspInfo = (List<Map>) PreSubOut.get("rspInfo");
        if (null == rspInfo || rspInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "cBSS统一预提交未返回应答信息");
        }
        // IS_REMOTE传1，为正式提交准备
        // TradeManagerUtils.insert2CBSSTrade(preDateMap);
        retMap.put("code", PreSubOut.get("code"));
        System.out.print("++++++++返回CODE？？？？？？？？？？???" + PreSubOut.get("code"));
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

    private Map threePartInfo(Exchange exchange, Map msg) throws Exception {
        // 掉三户接口
        String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType" };
        Map threePartMap = MapUtils.asMap("getMode", "11111100001000010000100000", "serialNumber",
                msg.get("serialNumber"), "tradeTypeCode", "9999");
        MapUtils.arrayPut(threePartMap, msg, copyArray);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trade.n6.checkUserParametersMapping", threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.UsrForNorthSer" + "." + msg.get("province"));
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        Map retMap = threePartExchange.getOut().getBody(Map.class);
        Map preDateMap = new HashMap();
        // 北六三户返回处理
        List<Map> retAcctInfo = (ArrayList<Map>) retMap.get("acctInfo");
        if (null == retAcctInfo || retAcctInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "三户信息校验接口:核心系统未返回账户信息");
        }
        List<Map> retUserInfo = (ArrayList<Map>) retMap.get("userInfo");
        if (null == retUserInfo || retUserInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "三户信息校验接口:核心系统未返回用户信息");
        }
        List<Map> reCustInfo = (ArrayList<Map>) retMap.get("custInfo");
        if (null == reCustInfo || reCustInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "三户信息校验接口:核心系统未返回客户信息");
        }
        preDateMap.put("acctId", retAcctInfo.get(0).get("acctId"));
        preDateMap.put("userId", retUserInfo.get(0).get("userId"));
        preDateMap.put("custName", reCustInfo.get(0).get("custName"));

        // 老产品相关信息
        preDateMap.put("oldProductId", retUserInfo.get(0).get("productId"));
        preDateMap.put("productTypeCode", retUserInfo.get(0).get("productTypeCode"));
        preDateMap.put("brandCode", retUserInfo.get(0).get("brandCode"));
        List<Map> threePartProductList = (List<Map>) retUserInfo.get(0).get("productInfo");
        String startDate = null;
        for (int i = 0; i < threePartProductList.size(); i++) {
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

    private void preDate4CB(Map inputMap, String eparchyCode, Map msg, String itemId) {
        inputMap.put("ordersId", inputMap.get("tradeId"));
        inputMap.put("operTypeCode", "0");
        Map base = preMixBase(inputMap, eparchyCode);
        Map ext = preMixExt(inputMap, msg, itemId);
        // 处理sp节点的itemId都不一样
        List<Map> tradeSP = (List<Map>) ((Map) ext.get("tradeSp")).get("item");
        if (tradeSP != null) {
            int i = 1;
            for (Map sp : tradeSP) {
                String itemid = GetSeqUtil.getSeqFromCb();
                long temp = Long.valueOf(itemid) + i;
                itemid = temp + "";
                sp.put("itemId", itemid);
                ++i;
            }
        }
        // 处理结束
        inputMap.put("ext", ext);
        inputMap.put("base", base);

    }

    private Map preMixExt(Map inputMap, Map msg, String itemId) {
        Map ext = new HashMap();
        // 主产品不变更，只参加活动
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        String commodityId = (String) inputMap.get("mainProduct");
        Map proparam = new HashMap();
        proparam.put("PROVINCE_CODE", "00" + (String) inputMap.get("province"));
        proparam.put("COMMODITY_ID", commodityId);
        proparam.put("EPARCHY_CODE", inputMap.get("eparchyCodeNew"));
        proparam.put("FIRST_MON_BILL_MODE", "02");
        List<Map> productInfoResult = new ArrayList<Map>();// dao.queryProductInfo(proparam);
        if (productInfoResult == null || productInfoResult.size() == 0) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
        }
        String newProductId = productInfoResult.get(0).get("PRODUCT_ID") + "";
        if (inputMap.get("oldProductId").equals(newProductId)) {
            ext.put("tradeItem", preTradeItem(inputMap, msg));
            ext.put("tradeSubItem", preTradeSubItem(inputMap, itemId));
            inputMap.remove("oldProductId");
            List<Map> productList = new ArrayList<Map>();
            ext.putAll(TradeManagerUtils.preProductInfo(productList, "00" + inputMap.get("province"), inputMap));

        }
        else {

            ext.put("tradeOther", preTradeOtherItem(inputMap));
            ext.put("tradeUser", preTradeUserItem(inputMap, msg));

            ext.put("tradeItem", preTradeItem(inputMap, msg));
            ext.put("tradeSubItem", preTradeSubItem(inputMap, itemId));
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
                ext.putAll(TradeManagerUtils.preProductInfo(productList, "00" + inputMap.get("province"), inputMap));
            }
        }

        return ext;
    }

    private Map preTradeSubItem(Map inputMap, String itemId) {
        Map tradeSubItem = new HashMap();
        // List<Map> item = new ArrayList<Map>();
        // Map itemMap = MapUtils.asMap("startDate", GetDateUtils.getDate(), "endDate",
        // "20501231000000",
        // "attrTypeCode", "8", "attrCode", "tradeId",
        // "attrValue", inputMap.get("tradeId"), "itemId", inputMap.get("itemId"));
        // item.add(itemMap);
        // tradeSubItem.put("item", item);
        List<Map> subitems = new ArrayList<Map>();
        LanUtils lan = new LanUtils();
        String startDate = GetDateUtils.getDate();
        String endDate = "20501231000000";
        subitems.add(createTradeSubItemNoDate("2", "interroamLimit", "0", itemId));
        subitems.add(createTradeSubItemNoDate("2", "interroamYatoyc", "0", itemId));
        subitems.add(createTradeSubItemNoDate("3", "tradeId", inputMap.get("tradeId"), itemId));
        subitems.add(createTradeSubItemNoDate("0", "MAINTENANCE_STAFF_ID", "0", itemId));
        subitems.add(createTradeSubItemNoDate("0", "MAINTENANCE_DEPART_ID", "0", itemId));
        tradeSubItem.put("item", subitems);
        return tradeSubItem;
    }

    private Map preTradeItem(Map inputMap, Map msg) {
        Map TradeItem = new HashMap();
        List<Map> items = new ArrayList<Map>();
        String channelType = (String) msg.get("channelType");
        String developStaffId = (String) msg.get("recomPersonId");
        String developDepartId = (String) msg.get("recomPersonChannelId");// 发展人渠道
        // items.add(lan.createAttrInfoNoTime("STANDARD_KIND_CODE", inputMap.get("city")));
        items.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", channelType));
        items.add(LanUtils.createTradeItem("DEVELOP_STAFF_ID", developStaffId));// 发展人
        items.add(LanUtils.createTradeItem("DEVELOP_DEPART_ID", developDepartId));
        items.add(LanUtils.createTradeItem("BLACK_USER_TAG", "0"));
        items.add(LanUtils.createTradeItem("SUB_TYPE", "0"));
        TradeItem.put("item", items);
        return TradeItem;
    }

    private Map preTradeUserItem(Map inputMap, Map msg) {
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        Map TradeUser = new HashMap();
        Map item = new HashMap();
        List<Map> productList = (List<Map>) inputMap.get("productInfo");
        for (Map productMap : productList) {
            if ("1".equals(productMap.get("productMode"))) {
                String commodityId = productMap.get("productId") + "";
                Map proparam = new HashMap();
                proparam.put("PROVINCE_CODE", "00" + (String) inputMap.get("province"));
                proparam.put("COMMODITY_ID", commodityId);
                proparam.put("EPARCHY_CODE", inputMap.get("eparchyCodeNew"));
                proparam.put("FIRST_MON_BILL_MODE", null);
                List<Map> productInfoResult = dao.queryProductInfo(proparam);
                if (productInfoResult == null || productInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + commodityId + "】的产品信息");
                }
                String newProductId = productInfoResult.get(0).get("PRODUCT_ID") + "";

                // 获取Td_b_Commodity_Trans_Item产品属性信息
                List<Map> productItemSet = ProductManagerUtils.getItemByPid(newProductId, commodityId, "00"
                        + (String) inputMap.get("province"), "U");

                if (productItemSet == null || productItemSet.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品映射item表中未查询到产品ID【" + newProductId + "】的产品属性信息");
                }
                String strBrandCode = TradeManagerUtils.getValueFromItem("BRAND_CODE", productItemSet);

                // Map itemMap = MapUtils.asMap("userId", inputMap.get("userId"), "productId",
                // newProductId,
                // "xDatatype", "NULL", "brandCode", strBrandCode, "netTypeCode", "50", "cityCode",
                // inputMap.get("city"));
                // item.add(itemMap);
                String developStaffId = (String) msg.get("recomPersonId");
                String developDepartId = (String) msg.get("recomPersonChannelId");// 发展人渠道
                String developDate = GetDateUtils.getDate();
                String userPasswd = isNull((String) msg.get("userPasswd"));

                String developCityCode = (String) msg.get("recomPersonCityCode");
                // String usecustId = (String) custInfo.get("custId");

                item.put("xDataType", "NULL");
                // item.put("usecustId", usecustId);
                item.put("userTypeCode", "2");
                item.put("userPasswd", userPasswd);
                item.put("scoreValue", "0");
                item.put("creditClass", "0");
                item.put("basicCreditValue", "0");
                item.put("creditValue", "0");
                item.put("acctTag", "0");
                item.put("prepayTag", "0");
                // item.put("productTypeCode", productTypeCode);
                item.put("inDate", GetDateUtils.getDate());
                item.put("openDate", GetDateUtils.getDate());
                item.put("openMode", "0");
                item.put("openDepartId", msg.get("channelId"));// 下面四个不写死
                item.put("openStaffId", msg.get("operatorId"));
                item.put("inDepartId", msg.get("channelId"));
                item.put("inStaffId", msg.get("operatorId"));
                item.put("removeTag", "0");
                item.put("cityCode", msg.get("city"));
                item.put("userStateCodeset", "0");
                item.put("mputeMonthFee", "0");
                item.put("developStaffId", developStaffId);
                item.put("developDate", developDate);
                item.put("developEparchyCode", "0311");// 写死
                item.put("developCityCode", developCityCode);
                item.put("developDepartId", developDepartId);
                item.put("inNetMode", "E");
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

    private Map preMixBase(Map inputMap, String eparchyCode) {
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
        base.put("inModeCode", "E");
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
        // base.put("cityCode", inputMap.get("district"));
        base.put("eparchyCode", eparchyCode);
        base.put("netTypeCode", "33");
        base.put("contact", "");
        base.put("feeStaffId", "");
        base.put("checktypeCode", "0");
        base.put("subscribeId", inputMap.get("tradeId"));
        base.put("brandCode", "4G00");
        base.put("actorName", "");
        return base;
    }

    /**
     * 判断是否为空
     */
    private String isNull(String string) {
        return null == string ? "" : string;
    }

    /**
     * 封装不带日期的属性
     */
    public Map createTradeSubItemNoDate(Object attrTypeCode, Object key, Object value, Object itemId) {
        return MapUtils.asMap("attrTypeCode", attrTypeCode, "attrCode", key, "attrValue", value, "itemId", itemId,
                "xDatatype", "NULL");
    }

}
