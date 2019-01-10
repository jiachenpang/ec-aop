package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;

/**
 * 用于处理宽带装通收费
 *
 * @auther zhousq
 * @create 2017_10_09_11:28
 */
@EcRocTag("N6brdOpenSubCommit")
public class N6brdOpenSubCommitProcessors extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.trade.n6.checkUserParametersMapping",
        "caop.masb.bccs.sProductChgParametersMapping",
        "ecaop.trades.tscs.ParametersMapping", "ecaop.masb.chph.gifa.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[5];
    LanUtils lan = new LanUtils();

    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        // 准备参数信息
        Map preDataMap = preDataForPreCommit(exchange, msg);
        MapUtils.arrayPut(preDataMap, msg, MagicNumber.COPYARRAY);
        Exchange presubExchange = ExchangeUtils.ofCopy(exchange, preDataMap);
        // 调预提交接口
        lan.preData(pmp[1], presubExchange);
        CallEngine.wsCall(presubExchange, "ecaop.comm.conf.url.OrdForNorthSer." + msg.get("province"));
        lan.xml2Json("ecaop.trades.bccs.template", presubExchange);

        // 调用正式提交
        Map preSubmitRetMap = presubExchange.getOut().getBody(Map.class);
        Map submitMap = Maps.newHashMap();
        MapUtils.arrayPut(submitMap, msg, MagicNumber.COPYARRAY);
        preOrderSubMessage(preSubmitRetMap, submitMap);
        Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMap);
        lan.preData(pmp[2], submitExchange);
        CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.OrderSub." + msg.get("province"));
        lan.xml2Json("ecaop.trades.mpsb.template", submitExchange);

        // 拼装返回参数
        Object provOrderId = ((ArrayList<Map>) preSubmitRetMap.get("rspInfo")).get(0).get("bssOrderId");
        Message out = new DefaultMessage();
        out.setBody(MapUtils.asMap("provOrderId", provOrderId));
        exchange.setOut(out);

    }

    private void preOrderSubMessage(Map preSubmitRetMap, Map submitMap) throws Exception {
        List<Map> rspInfo = (ArrayList<Map>) preSubmitRetMap.get("rspInfo");
        if (rspInfo == null || rspInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "统一预提交未返回应答信息");
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

    private Map preDataForPreCommit(Exchange exchange, Map msg) throws Exception {
        threepartCheck(ExchangeUtils.ofCopy(exchange, msg), msg);
        Map base = preBaseData(exchange, msg);
        Map ext = preExtData(msg);
        Map preDataMap = new HashMap();
        preDataMap.put("ordersId", msg.get("tradeId"));
        preDataMap.put("operTypeCode", "0");
        preDataMap.put("base", base);
        preDataMap.put("ext", ext);
        return preDataMap;
    }

    private void threepartCheck(Exchange exchange, Map msg) {
        msg.put("serialNumber", msg.get("serialNumber"));
        msg.put("tradeTypeCode", "9999");
        msg.put("getMode", "1111111111100013010000000100001");
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, msg);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], threePartExchange);
        try {
            CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.UsrForNorthSer." + msg.get("province"));
            lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "调用三户接口出错:" + e.getMessage());
        }

        Map result = threePartExchange.getOut().getBody(Map.class);
        List<Map> custInfoList = (List<Map>) result.get("custInfo");
        List<Map> userInfoList = (List<Map>) result.get("userInfo");
        List<Map> acctInfoList = (List<Map>) result.get("acctInfo");
        // 需要通过userInfo的返回,拼装老产品信息
        Map custInfo = custInfoList.get(0);
        Map userInfo = userInfoList.get(0);
        Map acctInfo = acctInfoList.get(0);
        String eparchyCode = ChangeCodeUtils.changeCityCode(msg);
        String itemId = (String) GetSeqUtil.getSeqFromCb(pmp[3], ExchangeUtils.ofCopy(exchange, msg),
                "seq_item_id", 1).get(0);
        String tradeId = (String) GetSeqUtil.getSeqFromCb(pmp[3], ExchangeUtils.ofCopy(exchange, msg),
                "seq_trade_id", 1).get(0);
        List<Map> avtivityInfos = new ArrayList<Map>();
        for (Map product : (List<Map>) userInfo.get("productInfo")) {
            if ("50".equals(product.get("productMode"))) {
                avtivityInfos.add(product);
            }
        }
        msg.putAll(MapUtils.asMap("custInfo", custInfo, "userInfo", userInfo, "acctInfo", acctInfo, "userId",
                userInfo.get("userId"),
                "custId", custInfo.get("custId"), "acctId", acctInfo.get("acctId"), "custName",
                custInfo.get("custName"), "brandCode",
                userInfo.get("brandCode"),
                "productId", userInfo.get("productId"), "productName",
                userInfo.get("productName"),
                "productTypeCode",
                userInfo.get("productTypeCode"), "itemId", itemId, "eparchyCode", eparchyCode,
                "tradeId", tradeId, "startDate", GetDateUtils.getDate(), "userDiffCode",
                userInfo.get("userDiffCode"),
                "contact", custInfo.get("contact"), "contactPhone", custInfo.get("contactPhone"), "avtivityInfos",
                avtivityInfos, "operTypeCode", "0"));

    }

    private Map preBaseData(Exchange exchange, Map msg) {
        String date = (String) msg.get("startDate");
        Map base = new HashMap();
        base.put("subscribeId", msg.get("tradeId"));
        base.put("tradeId", msg.get("tradeId"));
        base.put("tradeTypeCode", "0339");
        base.put("nextDealTag", "Y");
        base.put("olcomTag", "0");
        base.put("foregift", "0");
        base.put("execTime", date);
        base.put("acceptDate", date);
        base.put("chkTag", "0");
        base.put("operFee", "0");
        base.put("cancelTag", "0");
        base.put("netTypeCode", "0040");
        base.put("advancePay", "0");
        String inModeCode = (String) new ChangeCodeUtils().getInModeCode(exchange.getAppCode());
        base.put("inModeCode", inModeCode);
        base.put("custId", msg.get("custId"));
        base.put("custName", msg.get("custName"));
        base.put("acctId", msg.get("acctId"));
        base.put("serinalNamber", msg.get("serialNumber"));
        base.put("productId", msg.get("productId"));
        base.put("userDiffCode", "00");
        base.put("brandCode", msg.get("brandCode"));
        base.put("usecustId", msg.get("custId"));
        base.put("userId", msg.get("userId"));
        base.put("cityCode", msg.get("district"));
        return base;
    }

    private Map preExtData(Map msg) throws Exception {
        Map ext = new HashMap();
        ext.put("tradeItem", preDataForTradeItem(msg));
        return ext;
    }

    private Map preDataForTradeItem(Map msg) {
        List<Map> itemList = new ArrayList<Map>();
        Map tradeItem = new HashMap();
        itemList.add(LanUtils.createTradeItem("OLD_TRADE_ID", msg.get("oldtradeId")));
        itemList.add(LanUtils.createTradeItem("OLDFEE", msg.get("oldFee")));
        itemList.add(LanUtils.createTradeItem("SEND_TYPE", msg.get("sendType")));
        tradeItem.put("item", itemList);
        return tradeItem;
    }

    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
