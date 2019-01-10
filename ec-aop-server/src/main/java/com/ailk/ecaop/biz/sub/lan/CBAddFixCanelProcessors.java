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
import org.n3r.ecaop.dao.base.DaoEngine;
import org.n3r.esql.Esql;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.alibaba.fastjson.JSON;

/**
 * 固网同装撤单
 */
@EcRocTag("cbaddfixcanncel")
public class CBAddFixCanelProcessors extends BaseAopProcessor {

    LanUtils lan = new LanUtils();
    String tradeId;
    LanOpenApp4GDao dao = new LanOpenApp4GDao();

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        // msg
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) exchange.getIn().getBody(Map.class).get("msg"));
        }
        else {
            msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        }
        body.put("msg", msg);
        exchange.getIn().setBody(body);

        tradeId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, msg), "seq_trade_id");
        msg.put("ordersId", tradeId);

        // 需要固话号码必传，根据固话号码查询三户信息
        /*
         * msg.put("SERIALNUMBER", msg.get("phoneNumber"));
         * List resultSet = dao.queryThreePartInfo(msg);
         * if (resultSet != null && resultSet.size() > 0) {
         * Map outMap = (Map) resultSet.get(0);
         * msg.put("userId", outMap.get("USER_ID"));
         * msg.put("acctId", outMap.get("ACCT_ID"));
         * msg.put("custId", outMap.get("CUST_ID"));
         * }
         * else {
         * throw new EcAopServerBizException("9999", "无此固话号码相关三户编码信息，请检查是否是同装业务固话号码");
         * }
         */
        msg.put("userId", "-1");
        msg.put("acctId", "-1");
        msg.put("custId", "-1");

        msg.put("operTypeCode", "2");
        preData(msg);// 转换地市编码
        preBaseData(msg, exchange.getAppCode());// 准备base节点
        Map BigExt = new HashMap();
        BigExt.put("tradeItem", preTradeItem(msg, exchange.getAppCode()));
        BigExt.put("tradeOther", preTradeOtherData(msg));
        msg.put("ext", BigExt);

        // 预提交
        lan.preData("ecaop.trades.scmc.cancelPre.paramtersmapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.scmc.cancelPre.template", exchange);

        Map retMap = exchange.getOut().getBody(Map.class);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (null == rspInfoList || 0 == rspInfoList.size()) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }

        // 这两个信息要最终返回给接入系统，如不在此设置，可能会被后面的返回覆盖
        orderSubPreData(rspInfoList, msg);
        body.put("msg", msg);
        exchange.getIn().setBody(body);

        // 正式提交
        lan.preData("ecaop.trades.scmc.cancel.paramtersmapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        lan.xml2Json("ecaop.trades.scmc.cancel.template", exchange);
        Map out = exchange.getOut().getBody(Map.class);
        out.put("OK", "撤单成功");
    }

    private Map preTradeOtherData(Map msg) {
        HashMap brdItem = new HashMap();
        HashMap lineItem = new HashMap();
        HashMap tradeOther = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        // 固话信息
        brdItem.put("xDatatype", "NULL");
        brdItem.put("rsrvValue", msg.get("oldPhoProvOrderId"));
        brdItem.put("rsrvStr1", "track");
        brdItem.put("rsrvStr2", "c");
        brdItem.put("modifyTag", "0");
        brdItem.put("rsrvStr3", null != msg.get("cancelReason") ? msg.get("cancelReason").toString() : "撤单");
        brdItem.put("rsrvStr4", "30");
        brdItem.put("rsrvValueCode", "CLOR");
        brdItem.put("rsrvStr6", msg.get("areaCode"));
        brdItem.put("rsrvStr5", "3");
        brdItem.put("rsrvStr7", "DECIDE_IOM");
        lineItem.put("rsrvStr8", "true");
        lineItem.put("rsrvStr9", msg.get("oldPhoProvOrderId"));
        brdItem.put("rsrvStr12", "1");
        brdItem.put("rsrvStr11", "Y");
        lineItem.put("rsrvStr13", msg.get("phoneNumber"));
        brdItem.put("rsrvStr14", "0");
        brdItem.put("rsrvStr15", msg.get("areaCode"));
        itemList.add(brdItem);
        // 宽带信息
        lineItem.put("xDatatype", "NULL");
        lineItem.put("rsrvValue", msg.get("oldProvOrderId"));
        lineItem.put("rsrvStr1", "track");
        lineItem.put("rsrvStr2", "c");
        lineItem.put("modifyTag", "0");
        lineItem.put("rsrvStr3", null == msg.get("cancelReason") ? "撤单" : msg.get("cancelReason").toString());
        lineItem.put("rsrvStr4", "40");
        lineItem.put("rsrvValueCode", "CLOR");
        lineItem.put("rsrvStr6", msg.get("areaCode"));
        lineItem.put("rsrvStr5", "3");
        lineItem.put("rsrvStr7", "DECIDE_IOM");
        lineItem.put("rsrvStr8", "true");
        lineItem.put("rsrvStr9", msg.get("oldProvOrderId"));
        lineItem.put("rsrvStr13", msg.get("brdNumber"));
        lineItem.put("rsrvStr12", "1");
        lineItem.put("rsrvStr11", "Y");
        lineItem.put("rsrvStr14", "0");
        lineItem.put("rsrvStr15", msg.get("areaCode"));
        itemList.add(lineItem);
        tradeOther.put("item", itemList);
        return tradeOther;
    }

    private void preData(Map msg) {
        try {
            Esql dao = DaoEngine.getMySqlDao("/com/ailk/ecaop/sql/cbss/CbssAreaChangeQuery.esql");
            List result = dao.id("selAreaCode").params(msg).execute();
            if (null == result || 0 == result.size()) {
                throw new EcAopServerBizException("9999", "地市信息转换失败");
            }
            msg.put("areaCode", result.get(0));
        }
        catch (Exception e) {
            msg.put("areaCode", msg.get("city"));
        }
    }

    /**
     * 正式提交参数准备
     * 
     * @param rspInfo
     * @param preDateMap
     */
    private void orderSubPreData(List<Map> rspInfo, Map preDateMap) {
        if (null == rspInfo || rspInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "cBSS统一预提交未返回应答信息");
        }
        String orderNo = String.valueOf(rspInfo.get(0).get("provOrderId"));
        String provOrderId = String.valueOf(rspInfo.get(0).get("bssOrderId"));
        Integer totalFee = 0;
        List<Map> subOrderSubReqList = new ArrayList<Map>();
        // List<Map> payInfoList = new ArrayList<Map>();
        for (Map rsp : rspInfo) {
            List<Map> provinceOrderInfo = (List<Map>) rsp.get("provinceOrderInfo");
            if (null == provinceOrderInfo || 0 == provinceOrderInfo.size()) {
                continue;
            }
            for (Map preovinceOrder : provinceOrderInfo) {
                totalFee = totalFee + Integer.valueOf(preovinceOrder.get("totalFee").toString());
                String subProvinceOrderId = String.valueOf(preovinceOrder.get("subProvinceOrderId"));
                String subOrderId = String.valueOf(preovinceOrder.get("subOrderId"));
                List<Map> preFeeInfoRsp = (List<Map>) preovinceOrder.get("preFeeInfoRsp");
                List<Map> feeList = dealFee(preFeeInfoRsp);
                Map subOrderMap = MapUtils.asMap("subProvinceOrderId", subProvinceOrderId, "subOrderId", subOrderId,
                        "feeInfo", feeList);
                subOrderSubReqList.add(subOrderMap);
            }
        }
        List<Map> feeInfoList = (List<Map>) preDateMap.get("feeInfo");
        if (null != feeInfoList && feeInfoList.size() > 0) {
            List<Map> feeList = new ArrayList<Map>();
            for (Map feeMap : feeInfoList) {
                totalFee = totalFee + Integer.valueOf(feeMap.get("totalFee").toString());
                feeList.add(dealRequestFee(feeMap));
                Map subOrderMap = MapUtils.asMap("subProvinceOrderId", provOrderId, "subOrderId", orderNo,
                        "feeInfo", feeList);
                subOrderSubReqList.add(subOrderMap);
            }
        }

        // 正式提交支付信息节点
        /*
         * Map payInfoMap = new HashMap();
         * payInfoMap.put("payMoney", totalFee);
         * payInfoMap.put("payType", "10");
         * payInfoMap.put("subProvinceOrderId", provOrderId);
         * payInfoList.add(payInfoMap);
         */
        preDateMap.put("provOrderId", provOrderId);// 预提交返回订单
        preDateMap.put("orderNo", orderNo);// 外围系统订单
        preDateMap.put("origTotalFee", totalFee);
        preDateMap.put("subOrderSubReq", subOrderSubReqList);
        // preDateMap.put("payInfo", payInfoList);

    }

    /**
     * 处理请求传入的费用项
     * 
     * @param feeList
     * @return
     */
    private Map dealRequestFee(Map fee) {
        fee.put("isPay", "1");
        fee.put("operateType", "1");
        fee.put("calculateTag", "N");
        return fee;
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
            retFee.put("origFee", fee.get("fee"));
            retFeeList.add(retFee);
        }
        return retFeeList;
    }

    private void preBaseData(Map msg, String appCode) {

        // ext
        Map base = new HashMap();
        base.put("tradeId", tradeId);
        base.put("subscribeId", msg.get("provOrderId"));
        base.put("acceptDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("nextDealTag", "Z");// 后续处理状态
        base.put("olcomTag", "0");// 指令标志
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));// 接入方式
        base.put("tradeTypeCode", "0615");// 业务类型编码
        base.put("userId", msg.get("userId"));
        base.put("custId", msg.get("custId"));
        base.put("usecustId", msg.get("custId"));
        base.put("acctId", msg.get("acctId"));
        base.put("userDiffCode", "-1");
        base.put("netTypeCode", "00CP");// 网别
        base.put("serinalNamber", "-1");
        base.put("custName", "-1");
        base.put("termIp", "10.124.0.11");// 受理终端IP地址
        base.put("eparchyCode", msg.get("areaCode"));
        base.put("cityCode", msg.get("district"));
        base.put("execTime", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("operFee", "0");// 营业费用
        base.put("foregift", "0");// 押金金额
        base.put("advancePay", "0");// 预付话费
        base.put("feeState", "");// 收费标志：0-未收费，1-已收费
        base.put("feeStaffId", "");
        base.put("cancelTag", "0");
        base.put("checktypeCode", "");
        base.put("chkTag", "0");// <!--审核标志：0-未审核，1-审核通过，2-审核未通过。 -->
        base.put("contact", "");
        base.put("contactPhone", "");
        base.put("contactAddress", "");
        msg.put("base", base);

    }

    private Map preTradeItem(Map msg, String appCode) {
        List<Map> item = new ArrayList<Map>();
        Map tradeItem = new HashMap();
        if (new ChangeCodeUtils().isWOPre(appCode)) {
            item.add(LanUtils.createTradeItem("WORK_TRADE_ID", msg.get("provOrderId")));
        }
        item.add(LanUtils.createTradeItem("IS_ORDER_TRACK", tradeId));
        item.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "1010300"));
        item.add(LanUtils.createTradeItem("CANCELTRACK_REASON", ""));
        tradeItem.put("item", item);
        return tradeItem;
    }

    private Map preOrder(Map msg, Map rspInfo) {
        msg.put("orderNo", rspInfo.get("provOrderId"));
        msg.put("provOrderId", rspInfo.get("bssOrderId"));
        Map subOrderSub = new HashMap();
        List<Map> subOrderSubReq = new ArrayList<Map>();
        subOrderSub.put("subProvinceOrderId", rspInfo.get("provOrderId"));
        subOrderSub.put("subOrderId", rspInfo.get("orderNo"));
        subOrderSubReq.add(subOrderSub);
        msg.put("subOrderSubReq", subOrderSubReq);
        return msg;
    }
}
