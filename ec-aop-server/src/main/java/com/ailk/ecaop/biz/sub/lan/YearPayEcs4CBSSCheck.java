package com.ailk.ecaop.biz.sub.lan;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.lang.RDate;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.TransReqParamsMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.DateUtils;
import com.ailk.ecaop.biz.product.DealNewCbssProduct;
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
import com.alibaba.fastjson.JSON;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class YearPayEcs4CBSSCheck implements YearPayEcsCheck {

    protected String tradeId = "111";
    protected String userId = "111";
    protected String custId = "111";
    protected String acctId = "111";
    protected String itemId = "111";
    // 资费专属itemId
    protected String discntItemId = "111";
    private final String ERROR_CODE = "8888";

    @Override
    public Map yearPayEcsCheck(Exchange exchange, Map msg) throws Exception {
        // 调用三户接口
        Map returnMap = new HashMap();
        Map threePartInfo = callThreePart(exchange, msg);
        if ("1".equals(msg.get("changeTag")) || IsEmptyUtils.isEmpty(msg.get("changeTag"))) {
            new TransReqParamsMappingProcessor().process(exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.ec-aop.rest");
            Map result = JSON.parseObject(exchange.getOut().getBody(String.class));
            result.put("isNoChangeProduct", "0");
            return result;
        }
        else if ("2".equals(msg.get("changeTag"))) {
            // 初始化预提交请求中，各个ID
            initIds(exchange);

        List<Map> productInfoList = (List<Map>) msg.get("productInfo");
        List<Map> broadDiscntInfo = (List<Map>) msg.get("broadDiscntInfo");
        String speedLevel = null == (String) msg.get("speedLevel") ? "" : (String) msg.get("speedLevel");
        String eparchyCode = ChangeCodeUtils.changeEparchy(msg);

        // 宽带所需的资费和生效方式
        String delayType = null;
        String delayDiscntId = null;
        String delayDiscntType = null;
        String broadDiscntId = null;

        broadDiscntId = (String) broadDiscntInfo.get(0).get("broadDiscntId");
        Map broadDiscntAttr = (Map) broadDiscntInfo.get(0).get("broadDiscntAttr");
        if (null != broadDiscntAttr && !broadDiscntAttr.isEmpty()) {
            delayType = (String) broadDiscntAttr.get("delayType");
            delayDiscntId = (String) broadDiscntAttr.get("delayDiscntId");
            delayDiscntType = (String) broadDiscntAttr.get("delayDiscntType");
        }
        String startDate4Order = DateUtils.getDate();
        startDate4Order = "0".equals(delayDiscntId) ? DateUtils.getDate() : DateUtils.getNextMonthFirstDay();
        // 获取订购的主产品和退订的主产品
        String newProductId = "";
        String oldProductId = "";
        String orderEndDate = "";
        for (Map productInfoMap : productInfoList) {
            if ("0".equals(productInfoMap.get("optType")) && "0".equals(productInfoMap.get("productMode"))) {
                newProductId = (String) productInfoMap.get("oldProductId");
                orderEndDate = (String) productInfoMap.get("brandNumber");
            }
            if ("1".equals(productInfoMap.get("optType")) && "0".equals(productInfoMap.get("productMode"))) {
                oldProductId = (String) productInfoMap.get("oldProductId");
            }
        }

        // ((List<Map>) (threePartInfo.get("userInfo"))).get(0).get("custId")
        Object oldProductStartDate = GetDateUtils.getDate();
        Object orserProductStartDate = GetDateUtils.getDate();
        List<Map> userInfo = (List<Map>) threePartInfo.get("userInfo");
        for (Map user : userInfo) {
            oldProductStartDate = user.get("openDate");
            orserProductStartDate = user.get("openDate");
            List<Map> productInfo = (List<Map>) user.get("productInfo");
            if (IsEmptyUtils.isEmpty(productInfo)) {
                continue;
            }
            for (Map product : productInfo) {
                if (oldProductId.equals(product.get("productId"))) {
                    oldProductStartDate = product.get("productActiveTime");
                    break;
                }
                if (newProductId.equals(product.get("productId"))) {
                    orserProductStartDate = product.get("productActiveTime");
                    break;
                }
            }
        }
        // 预提交准备参数
        Map preDateMap = new HashMap();
        preDateMap = MapUtils.asMap("eparchyCode", eparchyCode, "broadDiscntId", broadDiscntId, "delayType",
                delayType, "delayDiscntId", delayDiscntId, "delayDiscntType", delayDiscntType,
                "itemId", itemId, "tradeId", tradeId, "methodCode", exchange.getAppCode(), "productInfo",
                productInfoList, "newProductId", newProductId, "oldProductId", oldProductId, "broadDiscntInfo",
                broadDiscntInfo, "contactPerson", msg.get("contactPerson"), "contactPhone",
                msg.get("contactPhone"), "discntitemId", msg.get("discntitemId"), "speedLevel",
                //
                "30M", "startDate", GetDateUtils.getNextMonthFirstDayFormat(),
                "userId", ((List<Map>) (threePartInfo.get("userInfo"))).get(0).get("userId"), "serialNumber",
                ((List<Map>) (threePartInfo.get("userInfo"))).get(0).get("serialNumber"), "acctId", acctId, "orderNo",
                msg.get("orderNo"), "custId", ((List<Map>) (threePartInfo.get("custInfo"))).get(0).get("custId"),
                "brandCode", "GZKD", "oldProductStartDate", oldProductStartDate, "method", exchange.getMethodCode(),
                "orderEndDate", orderEndDate, "orserProductStartDate", orserProductStartDate, "startDate4Order",
                startDate4Order);
        preDateMap.putAll(threePartInfo);
        MapUtils.arrayPut(preDateMap, msg, MagicNumber.COPYARRAY);
        // 拼装预提交信息
        Map preSubmitMap = PreData4PreSubmit(preDateMap, exchange.getAppCode(), exchange.getMethodCode());
        Exchange preSubmitExchange = ExchangeUtils.ofCopy(exchange, preSubmitMap);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trades.sccc.cancelPre.paramtersmapping", preSubmitExchange);
        CallEngine.wsCall(preSubmitExchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.sccc.cancelPre.template", preSubmitExchange);
        // 处理预提交返回,便于正式提交使用
        Map preSubOut = preSubmitExchange.getOut().getBody(Map.class);
        Map rspInfo = ((List<Map>) preSubOut.get("rspInfo")).get(0);
        if (null == rspInfo || rspInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "cBSS统一预提交未返回应答信息");
        }
        returnMap.put("provOrderId", rspInfo.get("provOrderId"));
        returnMap.put("bssOrderId", rspInfo.get("provOrderId"));
        int totalFee = 0;
        for (Map provinceOrder : (List<Map>) rspInfo.get("provinceOrderInfo")) {
            totalFee = totalFee + Integer.valueOf(provinceOrder.get("totalFee").toString());
            List<Map> preFeeInfoRsp = (List<Map>) provinceOrder.get("preFeeInfoRsp");
            if (null == preFeeInfoRsp || preFeeInfoRsp.isEmpty()) {
                continue;
            }
            List<Map> feeList = dealFee(preFeeInfoRsp);
            returnMap.put("feeInfo", feeList);
        }
        returnMap.put("totalFee", Integer.toString(totalFee));
        returnMap.putAll(rspInfo);
        returnMap.putAll(preDateMap);
        return returnMap;
    }
        else if ("3".equals(msg.get("changeTag"))) {
            throw new EcAopServerBizException(ERROR_CODE, "不支持非主产品变更,请核实");
        }
        else {
            throw new EcAopServerBizException(ERROR_CODE, "产品变更方式[" + msg.get("changeTag")
                    + "]不在[1.趸交;2.变更产品;3.变更非主产品");
        }
    }

    @Override
    public Map yearPayEcsSub(Exchange exchange, Map preSubmitRet, Map msg) throws Exception {
        Map submitMap = new HashMap();
        MapUtils.arrayPut(submitMap, msg, MagicNumber.COPYARRAY);
        Object orderNo = msg.get("orderNo");
        submitMap.put("orderNo", orderNo);

        // Map retMap = new YearPayEcs4N6Check().dealSubmit(submitMap, orderNo, preSubmitRet, msg);
        preSubmitRet.put("provOrderId", preSubmitRet.get("provOrderId"));
        preSubmitRet.put("orderNo", preSubmitRet.get("provOrderId"));
        preSubmitRet.put("operationType", "02");
        preSubmitRet.put("origTotalFee", "0");
        preSubmitRet.put("cancleTotalFee", preSubmitRet.get("totalFee"));
        preSubmitRet.put("noteType", "1");
        // {totalFee=72000, provOrderId=1716092858663743, bssOrderId=1716092858663743,
        // feeInfo=[{feeId=400000, maxRelief=0, feeCategory=2, origFee=72000, feeDes=[预存]带包一年-包月费)}]}
        List<Map> subOrderSubReq = new ArrayList<Map>();
        Map subOrderSub = new HashMap();
        subOrderSub.put("subProvinceOrderId", preSubmitRet.get("provOrderId"));
        subOrderSub.put("subOrderId", preSubmitRet.get("provOrderId"));
        List<Map> feeInfos = (List<Map>) preSubmitRet.get("feeInfo");
        List<Map> feeInfoList = new ArrayList<Map>();
        if (feeInfos != null && 0 != feeInfos.size()) {
            for (Map feeInfo : feeInfos) {
                Map retFee = new HashMap();
                retFee.put("feeCategory", feeInfo.get("feeCategory"));
                retFee.put("feeId", feeInfo.get("feeId"));
                retFee.put("feeDes", feeInfo.get("feeDes"));
                retFee.put("operateType", "1");
                retFee.put("origFee", preSubmitRet.get("totalFee"));
                retFee.put("calculateTag", "N");
                retFee.put("calculateId", GetSeqUtil.getSeqFromCb());
                retFee.put("calculateDate", GetDateUtils.getDate());
                feeInfoList.add(retFee);

            }
        }
        subOrderSub.put("feeInfo", feeInfoList);
        subOrderSubReq.add(subOrderSub);
        preSubmitRet.put("subOrderSubReq", subOrderSubReq);
        preSubmitRet.putAll(msg);
        Map body = new HashMap();
        body.put("msg", preSubmitRet);
        exchange.getIn().setBody(body);

        new LanUtils().preData("ecaop.trades.mpsb.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        new LanUtils().xml2Json("ecaop.trades.mpsb.template", exchange);
        Map out = exchange.getOut().getBody(Map.class);

        List feeList = new ArrayList();
        Map tempFeeMap = new HashMap();

        List<Map> provinceOrderInfo = (List<Map>) (preSubmitRet.get("provinceOrderInfo"));
        Map provinceInfo = provinceOrderInfo.get(0);
        List<Map> province = (List<Map>) provinceInfo.get("preFeeInfoRsp");
        tempFeeMap.put("operateType", "1");
        tempFeeMap.put("fee", preSubmitRet.get("totalFee"));

        tempFeeMap.put("feeMode", province.get(0).get("feeMode"));
        tempFeeMap.put("feeTypeCode", province.get(0).get("feeTypeCode"));
        tempFeeMap.put("feeTypeName", province.get(0).get("feeTypeName"));
        tempFeeMap.put("maxDerateFee", province.get(0).get("maxDerateFee"));
        // tempFeeMap.put("totalFee", preSubmitRet.get("totalFee"));
        feeList.add(tempFeeMap);
        Map rspMap = new HashMap();

        rspMap.put("code", "0000");
        rspMap.put("detail", "成功");
        rspMap.put("provOrderId", preSubmitRet.get("provOrderId"));//
        List<Map> custInfo = (List<Map>) (preSubmitRet.get("custInfo"));

        List<Map> userInfo = (List<Map>) (preSubmitRet.get("userInfo"));
        rspMap.put("productName", "山东宽带2M基本套餐（低）");
        List<Map> broadDiscntInfo = (List<Map>) (preSubmitRet.get("broadDiscntInfo"));
        // rspMap.put("productName", userInfo.get(0).get("productName"));
        rspMap.put("custName", custInfo.get(0).get("custName"));
        rspMap.put("discntName", broadDiscntInfo.get(0).get("broadDiscntId"));//
        rspMap.put("productType", "1");//
        List<Map> feeInfo = (List<Map>) (preSubmitRet.get("feeInfo"));
        rspMap.put("startDate", preSubmitRet.get("startDate4Order"));// startDate4Order
        String endDate = DateUtils.lastDay((String) preSubmitRet.get("startDate4Order"));
        rspMap.put(
                "endDate",
                DateUtils.addMonths(DateUtils.getBeforeDayLastTime(endDate),
                        (String) (preSubmitRet.get("orderEndDate"))));
        rspMap.put("totalFee", preSubmitRet.get("totalFee"));

        rspMap.put("feeInfo", feeList);

        return rspMap;


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
            retFee.put("maxRelief", fee.get("maxDerateFee"));
            retFee.put("origFee", fee.get("fee"));
            retFeeList.add(retFee);
        }
        return retFeeList;

    }

    /**
     * 初始化各个ID，预提交中会用
     * 
     * @param exchange
     */
    private void initIds(Exchange exchange) {
        Map msg = new HashMap((Map) exchange.getIn().getBody(Map.class).get("msg"));
        Map tradeMap = new HashMap();
        MapUtils.arrayPut(tradeMap, msg, MagicNumber.COPYARRAY);

        tradeId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, tradeMap), "seq_trade_id");
        itemId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, tradeMap), "seq_item_id");
        userId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, tradeMap), "seq_user_id");
        custId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, tradeMap), "seq_cust_id");
        acctId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, tradeMap), "seq_acct_id");
        discntItemId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, tradeMap), "seq_item_id");
    }

    /**
     * 调用三户接口,获取预提交的必要信息
     * 
     * @param exchange
     * @param msg
     * @return
     * @throws Exception
     */
    private Map callThreePart(Exchange exchange, Map msg) throws Exception {
        Map threePartMap = MapUtils.asMap("getMode", "101001101010001101", "serialNumber", msg.get("serialNumber"),
                "tradeTypeCode", "9999", "serviceClassCode", "0040");
        MapUtils.arrayPut(threePartMap, msg, MagicNumber.COPYARRAY);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trade.cbss.checkUserParametersMapping", threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        return threePartExchange.getOut().getBody(Map.class);
    }

    private Map PreData4PreSubmit(Map inputMap, String appCode, String method) {
        inputMap.put("ordersId", tradeId);
        inputMap.put("operTypeCode", "0");
        inputMap.put("ext", preExt4PreSubmit(inputMap, method));
        inputMap.put("base", preBase4PreSubmit(inputMap, appCode));
        return inputMap;
    }

    private Map preExt4PreSubmit(Map inputMap, String method) {
        Map ext = new HashMap();
        ext.put("tradeOther", preTradeOther(inputMap));
        ext.put("tradeItem", preTradeItem(inputMap));
        // TODO:请求参数转换。暂时只做主产品变更,不处理活动
        List<Map> productList = (List<Map>) inputMap.get("productInfo");
        List<Map> broadDiscntInfoList = (List<Map>) inputMap.get("broadDiscntInfo");
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
            inputMap.put("methodCode", method);
            ext.putAll(TradeManagerUtils.newPreProductInfo(productList, "00" + inputMap.get("province"), inputMap));
        }
        // 宽带需要传入资费节点,重新塞入discnt节点,作为新增资费下发
        List<Map> discntList = new ArrayList<Map>();
        if (null != broadDiscntInfoList && !broadDiscntInfoList.isEmpty()) {
            for (int j = 0; j < broadDiscntInfoList.size(); j++) {
                Map disMap = new HashMap();
                String broadDiscntId = (String) broadDiscntInfoList.get(j).get("broadDiscntId");
                inputMap.put("PROVINCE_CODE", "00" + inputMap.get("province"));
                inputMap.put("ELEMENT_ID", broadDiscntId);
                disMap = TradeManagerUtils.preProductInfoByElementId4CB(inputMap);
                discntList.add(disMap);
            }

            if (null != discntList && !discntList.isEmpty()) {
                inputMap.put("discntList", discntList);
            }
            ext.put("tradeDiscnt", preDiscntData(inputMap));
        }
        ext.put("tradeSubItem", preTradeSubItem(inputMap));
        // 传入主产品的品牌
        ext.put("tradeUser", preTradeUser(inputMap));

        return ext;
    }

    private Map preTradeSubItem(Map inputMap) {
        String discntitemId = itemId;
        // 默认开始结束时间
        String startDate = GetDateUtils.getNextMonthFirstDayFormat();
        String endDate = "20501231235959";
        Map TradeSubItem = new HashMap();
        LanUtils lan = new LanUtils();
        List<Map> items = new ArrayList<Map>();
        List<Map> broadDiscntInfo = (List<Map>) inputMap.get("broadDiscntInfo");
        if (null != broadDiscntInfo && !broadDiscntInfo.isEmpty()) {
            // 默认不传按续约标准资费b
            String expireDealMode = "1".equals(inputMap.get("delayType")) ? "a"
                    : "3".equals(inputMap.get("delayType")) ? "t" : "b";
            String aDiscntCode = (String) inputMap.get("delayDiscntId");
            String bDiscntCode = (String) inputMap.get("broadDiscntId");
            String delayDiscntType = (String) inputMap.get("delayDiscntType");
            items.add(lan.createTradeSubItemC(discntitemId, "adEnd", "", startDate, endDate));
            // items.add(lan.createTradeSubItemC(discntitemId, "expireDealMode", expireDealMode, startDate, endDate));
            items.add(lan.createTradeSubItemC(discntitemId, "expireDealMode", expireDealMode, startDate, endDate));
            // items.add(lan.createTradeSubItemC(discntitemId, "aDiscntCode", aDiscntCode, startDate, endDate));
            items.add(lan.createTradeSubItemC(discntitemId, "aDiscntCode", "7130101", startDate, endDate));
            if ("a".equals(expireDealMode)) {
                // items.add(lan.createTradeSubItemC(discntitemId, "bDiscntCode", bDiscntCode, startDate, endDate));

                items.add(lan.createTradeSubItemC(discntitemId, "bDiscntCode", "b", startDate, endDate));
            }
            else {
                // items.add(lan.createTradeSubItemC(discntitemId, "bDiscntCode", "", startDate, endDate));
                items.add(lan.createTradeSubItemC(discntitemId, "bDiscntCode", "b", startDate, endDate));
            }
            // items.add(lan.createTradeSubItemC(discntitemId, "effectMode", delayDiscntType, startDate, endDate));
            items.add(lan.createTradeSubItemC(discntitemId, "effectMode", "1", startDate, endDate));
            items.add(lan.createTradeSubItemC(discntitemId, "adStart", GetDateUtils.getNextMonthFirstDayFormat(),
                    startDate, endDate));
            items.add(lan.createTradeSubItemC(discntitemId, "cycleNum", "1", startDate, endDate));
            items.add(lan.createTradeSubItemC(discntitemId, "cycle", "12", startDate, endDate));
            items.add(lan.createTradeSubItemC(discntitemId, "fixedHire", "24", startDate, endDate));
            // items.add(lan.createTradeSubItemC(discntitemId, "cycleFee", "0", startDate, endDate));
            items.add(lan.createTradeSubItemC(discntitemId, "cycleFee", "720", startDate, endDate));
            items.add(lan.createTradeSubItemC(discntitemId, "recharge", "", startDate, endDate));
            items.add(lan.createTradeSubItemC(discntitemId, "callBack", "0", startDate, endDate));

        }
        items.add(lan.createTradeSubItem("SHARE_NBR", "", discntitemId));
        items.add(lan.createTradeSubItem("USETYPE", "1", discntitemId));
        items.add(lan.createTradeSubItem("WOPAY_MONEY", "", discntitemId));
        items.add(lan.createTradeSubItem("ACCESS_TYPE", "B61", discntitemId));//
        items.add(lan.createTradeSubItem("TIME_LIMIT_ID", "2", discntitemId));
        items.add(lan.createTradeSubItem("CONNECTNETMODE", "1", discntitemId));
        items.add(lan.createTradeSubItem("ISWAIL", "0", discntitemId));
        items.add(lan.createTradeSubItem("TERMINAL_SN", "", discntitemId));
        items.add(lan.createTradeSubItem("TERMINAL_MAC", "", discntitemId));
        items.add(lan.createTradeSubItem("ISWOPAY", "0", discntitemId));
        items.add(lan.createTradeSubItem("KDLX_2061", "N", discntitemId));
        items.add(lan.createTradeSubItem("TERMINALSRC_MODE", "A001", discntitemId));
        items.add(lan.createTradeSubItem("USER_TYPE_CODE", "0", discntitemId));
        items.add(lan.createTradeSubItem("CB_ACCESS_TYPE", "A13", discntitemId));
        items.add(lan.createTradeSubItem("ACCT_NBR", "", discntitemId));//
        items.add(lan.createTradeSubItem("COMMPANY_NBR", "", discntitemId));
        items.add(lan.createTradeSubItem("TOWN_FLAG", "C", discntitemId));
        items.add(lan.createTradeSubItem("TERMINAL_MODEL", "", discntitemId));
        // 判断宽带下发速率
        if (null != inputMap.get("speedLevel") && !"".equals(inputMap.get("speedLevel"))) {
            String speed = (String) inputMap.get("speedLevel");
            speed = speed.replace("M", "");
            // items.add(lan.createTradeSubItem("SPEED", inputMap.get("speedLevel"), discntitemId));
            items.add(lan.createTradeSubItem("SPEED", "30", discntitemId));
        }
        else {
            items.add(lan.createTradeSubItem("SPEED", "10", discntitemId));//
        }
        items.add(lan.createTradeSubItem("HZGS_0000", "", discntitemId));
        items.add(lan.createTradeSubItem("EXPECT_RATE", "", discntitemId));
        items.add(lan.createTradeSubItem("TERMINAL_TYPE", "2", discntitemId));
        items.add(lan.createTradeSubItem("TERMINAL_BRAND", "", discntitemId));
        items.add(lan.createTradeSubItem("COLLINEAR_TYPE", "X3", discntitemId));
        items.add(lan.createTradeSubItem("ACCT_PASSWD", inputMap.get("userPasswd"), discntitemId));//
        items.add(lan.createTradeSubItem("LINK_NAME", inputMap.get("contactPerson"), discntitemId));
        items.add(lan.createTradeSubItem("OTHERCONTACT", "", discntitemId));
        items.add(lan.createTradeSubItem("LINK_PHONE", inputMap.get("contactPhone"), discntitemId));//
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
            item.put("itemId", msg.get("itemId"));
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

    /**
     * 在准备EXT节点时，需要向msg塞入新产品的编码和品牌信息
     * checkType、chkTag未准备，不知如何塞值
     * 
     * @param threePartInfo
     * @param msg
     * @return
     */
    private Map preBase4PreSubmit(Map inputMap, String appCode) {
        Map base = new HashMap();
        // 获取订购的主产品
        List<Map> productInfo = (List<Map>) inputMap.get("productInfo");
        for (Map productInfoMap : productInfo) {
            if ("0".equals(productInfoMap.get("optType")) && "0".equals(productInfoMap.get("productMode"))) {
                base.put("productId", productInfoMap.get("oldProductId"));
            }
        }
        // 获取宽带资费节点
        List<Map> broadDiscntInfo = (List<Map>) inputMap.get("broadDiscntInfo");
        base.put("advancePay", "0");
        base.put("userDiffCode", "00");
        base.put("acceptDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
        base.put("serinalNamber", inputMap.get("serialNumber"));
        base.put("usecustId", inputMap.get("custId"));
        base.put("actorCertNum", "");
        base.put("remark", "");
        base.put("feeState", "0");
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
        if (null == broadDiscntInfo || broadDiscntInfo.isEmpty()) {
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

    private Map preTradeUser(Map inputMap) {
        Map tradeUser = new HashMap();
        List<Map> item = new ArrayList<Map>();
        List<Map> productList = (List<Map>) inputMap.get("productInfo");
        List<Map> broadDiscntInfo = (List<Map>) inputMap.get("broadDiscntInfo");
        String netTypeCode = null;
        if (null == broadDiscntInfo || broadDiscntInfo.isEmpty()) {
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
        tradeUser.put("item", item);
        return tradeUser;
    }

    private Map preTradeOther(Map inputMap) {
        Map TradeOther = new HashMap();
        List<Map> item = new ArrayList<Map>();
        String monLastTime = GetDateUtils.getMonthLastDayFormat();
        List<Map> oldProductList = queryProductInfoWithLimit("00" + inputMap.get("province"),
                (String) inputMap.get("oldProductId"), (String) inputMap.get("eparchyCode"), "02", "00");
        if (null == oldProductList || oldProductList.isEmpty()) {
            throw new EcAopServerBizException("9999", "在产品映射表中未查询到产品ID【" + inputMap.get("oldProductId")
                    + "】的产品信息");
        }
        String subId = String.valueOf(oldProductList.get(0).get("PRODUCT_ID"));
        List<Map> subOldProductItem = queryProductItemInfoNoPtype(subId);
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
                "startDate", inputMap.get("oldProductStartDate"), "endDate", monLastTime);
        item.add(itemMap);

        TradeOther.put("item", item);
        return TradeOther;
    }
    /**
     * @param provinceCode;省份编码++++
     * @param commodityId;产品ID
     * @param eparchyCode;地市编码
     * @param firstMonBillMode;首月付费模式
     * @param productMode;产品类型00主产品；01附加产品；04活动
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private List<Map> queryProductItemInfoNoPtype(String subId) {
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        Map param = new HashMap();
        param.put("PRODUCT_ID", subId);
        return n25Dao.queryProductAndPtypeProduct(param);
    }

    /**
     * @param provinceCode;省份编码
     * @param commodityId;产品ID
     * @param eparchyCode;地市编码
     * @param firstMonBillMode;首月付费模式
     * @param productMode;产品类型00主产品；01附加产品；04活动
     */
    private List<Map> queryProductInfoWithLimit(String provinceCode, String commodityId, String eparchyCode,
            String firstMonBillMode, String productMode) {
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        // 需要新增Limit表，新写SQL 暂用qryDefaultPackageElement
        Map param = new HashMap();
        param.put("PROVINCE_CODE", provinceCode);
        param.put("PRODUCT_ID", commodityId);
        param.put("EPARCHY_CODE", eparchyCode);
        param.put("FIRST_MON_BILL_MODE", firstMonBillMode);
        param.put("PRODUCT_MODE", productMode);
        return n25Dao.queryProductInfoWithLimit(param);
    }
}
