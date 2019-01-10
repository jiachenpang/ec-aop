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
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.google.common.collect.Maps;

/**
 * 微信托收签约预提交
 * 
 * @author zhaok 20170721
 */
@EcRocTag("curtWechatdeductApp")
public class CurtWechatdeductAppProcessor extends BaseAopProcessor implements ParamsAppliable {

    LanUtils lan = new LanUtils();
    private static final String[] PARAM_ARRAY = {"ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.masb.chph.gifa.ParametersMapping", "ecaop.trades.sccc.cancelPre.paramtersmapping",
            "ecaop.trades.sccc.cancel.paramtersmapping"};
    // 查询三户， 生成tradeId， 预提交 ，正式提交
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        // 查询三户信息
        threepartCheck(exchange, msg);
        // 处理活动操作类型 签约|解约
        String operType = (String) msg.get("operType");

        if (!"0|1".contains(operType)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "请传入正确签约业务类型！");
        }

        String systemDate = GetDateUtils.getDate();
        msg.put("updateDate", systemDate);
        msg.put("systemDate", systemDate.substring(0, 6));

        // 0 签约
        if ("0".equals(operType)) {
            String agreementNo = (String) msg.get("agreementNo");
            if (IsEmptyUtils.isEmpty(agreementNo) || "null".equals(agreementNo)) {
                throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "办理签约业务时签约协议号！");
            }

            // 封装base节点
            preContractBaseData(exchange, msg);
            // 封装ext节点
            Map ext = new HashMap();
            preExtTaData(msg, ext);
            preExtTacData(msg, ext);
            msg.put("ext", ext);
        }
        // 1 解约
        else if ("1".equals(operType)) {
            // 封装base节点
            preChargeBaseData(msg);
            preChargeExtTaData(msg);
        }

        // 调预提交接口
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        // 把msg里面的信息copy一份
        Exchange tempExchange = ExchangeUtils.ofCopy(exchange, msg);
        lan.preData(pmp[2], tempExchange);
        CallEngine.wsCall(tempExchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.masb.sbac.sglUniTradeTemplate", tempExchange);
        // 处理返回信息
        Map recvMap = tempExchange.getOut().getBody(Map.class);

        // 正式提交
        preOrderSubMessage(exchange, recvMap, msg);

        // dealReturn(exchange);

    }

    private void preChargeExtTaData(Map msg) {

        Map ext = new HashMap();
        Map tradeAcct = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();

        item.put("xDatatype", "NULL");
        item.put("acctId", msg.get("acctId"));
        item.put("payName", msg.get("payName"));
        item.put("payModeCode", "0");
        item.put("debutyUserId", msg.get("userId"));
        item.put("debutyCode", msg.get("serialNumber"));
        item.put("contractNo", "");
        item.put("removeTag", "0");
        item.put("acctPasswd", "");

        itemList.add(item);
        tradeAcct.put("item", itemList);
        ext.put("tradeAcct", tradeAcct);
        msg.put("ext", ext);
    }

    /**
     * 解约流程
     * 
     * @param exchange
     * @param msg
     */
    private void preChargeBaseData(Map msg) {

        try {
            // 获取系统时间
            String systemDate = (String) msg.get("systemDate");

            Map base = new HashMap();

            base.put("subscribeId", msg.get("tradeId"));
            base.put("tradeId", msg.get("tradeId"));
            base.put("acceptDate", msg.get("updateDate"));
            base.put("nextDealTag", "Z");
            base.put("olcomTag", "0");
            base.put("inModeCode", "E");
            // 微信托收解约业务类型编码 0822
            base.put("tradeTypeCode", "0822");
            base.put("productId", "99999830");
            base.put("brandCode", "4G00");
            base.put("userId", msg.get("userId"));
            base.put("custId", msg.get("custId"));
            base.put("usecustId", msg.get("usecustId"));
            base.put("acctId", msg.get("acctId"));
            base.put("userDiffCode", "00");
            base.put("netTypeCode", "0050");
            base.put("serinalNamber", msg.get("serialNumber"));
            base.put("custName", msg.get("custName"));
            base.put("termIp", "10.124.0.48");
            base.put("eparchyCode", msg.get("eparchyCode"));
            base.put("cityCode", msg.get("district"));
            base.put("execTime", msg.get("updateDate"));
            base.put("operFee", "0");
            base.put("foregift", "0");
            base.put("advancePay", "0");
            base.put("chkTag", "0");
            base.put("feeState", "");
            base.put("feeStaffId", "");
            base.put("checktypeCode", "");
            base.put("actorName", "");
            base.put("actorCertTypeId", "");
            base.put("actorPhone", "");
            base.put("actorCertNum", "");
            base.put("contact", "");
            base.put("contactAddress", "");
            base.put("remark", "");

            msg.put("base", base);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装base节点报错");
        }
    }

    /**
     * 拼装正式提交参数
     * 
     * @param exchange
     * @param retMap
     * @param msg
     */
    private void preOrderSubMessage(Exchange exchange, Map recvMap, Map msg) {

        Map retMap = new HashMap();
        Map submitMsg = Maps.newHashMap();
        // 把msg的内容放入submitMsg
        MapUtils.arrayPut(submitMsg, msg, MagicNumber.COPYARRAY);

        List<Map> subOrderSubReq = new ArrayList<Map>();

        if (IsEmptyUtils.isEmpty(recvMap.get("rspInfo"))
                || IsEmptyUtils.isEmpty(((List<Map>) recvMap.get("rspInfo")).get(0))) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "调用cbss预提交未返回信息");
        }
        List<Map> rspInfo = ((List<Map>) recvMap.get("rspInfo"));
        // 省分订单ID
        String provOrderId = (String) rspInfo.get(0).get("bssOrderId");
        // 总部订单ID
        String orderNo = (String) rspInfo.get(0).get("provOrderId");
        retMap.put("subProvinceOrderId", provOrderId);
        retMap.put("subOrderId", orderNo);
        subOrderSubReq.add(retMap);

        submitMsg.put("provOrderId", provOrderId);
        submitMsg.put("orderNo", IsEmptyUtils.isEmpty(orderNo) ? provOrderId : orderNo);
        submitMsg.put("operationType", "01");
        submitMsg.put("sendTypeCode", "0");
        submitMsg.put("noteNo", "11111111111111");
        submitMsg.put("noteType", "1");
        submitMsg.put("origTotalFee", "0");
        submitMsg.put("cancleTotalFee", "0");
        submitMsg.put("subOrderSubReq", subOrderSubReq);

        Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMsg);
        // lan.preData(pmp[0], exchange);
        // CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        // lan.xml2Json("ecaop.masb.odsb.template", exchange);

        try {
            lan.preData(pmp[3], submitExchange);
            CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.cbss.services.orderSub");
            lan.xml2Json("ecaop.masb.odsb.template", submitExchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "CBSS正式提交返回:" + e.getMessage());
        }
        // 处理正式提交的返回,取预提交返回的订单号返回
        Message out = new DefaultMessage();
        out.setBody(MapUtils.asMap("orderId", orderNo, "provOrderId", provOrderId));
        exchange.setOut(out);
    }

    private void preExtTaData(Map msg, Map ext) {
        // TradeManagerUtils.preActivityInfo(msg);

        Map tradeAcct = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();

        item.put("xDatatype", "NULL");
        item.put("acctId", msg.get("acctId"));
        item.put("payName", msg.get("payName"));
        item.put("payModeCode", "L");
        item.put("debutyUserId", msg.get("userId"));
        item.put("debutyCode", msg.get("serialNumber"));
        item.put("contractNo", "");
        item.put("removeTag", "0");
        item.put("acctPasswd", "");

        itemList.add(item);
        tradeAcct.put("item", itemList);
        ext.put("tradeAcct", tradeAcct);
    }

    private void preExtTacData(Map msg, Map ext) {

        Map tradeAcctConsign = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();

        item.put("xDatatype", "NULL");
        item.put("eparchyCode", msg.get("eparchyCode"));
        item.put("acctId", msg.get("acctId"));
        item.put("payModeCode", "L");
        item.put("consignMode", "0");
        item.put("assistantTag", "0");
        // 2017-09-08 根据需求修改 by zhaok
        item.put("superBankCode", "WX");
        item.put("bankCode", "WX");
        item.put("bankAcctNo", msg.get("agreementNo"));
        item.put("agreementNo", msg.get("agreementNo"));
        item.put("paymentId", "1000044");
        item.put("payFeeModeCode", "3");
        item.put("actTag", "1");
        item.put("startCycleId", msg.get("systemDate"));
        item.put("endCycleId", "205012");
        item.put("updateTime", msg.get("updateDate"));
        item.put("bankAcctName", msg.get("custName"));

        itemList.add(item);
        tradeAcctConsign.put("item", itemList);
        ext.put("tradeAcctConsign", tradeAcctConsign);
    }

    /**
     * 从三户获取用户的三户标识和使用用户标识、客户名称
     * 
     * @param exchange
     * @param msg
     */
    private void threepartCheck(Exchange exchange, Map msg) {
        msg.put("serialNumber", msg.get("serialNumber"));
        msg.put("tradeTypeCode", "9999");
        msg.put("serviceClassCode", "0000");
        msg.put("getMode", "1111111111100013010000000100001");
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, msg);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], threePartExchange);
        try {
            CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "调用cbss三户接口出错:" + e.getMessage());
        }
        // 获取userId、usecustId、custId、acctId、custName
        Map result = threePartExchange.getOut().getBody(Map.class);
        if (IsEmptyUtils.isEmpty(result)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "根据用户号码:[" + msg.get("serialNumber")
                    + "]未获取到三户信息");
        }
        List<Map> custInfoList = (List<Map>) result.get("custInfo");
        List<Map> userInfoList = (List<Map>) result.get("userInfo");
        List<Map> acctInfoList = (List<Map>) result.get("acctInfo");
        // 获取三户内容
        Map custInfo = custInfoList.get(0);
        Map userInfo = userInfoList.get(0);
        Map acctInfo = acctInfoList.get(0);

        String userId = (String) userInfo.get("userId");
        String productId = (String) userInfo.get("productId");
        String brandCode = (String) userInfo.get("brandCode");
        String usecustId = (String) userInfo.get("usecustId");
        String acctId = (String) acctInfo.get("acctId");
        String payName = (String) acctInfo.get("payName");
        String custId = (String) custInfo.get("custId");
        String custName = (String) custInfo.get("custName");
        String eparchyCode = ChangeCodeUtils.changeCityCode(msg);

        // List<Map> consignList = (List<Map>) acctInfo.get("consign");
        // String agreementNo = (String) consignList.get(0).get("agreementNo"); （不需要从账户信息里获取）

        String tradeId = (String) GetSeqUtil.getSeqFromCb(pmp[1], ExchangeUtils.ofCopy(exchange, msg), "seq_trade_id",
                1).get(0);

        msg.putAll(MapUtils.asMap("userId", userId, "usecustId", usecustId, "acctId", acctId, "custId",
                custId, "custName", custName, "eparchyCode", eparchyCode, "tradeId", tradeId,
                "ordersId", tradeId, "operTypeCode", "0", "productId", productId,
                "brandCode", brandCode, "payName", payName));

    }

    private void preContractBaseData(Exchange exchange, Map msg) {
        try {

            Map base = new HashMap();
            base.put("subscribeId", msg.get("tradeId"));
            base.put("tradeId", msg.get("tradeId"));
            base.put("acceptDate", msg.get("updateDate"));
            base.put("nextDealTag", "Z");
            base.put("olcomTag", "0");
            base.put("inModeCode", "E");
            base.put("tradeTypeCode", "0821");
            base.put("productId", msg.get("productId"));
            base.put("brandCode", msg.get("brandCode"));
            base.put("userId", msg.get("userId"));
            base.put("custId", msg.get("custId"));
            base.put("usecustId", msg.get("usecustId"));
            base.put("acctId", msg.get("acctId"));
            base.put("userDiffCode", "00");
            base.put("netTypeCode", "0050");
            base.put("serinalNamber", msg.get("serialNumber"));
            base.put("custName", msg.get("custName"));
            base.put("termIp", "10.124.0.48");
            base.put("eparchyCode", msg.get("eparchyCode"));
            base.put("cityCode", msg.get("district"));
            base.put("execTime", msg.get("updateDate"));
            base.put("operFee", "0");
            base.put("foregift", "0");
            base.put("advancePay", "0");
            base.put("chkTag", "0");
            base.put("feeState", "");
            base.put("feeStaffId", "");
            base.put("checktypeCode", "");
            base.put("actorName", "");
            base.put("actorCertTypeId", "");
            base.put("actorPhone", "");
            base.put("actorCertNum", "");
            base.put("contact", "");
            base.put("contactAddress", "");
            base.put("remark", "");

            msg.put("base", base);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装base节点报错");
        }
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[]{PARAM_ARRAY[i]});
        }
    }

}
