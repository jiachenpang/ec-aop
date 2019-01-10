package com.ailk.ecaop.biz.sub.lan;

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
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;

@EcRocTag("N6FixProductChg")
public class N6FixProductChgProcessor extends BaseAopProcessor {

    String eparchyCode;
    static String[] COPYARRAY = { "operatorId", "province", "city", "district", "channelId", "channelType",
            "serialNumber", "areaCode" };

    @Override
    public void process(Exchange exchange) throws Exception {
        // 保存请求msg信息
        Map inputInfo = exchange.getIn().getBody(Map.class);
        String methodCode = exchange.getMethodCode();

        boolean isString = inputInfo.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) inputInfo.get("msg"));
        }
        else {
            msg = (Map) inputInfo.get("msg");
        }
        // 生成N6序列
        eparchyCode = ChangeCodeUtils.changeEparchy(msg);
        String tradeId = GetSeqUtil.getSeqFromN6ess(exchange, "TRADE_ID", eparchyCode);
        exchange.getIn().setBody(inputInfo);
        String itemId = GetSeqUtil.getSeqFromN6ess(exchange, "ITEM_ID", eparchyCode);
        exchange.getIn().setBody(inputInfo);
        // 资费节点专属itemid
        String discntitemId = GetSeqUtil.getSeqFromN6ess(exchange, "ITEM_ID", eparchyCode);
        exchange.getIn().setBody(inputInfo);
        msg.put("discntitemId", discntitemId);
        // 地市转化
        // msg.put("city", ChangeCodeUtils.changeEparchy(msg));
        // 获取请求产品信息
        List<Map> newUserInfoList = (List<Map>) msg.get("newUserInfo");
        List<Map> productInfoList = new ArrayList<Map>();
        List<Map> boradDiscntInfoList = new ArrayList<Map>();
        if (null != newUserInfoList && !newUserInfoList.isEmpty()) {
            productInfoList = (List<Map>) newUserInfoList.get(0).get("productInfo");
            boradDiscntInfoList = (List<Map>) newUserInfoList.get(0).get("boradDiscntInfo");
        }
        // 宽带所需的资费和生效方式
        String delayType = null;
        String delayDiscntId = null;
        String delayDiscntType = null;
        String boradDiscntId = null;
        if (null != boradDiscntInfoList && !boradDiscntInfoList.isEmpty()) {
            boradDiscntId = (String) boradDiscntInfoList.get(0).get("boradDiscntId");
            Map boradDiscntAttr = (Map) boradDiscntInfoList.get(0).get("boradDiscntAttr");
            if (null != boradDiscntAttr && !boradDiscntAttr.isEmpty()) {
                delayType = (String) boradDiscntAttr.get("delayType");
                delayDiscntId = (String) boradDiscntAttr.get("delayDiscntId");
                delayDiscntType = (String) boradDiscntAttr.get("delayDiscntType");
            }
        }
        // 获取订购的主产品和退订的主产品
        String newProductId = "";
        String oldProductId = "";
        for (Map productInfoMap : productInfoList) {
            if ("0".equals(productInfoMap.get("optType")) && "0".equals(productInfoMap.get("productMode"))) {
                newProductId = (String) productInfoMap.get("oldProductId");
            }
            if ("1".equals(productInfoMap.get("optType")) && "0".equals(productInfoMap.get("productMode"))) {
                oldProductId = (String) productInfoMap.get("oldProductId");
            }
        }

        // TODO:预提交准备参数
        Map preDateMap = new HashMap();
        preDateMap = MapUtils.asMap("eparchyCode", msg.get("city"), "boradDiscntId", boradDiscntId, "delayType",
                delayType,
                "delayDiscntId", delayDiscntId,
                "delayDiscntType", delayDiscntType,
                "itemId", itemId, "tradeId", tradeId, "methodCode", methodCode, "productInfo",
                productInfoList, "newProductId", newProductId, "oldProductId", oldProductId, "boradDiscntInfo",
                boradDiscntInfoList, "contactPerson", msg.get("contactPerson"), "contactPhone",
                msg.get("contactPhone"), "discntitemId", msg.get("discntitemId"), "province", msg.get("province"));
        // 调用三户接口
        exchange.getIn().setBody(inputInfo);
        Map threePartInfoMap = threePartInfo(exchange, (String) preDateMap.get("oldProductId"), msg);
        preDateMap.putAll(threePartInfoMap);
        MapUtils.arrayPut(preDateMap, msg, COPYARRAY);
        preData4CbPre(preDateMap);
        // 调统一预提交接口
        Exchange exchange4B6 = ExchangeUtils.ofCopy(exchange, preDateMap);
        Map retPreSubMap = Maps.newHashMap();
        try {
            new LanUtils().preData("ecaop.trades.sccc.cancelPre.paramtersmapping", exchange4B6);
            CallEngine.wsCall(exchange4B6,
                    "ecaop.comm.conf.url.n6ess.services.AOPOrdForNorthSer" + "." + msg.get("province"));
            new LanUtils().xml2Json("ecaop.trades.sccc.cancelPre.template", exchange4B6);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用预提交失败！" + e.getMessage());
        }
        // 对预提交返回做处理
        Map PreSubOut = exchange4B6.getOut().getBody(Map.class);
        List<Map> rspInfo = (List<Map>) PreSubOut.get("rspInfo");
        if (null == rspInfo || rspInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "统一预提交未返回应答信息");
        }
        retPreSubMap.put("provOrderId", rspInfo.get(0).get("provOrderId"));
        // TODO:固话账户名称
        retPreSubMap.put("userName", "");
        Integer totalFee = 0;
        for (Map rspMap : rspInfo) {
            List<Map> provinceOrderInfo = (List<Map>) rspMap.get("provinceOrderInfo");
            if (null == provinceOrderInfo || provinceOrderInfo.isEmpty()) {
                continue;
            }
            for (Map provinceOrder : provinceOrderInfo) {
                totalFee = totalFee + Integer.valueOf(provinceOrder.get("totalFee").toString());
                List<Map> preFeeInfoRsp = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                if (null == preFeeInfoRsp || preFeeInfoRsp.isEmpty()) {
                    continue;
                }
                List<Map> feeList = dealFee(preFeeInfoRsp);
                retPreSubMap.put("feeInfo", feeList);
            }
            retPreSubMap.put("totalFee", totalFee);
        }
        exchange.getOut().setBody(retPreSubMap);
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

    /**
     * 预提交准备参数
     * 
     * @param preDateMap
     */
    private void preData4CbPre(Map inputMap) {
        inputMap.put("ordersId", inputMap.get("tradeId"));
        inputMap.put("operTypeCode", "0");
        Map base = preMixBase(inputMap);
        Map ext = preMixExt(inputMap);
        inputMap.put("ext", ext);
        inputMap.put("base", base);

    }

    private Map preMixExt(Map inputMap) {
        Map ext = new HashMap();
        ext.put("tradeOther", preTradeOtherItem(inputMap));
        ext.put("tradeItem", preTradeItem(inputMap));
        // TODO:请求参数转换。暂时只做主产品变更,不处理活动
        List<Map> productList = (List<Map>) inputMap.get("productInfo");
        List<Map> boradDiscntInfoList = (List<Map>) inputMap.get("boradDiscntInfo");
        List<Map> oldProductList = new ArrayList<Map>();
        for (int i = 0; i < productList.size(); i++) {
            if ("0".equals(productList.get(i).get("optType"))) {
                productList.get(i).put("firstMonBillMode", "02");// 给外围主产品默认02
                // 订购的新产品
                productList.get(i).put("productId", productList.get(i).get("oldProductId"));
            }
            else {
                oldProductList.add(productList.get(i));
            }
        }
        if (null != oldProductList || !oldProductList.isEmpty()) {
            productList.removeAll(oldProductList);
        }
        if (null != productList && !productList.isEmpty()) {
            ext.putAll(TradeManagerUtils.preProductInfo(productList, "00" + inputMap.get("province"), inputMap));
        }
        // 宽带需要传入资费节点,重新塞入discnt节点,作为新增资费下发
        List<Map> discntList = new ArrayList<Map>();
        if (null != boradDiscntInfoList && !boradDiscntInfoList.isEmpty()) {
            for (int j = 0; j < boradDiscntInfoList.size(); j++) {
                Map disMap = new HashMap();
                String boradDiscntId = (String) boradDiscntInfoList.get(j).get("boradDiscntId");
                inputMap.put("elementId", boradDiscntId);
                inputMap.put("PROVINCE_CODE", "00" + inputMap.get("province"));
                inputMap.put("EPARCHY_CODE", eparchyCode);
                disMap = TradeManagerUtils.preProductInfoByElementId(inputMap);
                discntList.add(disMap);
            }

            if (null != discntList && !discntList.isEmpty()) {
                inputMap.put("discntList", discntList);
            }
            ext.put("tradeDiscnt", preDiscntData(inputMap));
        }
        ext.put("tradeSubItem", preTradeSubItem(inputMap));
        // 传入主产品的品牌
        ext.put("tradeUser", preTradeUserItem(inputMap));

        return ext;
    }

    public Map preDiscntData(Map msg) {
        Map tradeDis = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> discnt = (List<Map>) msg.get("discntList");
        for (int i = 0; i < discnt.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("id", msg.get("userId"));
            item.put("idType", "1");
            item.put("userIdA", "-1");
            item.put("productId", discnt.get(i).get("productId"));
            item.put("packageId", discnt.get(i).get("packageId"));
            item.put("discntCode", discnt.get(i).get("discntCode"));
            item.put("specTag", "0");// FIXME
            item.put("relationTypeCode", "");
            item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
            item.put("itemId", msg.get("discntitemId"));
            if (null != discnt.get(i).get("activityTime")) {
                item.put("endDate", discnt.get(i).get("activityTime"));
            }
            else {
                item.put("endDate", "20501231235959");
            }
            if ("50".equals(discnt.get(i).get("productMode"))) {
                item.put("itemId", msg.get("itemId"));
            }
            item.put("modifyTag", "0");
            itemList.add(item);

        }
        Map numberDiscnt = (Map) msg.get("numberDiscnt");
        if (null != numberDiscnt && !numberDiscnt.isEmpty()) {
            Map item = new HashMap();
            item.putAll(numberDiscnt);
            itemList.add(item);
        }
        Map numberDis = (Map) msg.get("numberDis");
        if (null != numberDis && !numberDis.isEmpty()) {
            Map item1 = new HashMap();
            item1.putAll(numberDis);
            itemList.add(item1);

        }
        tradeDis.put("item", itemList);
        return tradeDis;
    }

    private Map preTradeSubItem(Map inputMap) {
        Map TradeSubItem = new HashMap();
        String discntitemId = (String) inputMap.get("discntitemId");
        System.out.println("++++++++++++++discntitemId" + discntitemId);
        String userId = (String) inputMap.get("userId");
        String startDate = GetDateUtils.getNextMonthFirstDayFormat();
        String endDate = "20501231235959";
        LanUtils lan = new LanUtils();
        List<Map> items = new ArrayList<Map>();
        List<Map> boradDiscntInfo = (List<Map>) inputMap.get("boradDiscntInfo");
        if (null != boradDiscntInfo && !boradDiscntInfo.isEmpty()) {
            // 默认不传按续约标准资费b
            String expireDealMode = "1".equals(inputMap.get("delayType").toString()) ? "a"
                    : "3".equals(inputMap.get("delayType").toString()) ? "t" : "b";
            String aDiscntCode = (String) inputMap.get("delayDiscntId");
            String bDiscntCode = (String) inputMap.get("boradDiscntId");
            String delayDiscntType = (String) inputMap.get("delayDiscntType");
            items.add(lan.createTradeSubItemC(discntitemId, "adEnd", "", startDate, endDate));
            items.add(lan.createTradeSubItemC(discntitemId, "expireDealMode", expireDealMode, startDate, endDate));
            items.add(lan.createTradeSubItemC(discntitemId, "aDiscntCode", aDiscntCode, startDate, endDate));
            if ("a".equals(expireDealMode)) {
                items.add(lan.createTradeSubItemC(discntitemId, "bDiscntCode", bDiscntCode, startDate, endDate));
            }
            else {
                items.add(lan.createTradeSubItemC(discntitemId, "bDiscntCode", "", startDate, endDate));
            }
            items.add(lan.createTradeSubItemC(discntitemId, "effectMode", delayDiscntType, startDate, endDate));
            items.add(lan.createTradeSubItemC(discntitemId, "adStart", "0", startDate, endDate));
            items.add(lan.createTradeSubItemC(discntitemId, "cycleNum", "1", startDate, endDate));
            items.add(lan.createTradeSubItemC(discntitemId, "cycle", "24", startDate, endDate));
            items.add(lan.createTradeSubItemC(discntitemId, "fixedHire", "24", startDate, endDate));
            items.add(lan.createTradeSubItemC(discntitemId, "cycleFee", "1800", startDate, endDate));
            items.add(lan.createTradeSubItemC(discntitemId, "recharge", "", startDate, endDate));
            items.add(lan.createTradeSubItemC(discntitemId, "callBack", "0", startDate, endDate));
        }
        items.add(lan.createTradeSubItem("SHARE_NBR", "", userId));
        items.add(lan.createTradeSubItem("USETYPE", "1", userId));
        items.add(lan.createTradeSubItem("WOPAY_MONEY", "", userId));
        items.add(lan.createTradeSubItem("ACCESS_TYPE", "B61", userId));//
        items.add(lan.createTradeSubItem("TIME_LIMIT_ID", "2", userId));
        items.add(lan.createTradeSubItem("CONNECTNETMODE", "1", userId));
        items.add(lan.createTradeSubItem("ISWAIL", "0", userId));
        items.add(lan.createTradeSubItem("TERMINAL_SN", "", userId));
        items.add(lan.createTradeSubItem("TERMINAL_MAC", "", userId));
        items.add(lan.createTradeSubItem("ISWOPAY", "0", userId));
        items.add(lan.createTradeSubItem("KDLX_2061", "N", userId));
        items.add(lan.createTradeSubItem("TERMINALSRC_MODE", "A001", userId));
        items.add(lan.createTradeSubItem("USER_TYPE_CODE", "0", userId));
        items.add(lan.createTradeSubItem("CB_ACCESS_TYPE", "A13", userId));
        items.add(lan.createTradeSubItem("ACCT_NBR", "", userId));//
        items.add(lan.createTradeSubItem("COMMPANY_NBR", "", userId));
        items.add(lan.createTradeSubItem("TOWN_FLAG", "C", userId));
        items.add(lan.createTradeSubItem("TERMINAL_MODEL", "", userId));
        items.add(lan.createTradeSubItem("SPEED", "10", userId));//
        items.add(lan.createTradeSubItem("HZGS_0000", "", userId));
        items.add(lan.createTradeSubItem("EXPECT_RATE", "", userId));
        items.add(lan.createTradeSubItem("TERMINAL_TYPE", "2", userId));
        items.add(lan.createTradeSubItem("TERMINAL_BRAND", "", userId));
        items.add(lan.createTradeSubItem("COLLINEAR_TYPE", "X3", userId));
        items.add(lan.createTradeSubItem("ACCT_PASSWD", inputMap.get("userPasswd"), userId));//
        items.add(lan.createTradeSubItem("LINK_NAME", inputMap.get("contactPerson"), userId));
        items.add(lan.createTradeSubItem("OTHERCONTACT", "", userId));
        items.add(lan.createTradeSubItem("LINK_PHONE", inputMap.get("contactPhone"), userId));//
        TradeSubItem.put("item", items);
        return TradeSubItem;
    }

    private Map preTradeItem(Map inputMap) {
        Map TradeItem = new HashMap();
        LanUtils lan = new LanUtils();
        List<Map> items = new ArrayList<Map>();
        items.add(lan.createAttrInfoNoTime("PH_NUM", ""));
        items.add(lan.createAttrInfoNoTime("SFGX_2060", "N"));
        items.add(lan.createAttrInfoNoTime("GXLX_TANGSZ", ""));
        items.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", (String) inputMap.get("city")));
        items.add(LanUtils.createTradeItem("DEVELOP_DEPART_ID", ""));
        items.add(LanUtils.createTradeItem("MARKETING_MODE", "1"));
        items.add(LanUtils.createTradeItem("DEVELOP_STAFF_ID", ""));
        items.add(LanUtils.createTradeItem("IMMEDIACY_INFO", "0"));
        items.add(LanUtils.createTradeItem("USER_ACCPDATE", ""));
        items.add(LanUtils.createTradeItem("SUB_TYPE", "0"));
        TradeItem.put("item", items);
        return TradeItem;
    }

    private Map preTradeUserItem(Map inputMap) {
        Map TradeUser = new HashMap();
        List<Map> item = new ArrayList<Map>();
        List<Map> productList = (List<Map>) inputMap.get("productInfo");
        List<Map> boradDiscntInfo = (List<Map>) inputMap.get("boradDiscntInfo");
        String netTypeCode = null;
        if (null == boradDiscntInfo || boradDiscntInfo.isEmpty()) {
            netTypeCode = "0030";
        }
        else {
            netTypeCode = "0040";
        }
        for (Map productMap : productList) {
            if ("0".equals(productMap.get("optType")) && "0".equals(productMap.get("productMode"))) {
                Map itemMap = MapUtils.asMap("userId", inputMap.get("userId"), "productId",
                        productMap.get("oldProductId"),
                        "xDatatype", "NULL", "brandCode", inputMap.get("brandCode"), "netTypeCode", netTypeCode,
                        "productTypeCode", inputMap.get("productTypeCode"));
                item.add(itemMap);
            }
        }
        TradeUser.put("item", item);
        return TradeUser;
    }

    private Map preTradeOtherItem(Map inputMap) {
        Map TradeOther = new HashMap();
        List<Map> item = new ArrayList<Map>();
        String monLastTime = GetDateUtils.getMonthLastDayFormat();
        List<Map> oldProductList = ProductManagerUtils.getProductInfoWithLimit((String) inputMap.get("oldProductId"),
                "00", "00" + inputMap.get("province"),
                (String) inputMap.get("city"), "02");
        if (null == oldProductList || oldProductList.isEmpty()) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + inputMap.get("oldProductId")
                    + "】的产品信息");
        }
        String subId = String.valueOf(oldProductList.get(0).get("PRODUCT_ID"));
        List<Map> subOldProductItem = ProductManagerUtils.getProductItemWithoutPtype(subId,
                (String) inputMap.get("oldProductId"),
                "00" + inputMap.get("province"));
        if (null == subOldProductItem || subOldProductItem.isEmpty()) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品属性表【" + inputMap.get("oldProductId") + "】的产品信息");
        }
        String subProductTypeCode = TradeManagerUtils.getValueFromItem("PRODUCT_TYPE_CODE", subOldProductItem);
        String subBrandCode = TradeManagerUtils.getValueFromItem("BRAND_CODE", subOldProductItem);

        Map itemMap = MapUtils.asMap("xDatatype", null, "rsrvValueCode", "NEXP", "rsrvValue",
                inputMap.get("userId"), "rsrvStr1", inputMap.get("oldProductId"), "rsrvStr2", "00", "rsrvStr3",
                "-9", "rsrvStr4", subProductTypeCode, "rsrvStr5", subProductTypeCode, "rsrvStr6", "-1", "rsrvStr7",
                "0",
                "rsrvStr8", "", "rsrvStr9", subBrandCode, "rsrvStr10", inputMap.get("serialNumber"), "modifyTag", "1",
                "startDate", inputMap.get("startDate"), "endDate", monLastTime);
        item.add(itemMap);

        TradeOther.put("item", item);
        return TradeOther;
    }

    private Map preMixBase(Map inputMap) {
        Map base = new HashMap();
        // 获取订购的主产品
        List<Map> productInfo = (List<Map>) inputMap.get("productInfo");
        for (Map productInfoMap : productInfo) {
            if ("0".equals(productInfoMap.get("optType")) && "0".equals(productInfoMap.get("productMode"))) {
                base.put("productId", productInfoMap.get("oldProductId"));
            }
        }
        // 获取宽带资费节点
        List<Map> boradDiscntInfo = (List<Map>) inputMap.get("boradDiscntInfo");
        base.put("advancePay", "0");
        base.put("userDiffCode", "00");
        base.put("acceptDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("inModeCode", "E");
        base.put("serinalNamber", inputMap.get("serialNumber"));
        base.put("usecustId", inputMap.get("custId"));
        base.put("actorCertNum", "");
        base.put("remark", "");
        base.put("feeState", "");
        base.put("contactPhone", inputMap.get("contactPhone"));
        base.put("nextDealTag", "Z");
        base.put("contactAddress", inputMap.get("contactAddress"));
        base.put("olcomTag", "0");
        base.put("custId", inputMap.get("custId"));
        base.put("acctId", inputMap.get("acctId"));
        base.put("userId", inputMap.get("userId"));
        base.put("custName", inputMap.get("custName"));
        base.put("foregift", "0");
        base.put("execTime", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("termIp", "132.35.87.221");
        base.put("actorCertTypeId", "");
        base.put("chkTag", "0");
        base.put("tradeId", inputMap.get("tradeId"));
        base.put("actorPhone", "");
        base.put("operFee", "0");
        base.put("cancelTag", "0");
        base.put("tradeTypeCode", "0127");
        base.put("cityCode", inputMap.get("district"));
        base.put("eparchyCode", inputMap.get("city"));
        if (null == boradDiscntInfo || boradDiscntInfo.isEmpty()) {
            base.put("netTypeCode", "0030");
        }
        else {
            base.put("netTypeCode", "0040");
        }
        base.put("contact", "");
        base.put("feeStaffId", "");
        base.put("checktypeCode", "0");
        base.put("subscribeId", inputMap.get("tradeId"));
        base.put("brandCode", inputMap.get("brandCode"));
        base.put("actorName", "");
        return base;
    }

    /**
     * 调三户接口
     * 
     * @param exchange
     * @param oldProductId
     * @return
     * @throws Exception
     */
    private Map threePartInfo(Exchange exchange, String oldProductId, Map msg) throws Exception {
        // 掉三户接口
        String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType" };
        Map threePartMap = MapUtils.asMap("getMode", "11111100001000010000100000", "serialNumber",
                msg.get("serialNumber"),
                "tradeTypeCode", "9999");
        MapUtils.arrayPut(threePartMap, msg, copyArray);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trade.n6.checkUserParametersMapping", threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.UsrForNorthSer" + "." + msg.get("province"));
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        Map retMap = threePartExchange.getOut().getBody(Map.class);
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
        // 需要通过userInfo的返回,拼装老产品信息
        Map userInfoMap = retUserList.get(0);
        Object oldProduct = userInfoMap.get("productId");
        List<Map> discntList = new ArrayList<Map>();
        List<Map> discntInfo = (List<Map>) userInfoMap.get("discntInfo");
        for (Map discnt : discntInfo) {
            if (oldProduct.equals(discnt.get("productId"))) {
                Map element = new HashMap();
                element.put("productId", oldProduct);
                element.put("packageId", discnt.get("packageId"));
                element.put("discntCode", discnt.get("discntCode"));
                element.put("itemId", discnt.get("itemId"));
                discntList.add(element);
            }
        }
        preDateMap.put("oldProDiscnt", discntList);
        preDateMap.put("acctId", retAcctList.get(0).get("acctId"));
        preDateMap.put("userId", retUserList.get(0).get("userId"));
        preDateMap.put("custId", reCusttList.get(0).get("custId"));
        preDateMap.put("custName", reCusttList.get(0).get("custName"));
        preDateMap.put("userPasswd", retUserList.get(0).get("userPasswd"));
        // 老产品相关信息
        preDateMap.put("productTypeCode", retUserList.get(0).get("productTypeCode"));
        preDateMap.put("brandCode", retUserList.get(0).get("brandCode"));
        List<Map> threePartProductList = (List<Map>) retUserList.get(0).get("productInfo");
        String startDate = null;
        for (int i = 0; i < threePartProductList.size(); i++) {
            if (oldProductId.equals(threePartProductList.get(i).get("productId"))) {
                startDate = (String) threePartProductList.get(i).get("productActiveTime");
                preDateMap.put("startDate", startDate);
            }
        }
        if (null == startDate || "".equals(startDate)) {
            throw new EcAopServerBizException("9999", "三户信息校验接口未返回老产品生效时间");
        }
        return preDateMap;
    }

}
