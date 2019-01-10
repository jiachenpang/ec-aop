package com.ailk.ecaop.biz.brd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

@EcRocTag("brdYearlyPayCheck4G")
public class BrdYearlyPayCheck4GProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        // 生成tradeId号
        String tradeId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_trade_id");
        // 生成ItemId号
        String ItemId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_item_id");

        LanUtils lan = new LanUtils();
        msg.putAll(preCheckData(msg));
        Exchange checkUsrExchange = ExchangeUtils.ofCopy(exchange, msg);

        // 调用三户接口
        lan.preData("ecaop.trade.cbss.checkUserParametersMapping", checkUsrExchange);
        CallEngine.wsCall(checkUsrExchange,
                "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.threePart.template", checkUsrExchange);
        Map threepartMap = new HashMap();
        threepartMap.putAll(checkUsrExchange.getOut().getBody(Map.class));
        List<Map> threeList = new ArrayList<Map>();
        List<Map> userInfoList = (ArrayList<Map>) threepartMap.get("userInfo");
        if (null == userInfoList || 0 == userInfoList.size()) {
            throw new EcAopServerBizException("9999", "三户信息校验接口:核心系统未返回用户信息");
        }
        List<Map> custInfoList = (ArrayList<Map>) threepartMap.get("custInfo");
        if (null == custInfoList || 0 == custInfoList.size()) {
            throw new EcAopServerBizException("9999", "三户信息校验接口:核心系统未返回客户信息");
        }
        List<Map> acctInfoList = (ArrayList<Map>) threepartMap.get("acctInfo");
        if (null == acctInfoList || 0 == acctInfoList.size()) {
            throw new EcAopServerBizException("9999", "三户信息校验接口:核心系统未返回账户信息");
        }

        // 调用预提交
        // msg.put("operTypeCode", "0");
        // msg.put("ordersId", msg.get("orderNo"));
        // msg.put("tradeId", tradeId);
        // msg.put("base", preSubBaseData(msg, userInfoList, custInfoList, acctInfoList));
        // msg.put("ext", preSubExtData(msg, custInfoList, ItemId));
        // Exchange preExchange = ExchangeUtils.ofCopy(exchange, msg);
        // lan.preData("ecaop.trades.sccc.cancelPre.paramtersmapping", preExchange);
        // CallEngine.wsCall(preExchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        // lan.xml2Json("ecaop.trades.sccc.cancelPre.template", preExchange);
        // Map retMapSub = new HashMap();
        // retMapSub.putAll(preExchange.getOut().getBody(Map.class));
        // List<Map> rspInfoListSub = (ArrayList<Map>) retMapSub.get("rspInfo");
        // if (null == rspInfoListSub || 0 == rspInfoListSub.size()) {
        // throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE,
        // "核心系统未返回订单信息.");
        // }
        // 调用欠费信息查询
        msg.put("queryType", "2");
        // msg.put("queryMode", "3");
        msg.put("chargeType", "0");
        msg.put("badDebtTag", "0");
        msg.put("acctId", acctInfoList.get(0).get("acctId"));
        // if (null == msg.get("areaCode")) {
        // msg.put("areaCode", "");
        // }
        // msg.put("serialNumber", msg.get("areaCode") + msg.get("serialNumber").toString());

        Exchange oweExchange = ExchangeUtils.ofCopy(exchange, msg);
        lan.preData("ecaop.core.brd.qryOweFeeInfoParametersMapping", oweExchange);
        CallEngine.wsCall(oweExchange, "ecaop.comm.conf.url.cbss.services.PaymentSer");
        lan.xml2JsonNoError("ecaop.core.brd.qryOweFeeInfoTemplate", oweExchange);
        Map oweFeeRsp = new HashMap();
        oweFeeRsp.putAll(oweExchange.getOut().getBody(Map.class));
        // 返回结果给上游
        // exchange.getOut().setBody(preBackData(retMapSub, custInfoList, userInfoList, oweFeeRsp));
    }

    private Map preCheckData(Map msg) {
        msg.put("tradeTypeCode", "0340");
        msg.put("getMode", "101001000000000000000000000000");
        Map<String, Object> cardNumberInfo = new HashMap();
        cardNumberInfo.put("serialNumber", msg.get("serialNumber"));
        msg.put("cardNumberInfo", cardNumberInfo);
        return msg;
    }

    private Map preSubBaseData(Map msg, List<Map> userInfoList, List<Map> custInfoList, List<Map> acctInfoList) {
        Map base = new HashMap();

        base.put("subscribeId", msg.get("tradeId"));
        base.put("tradeId", msg.get("tradeId"));
        base.put("acceptDate", GetDateUtils.getDate());
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("inModeCode", msg.get("inModeCode"));
        base.put("tradeTypeCode", "0127");
        base.put("productId", userInfoList.get(0).get("productId"));
        base.put("brandCode", userInfoList.get(0).get("brandCode"));
        base.put("userId", userInfoList.get(0).get("userId"));
        base.put("custId", custInfoList.get(0).get("custId"));
        base.put("usecustId", userInfoList.get(0).get("usecustId"));
        base.put("acctId", acctInfoList.get(0).get("acctId"));
        base.put("userDiffCode", userInfoList.get(0).get("userDiffCode"));
        base.put("netTypeCode", "0040");
        base.put("serinalNamber", msg.get("serialNumber"));
        base.put("custName", custInfoList.get(0).get("custName"));
        base.put("termIp", "10.124.0.11");
        base.put("eparchyCode", ChangeCodeUtils.changeEparchy(msg));
        base.put("cityCode", msg.get("district"));
        base.put("execTime", GetDateUtils.getDate());
        base.put("operFee", "0");
        base.put("foregift", "0");
        base.put("advancePay", "0");
        base.put("feeState", "");
        base.put("feeStaffId", "");
        base.put("cancelTag", "0");
        base.put("checktypeCode", "8");
        base.put("chkTag", "0");
        base.put("actorName", "");
        base.put("actorCertTypeId", "");
        base.put("actorPhone", "");
        base.put("actorCertNum", "");
        base.put("contact", custInfoList.get(0).get("contact"));
        base.put("contactPhone", custInfoList.get(0).get("contactPhone"));
        base.put("contactAddress", "");
        base.put("remark", "");

        return base;
    }

    private Map preSubExtData(Map msg, List<Map> custInfoList, String ItemId) {
        Map ext = new HashMap();
        Map trade = new HashMap();
        Map tradeRes = new HashMap();
        List<Map> tradeitem = new ArrayList<Map>();
        List<Map> tradeResitem = new ArrayList<Map>();

        // tradeItem
        Map temp = new HashMap();
        temp.put("attrCode", "STANDARD_KIND_CODE");
        temp.put("attrValue", ChangeCodeUtils.changeEparchy(msg));
        temp.put("xDatatype", "null");
        tradeitem.add(temp);
        trade.put("item", tradeitem);
        ext.put("tradeItem", trade);

        tradeResitem.add(createTradeSubItemWithTime("expireDealMode", "a", ItemId));
        tradeResitem.add(createTradeSubItemWithTime("bDiscntCode", "7113900", ItemId));
        tradeResitem.add(createTradeSubItemWithTime("callBack", "0", ItemId));
        tradeResitem.add(createTradeSubItemNoTime("linkName", custInfoList.get(0).get("contact"), ItemId));
        tradeResitem.add(createTradeSubItemNoTime("otherContact", "", ItemId));
        tradeResitem.add(createTradeSubItemNoTime("linkPhone", custInfoList.get(0).get("contactPhone"), ItemId));
        tradeRes.put("item", tradeResitem);
        ext.put("tradeSubItem", tradeRes);
        return ext;
    }

    private Map createTradeSubItemWithTime(String key, Object value, String itemId) {
        return MapUtils.asMap("attrTypeCode", "0", "attrCode", key, "attrValue",
                value, "itemId", itemId, "startDate", GetDateUtils.getDate(), "endDate", "20501231235959");
    }

    private Map createTradeSubItemNoTime(String key, Object value, String itemId) {
        return MapUtils.asMap("xDatatype", "NULL", "attrTypeCode", "0", "attrCode", key, "attrValue", value, "itemId",
                itemId);
    }

    private Map preBackData(Map retMapSub, List<Map> custInfoList, List<Map> userInfoList, Map oweFeeRsp) {
        Map rspInfoSub = ((ArrayList<Map>) retMapSub.get("rspInfo")).get(0);
        Map outMap = new HashMap();
        outMap.put("code", retMapSub.get("code"));
        outMap.put("detail", retMapSub.get("detail"));
        outMap.put("provOrderId", rspInfoSub.get("provOrderId"));
        outMap.put("custName", custInfoList.get(0).get("custName"));
        outMap.put("productType", userInfoList.get(0).get("productTypeCode"));
        outMap.put("productName", userInfoList.get(0).get("productName"));

        List<Map> discntInfoList = (List<Map>) userInfoList.get(0).get("discntInfo");
        String cycle = "";
        String startDate = "";
        String endDate = "";
        String discntName = "";
        if (discntInfoList != null && discntInfoList.size() != 0)
        {
            List<Map> attrInfoList = (List<Map>) discntInfoList.get(0).get("attrInfo");
            for (int j = 0; j < attrInfoList.size(); j++) {
                Map attrInfo = attrInfoList.get(j);
                if ("monthnum".equals(attrInfo.get("attrCode"))) {
                    cycle = (String) attrInfo.get("attrValue");
                }
            }
            startDate = (String) discntInfoList.get(0).get("startDate");
            endDate = (String) discntInfoList.get(0).get("endDate");
            discntName = (String) discntInfoList.get(0).get("discntCode");
        }
        outMap.put("discntName", discntName);
        outMap.put("startDate", startDate);
        outMap.put("endDate", endDate);
        outMap.put("cycle", cycle);
        List<Map> arrearageMessList = new ArrayList<Map>();
        List<Map> rspOweInfoList = ((ArrayList<Map>) oweFeeRsp.get("oweFeeInfo"));
        for (int i = 0; i < rspOweInfoList.size(); i++) {
            Map arrearageMessMap = new HashMap();
            Map rspOweInfo = rspOweInfoList.get(i);
            arrearageMessMap.put("arrearageNumber", rspOweInfo.get("serialNumber"));
            arrearageMessMap.put("areaCode", rspOweInfo.get("areaCode"));
            arrearageMessMap.put("arrearageType", "2");
            arrearageMessMap.put("arrearageUserName", rspOweInfo.get("payName"));
            arrearageMessList.add(arrearageMessMap);
        }
        outMap.put("arrearageMess", arrearageMessList);
        List<Map> provinceOrderInfoList = (ArrayList<Map>) rspInfoSub
                .get("provinceOrderInfo");
        Map provinceOrderInfo = provinceOrderInfoList.get(0);
        List<Map> preFeeInfoRspList = (ArrayList<Map>) provinceOrderInfo
                .get("preFeeInfoRsp");
        List<Map> feeInfoList = new ArrayList<Map>();
        for (int i = 0; i < preFeeInfoRspList.size(); i++) {
            Map feeInfo = new HashMap();
            Map preFeeInfoRsp = preFeeInfoRspList.get(i);
            feeInfo.put("operateType", preFeeInfoRsp.get("operateType"));
            feeInfo.put("feeMode", preFeeInfoRsp.get("feeMode"));
            feeInfo.put("feeTypeCode", preFeeInfoRsp.get("feeTypeCode"));
            feeInfo.put("feeTypeName", preFeeInfoRsp.get("feeTypeName"));
            feeInfo.put("maxDerateFee", preFeeInfoRsp.get("maxDerateFee"));
            feeInfo.put("fee", preFeeInfoRsp.get("fee"));
            feeInfoList.add(feeInfo);
        }
        outMap.put("provOrderId", rspInfoSub.get("provOrderId"));
        outMap.put("feeInfo", feeInfoList);
        outMap.put("totalFee", provinceOrderInfo.get("totalFee"));
        outMap.put("para", retMapSub.get("para"));
        return outMap;
    }
}
