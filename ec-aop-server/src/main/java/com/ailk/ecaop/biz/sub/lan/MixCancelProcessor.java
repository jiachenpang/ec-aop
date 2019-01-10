package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.extractor.TransIdFromRedisValueExtractor;
import org.n3r.ecaop.core.impl.DefaultMessage;
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
import com.alibaba.fastjson.JSON;

@EcRocTag("mixcancel")
public class MixCancelProcessor extends BaseAopProcessor {

    LanUtils lan = new LanUtils();
    String tradeId;

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        // msg
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) exchange.getIn().getBody(Map.class)
                    .get("msg"));
        }
        else {
            msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        }
        msg.put("tradeId", msg.get("soNbr"));

        body.put("msg", msg);
        exchange.getIn().setBody(body);
        // 撤单许可预判（暂时不调撤单校验）

        // lan.preData("ecaop.trade.scmc.isAllowCancelSoParametersMapping",
        // exchange);
        // CallEngine.wsCall(exchange,
        // "ecaop.comm.conf.url.cbss.services.orderChgandCleSer");
        // lan.xml2Json("ecaop.trade.scmc.isAllowCancelSoTemplate", exchange);
        //
        // Map outMap = exchange.getOut().getBody(Map.class);
        // /*
        // * code:0-请求处理成功,1-请求处理异常 code是0时，cancelFlag：Y-可以撤单，N-不允许撤单 modify by
        // * wangrj3
        // */
        // boolean codeFlag = null != outMap.get("code")
        // && "0".equals(outMap.get("code"));
        // boolean canCancelFlag = null != outMap.get("cancelFlag")
        // && "Y".equals(outMap.get("cancelFlag"));
        // if (codeFlag && !canCancelFlag) {// 请求处理成功&不允许撤单
        // throw new EcAopServerBizException("9999",
        // null == outMap.get("detail") ? outMap.get("detail")
        // .toString() : "撤单预判失败");
        // }
        // String cancelFlag = (String) outMap.get("cancelFlag");
        // if (StringUtils.isEmpty(cancelFlag) && "N".equals(cancelFlag)) {
        // throw new EcAopServerBizException("9999", "省份返回是否允许撤单标志为空");
        // }

        // 预提交
        Exchange exchange0 = ExchangeUtils.ofCopy(exchange, msg);
        tradeId = GetSeqUtil.getSeqFromCb(exchange0, "seq_trade_id");
        Exchange exchange1 = ExchangeUtils.ofCopy(exchange0, msg);
        msg.put("ordersId", msg.get("soNbr"));

        msg.put("operTypeCode", "2");
        preData(msg);// 转换地市编码
        preBaseData(msg, exchange.getAppCode());// 准备base节点
        Map BigExt = new HashMap();
        BigExt.put("tradeItem", preTradeItem(msg));
        Exchange exchange2 = ExchangeUtils.ofCopy(exchange1, msg);
        BigExt.put("tradeOther", preTradeOther(msg, tradeId, exchange2));
        msg.put("ext", BigExt);

        Exchange exchange3 = ExchangeUtils.ofCopy(exchange2, msg);

        lan.preData("ecaop.trades.scmc.cancelPre.paramtersmapping", exchange3);
        CallEngine.wsCall(exchange3,
                "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.scmc.cancelPre.template", exchange3);

        Map retMap = exchange3.getOut().getBody(Map.class);

        // TODO:

        // 77777777777
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (null == rspInfoList || 0 == rspInfoList.size()) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE,
                    "核心系统未返回订单信息.");
        }

        // 这两个信息要最终返回给接入系统，如不在此设置，可能会被后面的返回覆盖
        Map rspInfo = rspInfoList.get(0);
        Map orderMap = preOrderSubParam(rspInfo, msg);

        msg.putAll(orderMap);
        orderSubPreData(retMap, msg);
        msg = preOrder(msg, rspInfo);
        Exchange exchange4cBOrderSub = ExchangeUtils.ofCopy(exchange3, msg);
        // Map payInfo = new HashMap<String, Object>();
        // payInfo.put("payMoney", rspInfoList.get(3).get("fee"));
        // payInfo.put("payType", "10");
        // payInfo.put("subProvinceOrderId", msg.get("provOrderId"));

        body.put("msg", msg);
        exchange4cBOrderSub.getIn().setBody(body);

        // 正式提交
        lan.preData("ecaop.trades.scmc.cancel.paramtersmapping",
                exchange4cBOrderSub);
        CallEngine.wsCall(exchange4cBOrderSub,
                "ecaop.comm.conf.url.cbss.services.orderSub");
        lan.xml2Json("ecaop.trades.scmc.cancel.template", exchange4cBOrderSub);
        Map out = exchange4cBOrderSub.getOut().getBody(Map.class);
        // out.put("0000", "撤单成功");
        out.put("code", "0000");
        out.put("detail", "撤单成功");

        Message message = new DefaultMessage();
        message = exchange4cBOrderSub.getOut();
        message.setBody(out);
        exchange.setOut(message);
    }

    private Map preOrderSubParam(Map inMap, Map msg) {
        Map outMap = new HashMap();
        List<Map> provinceOrderInfo = (ArrayList<Map>) inMap
                .get("provinceOrderInfo");
        if (null != provinceOrderInfo && 0 != provinceOrderInfo.size()) {
            outMap.put("subOrderSubReq", dealSubOrder(provinceOrderInfo, msg));
        }
        outMap.put("origTotalFee", "0");
        outMap.put("operationType", "01");
        outMap.put("cancleTotalFee", provinceOrderInfo.get(0).get("totalFee"));
        return outMap;
    }

    private Map dealSubOrder(List<Map> provinceOrderInfo, Map msg) {
        Map retMap = new HashMap();
        for (Map tempMap : provinceOrderInfo) {
            retMap.put("subOrderId", tempMap.get("subOrderId"));
            retMap.put("subProvinceOrderId", tempMap.get("subProvinceOrderId"));

            List<Map> feeList = (ArrayList<Map>) tempMap.get("preFeeInfoRsp");
            List<Map> retFee = new ArrayList<Map>();
            if (null != feeList && 0 != feeList.size()) {
                for (Map fee : feeList) {
                    Map tempFee = dealFeeInfo(fee);
                    retFee.add(tempFee);
                }
                retMap.put("feeInfo", retFee);
                // 好像返回没有费用，不知道为什么要进行费用处理，将逻辑中if的外面放到里面来，否则会报错抛异常，而且个人认为这段判断没必要，但是先不删除了吧
                // modify by wangrj3
                if (null == retMap.get("feeInfo")) {
                    throw new EcAopServerBizException("9999", "没有费用信息");// 增加抛异常的提示
                                                                        // add
                                                                        // by
                                                                        // wangrj3
                }
            }
        }

        return retMap;
    }

    private Map dealFeeInfo(Map inputMap) {
        Map retMap = new HashMap();
        retMap.put("feeCategory", inputMap.get("feeMode"));
        retMap.put("feeId", inputMap.get("feeTypeCode"));
        retMap.put("feeDes", inputMap.get("feeTypeName"));
        retMap.put("operateType", "2");
        retMap.put("origFee", inputMap.get("oldFee"));
        retMap.put("reliefFee", "0");
        retMap.put("realFee", inputMap.get("oldFee"));
        return retMap;
    }

    private Map preTradeOtherData(Map msg) {
        HashMap item = new HashMap();
        HashMap tradeOther = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("rsrvValue", msg.get("soNbr"));
        item.put("rsrvStr1", "track");
        item.put("modifyTag", "0");
        item.put("rsrvStr3",
                null == msg.get("cancelReason") ? msg.get("cancelReason")
                        .toString() : "撤单");
        item.put("rsrvStr4", "50");
        item.put("rsrvValueCode", "CLOR");
        item.put("rsrvStr6", msg.get("areaCode"));
        item.put("rsrvStr5", "1");
        item.put("rsrvStr7", "DECIDE_IOM");
        item.put("rsrvStr12", "1");
        item.put("rsrvStr11", "T");
        item.put("rsrvStr14", "0");
        item.put("rsrvStr15", msg.get("areaCode"));
        item.put("rsrvStr20", msg.get("tradeTypeCode"));
        item.put("rsrvStr21", msg.get("soNbr"));
        item.put("rsrvStr22", "0");
        tradeOther.put("item", item);
        return tradeOther;
    }

    private Map preTradeItemData(Map msg) {
        ArrayList item = new ArrayList();
        HashMap tradeItem = new HashMap();
        HashMap tempMap1 = new HashMap();
        HashMap tempMap2 = new HashMap();
        HashMap tempMap3 = new HashMap();
        HashMap tempMap4 = new HashMap();
        tempMap1.put("xDatatype", "NULL");
        tempMap1.put("attrCode", "STANDARD_KIND_CODE");
        tempMap1.put("attrValue", msg.get("channelType"));

        tempMap2.put("xDatatype", "NULL");
        tempMap2.put("attrCode", "IS_ORDER_TRACK_CP");
        tempMap2.put("attrValue", msg.get("soNbr"));

        tempMap3.put("xDatatype", "NULL");
        tempMap3.put("attrCode", "COMP_TRACK_STATE");
        tempMap3.put("attrValue", msg.get("tradeId"));

        tempMap4.put("xDatatype", "NULL");
        tempMap4.put("attrCode", "CANCELTRACK_REASON");
        tempMap4.put("attrValue",
                null == msg.get("cancelReason") ? msg.get("cancelReason")
                        .toString() : "撤单");

        item.add(tempMap1);
        item.add(tempMap2);
        item.add(tempMap3);
        item.add(tempMap4);
        tradeItem.put("item", item);
        return tradeItem;
    }

    private Object creatTransIDO(Exchange exchange) {
        String str[] = { "@50" };
        TransIdFromRedisValueExtractor transId = new TransIdFromRedisValueExtractor();
        transId.applyParams(str);
        return transId.extract(exchange);
    }

    private void preData(Map msg) {
        try {
            Esql dao = DaoEngine
                    .getMySqlDao("/com/ailk/ecaop/sql/cbss/CbssAreaChangeQuery.esql");
            List result = dao.id("selAreaCode").params(msg).execute();
            if (null == result || 0 == result.size()) {
                throw new EcAopServerBizException("9999", "地市信息转换失败");
            }
            msg.put("areaCode", result.get(0));
        }
        catch (Exception e) {
            // throw new EcAopServerBizException("9999", e.getMessage());
            msg.put("areaCode", msg.get("city"));
        }
        // HashMap ext1 = new HashMap();
        // ext1.put("tradeOther", this.preTradeOtherData(msg));
        // ext1.put("tradeItem", this.preTradeItemData(msg));
        // msg.put("ext", ext1);
        // msg.put("ordersId", msg.get("provOrderId"));
        // msg.put("operTypeCode", "2");

    }

    /**
     * 正式提交参数准备
     * 
     * @param preSubRetMap
     * @param preDateMap
     */
    private void orderSubPreData(Map preSubRetMap, Map preDateMap) {
        List<Map> rspInfo = (List<Map>) preSubRetMap.get("rspInfo");
        if (null == rspInfo || rspInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "cBSS统一预提交未返回应答信息");
        }
        String provOrderId = String.valueOf(rspInfo.get(0).get("provOrderId"));
        String orderNo = String.valueOf(preDateMap.get("tradeId"));
        Integer totalFee = 0;
        List<Map> subOrderSubReqList = new ArrayList<Map>();
        List<Map> payInfoList = new ArrayList<Map>();
        for (Map rsp : rspInfo) {
            List<Map> provinceOrderInfo = (List<Map>) rsp
                    .get("provinceOrderInfo");
            if (null == provinceOrderInfo || 0 == provinceOrderInfo.size()) {
                continue;
            }
            for (Map preovinceOrder : provinceOrderInfo) {
                totalFee = totalFee
                        + Integer.valueOf(preovinceOrder.get("totalFee")
                                .toString());
                String subProvinceOrderId = String.valueOf(preovinceOrder
                        .get("subProvinceOrderId"));
                String subOrderId = String.valueOf(preovinceOrder
                        .get("subOrderId"));
                List<Map> preFeeInfoRsp = (List<Map>) preovinceOrder
                        .get("preFeeInfoRsp");
                List<Map> feeList = dealFee(preFeeInfoRsp);
                Map subOrderMap = MapUtils.asMap("subProvinceOrderId",
                        subProvinceOrderId, "subOrderId", subOrderId,
                        "feeInfo", feeList);
                subOrderSubReqList.add(subOrderMap);
            }
        }
        // 正式提交支付信息节点
        Map payInfoMap = new HashMap();
        payInfoMap.put("payMoney", totalFee);
        payInfoMap.put("payType", "10");
        payInfoMap.put("subProvinceOrderId", provOrderId);
        payInfoList.add(payInfoMap);
        preDateMap.put("provOrderId", provOrderId);// 预提交返回订单
        preDateMap.put("orderNo", orderNo);// 外围系统订单
        preDateMap.put("origTotalFee", totalFee);
        preDateMap.put("subOrderSubReq", subOrderSubReqList);
        preDateMap.put("payInfo", payInfoList);
        preDateMap.put("operationType", "02");

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
        base.put("subscribeId", msg.get("soNbr"));
        // base.put("subscribeId", tradeId);
        // base.put("startDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        // base.put("endDate", "20501231122359");
        base.put("acceptDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("nextDealTag", "Z");// 后续处理状态
        base.put("olcomTag", "0");// 指令标志
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));// 接入方式
        base.put("tradeTypeCode", "0615");// 业务类型编码
        // base.put("productId", productInfo.get(0).get("productId"));// 从上游获取的
        // base.put("brandCode", productList.get(0).get("brandCode"));// 品牌编码
        base.put("userId", "-1");
        base.put("custId", "-1");
        base.put("usecustId", "-1");
        base.put("acctId", "-1");
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

    private Map preTradeItem(Map msg) {
        List<Map> item = new ArrayList<Map>();
        Map tradeItem = new HashMap();
        // IS_ORDER_TRACK_CP-融合订单撤单，IS_ORDER_TRACK_UU-关联单撤单，IS_ORDER_TRACK-普通订单撤单，单产品撤单属于普通订单撤单
        // modify by wangrj3
        item.add(LanUtils.createTradeItem("IS_ORDER_TRACK_CP", msg.get("soNbr")
                .toString()));// 塞原订单流水
        item.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", ""));// 值不知道怎么生成，先塞空吧
        item.add(LanUtils.createTradeItem("CANCELTRACK_REASON", null != msg
                .get("cancelReason") ? msg.get("cancelReason").toString()
                : "撤单"));// 塞撤单原因
        tradeItem.put("item", item);
        return tradeItem;
    }

    private Map preTradeOther(Map msg, String tradeId, Exchange exchange) {
        ArrayList otherItem = new ArrayList();
        HashMap tradeOtherItem = new HashMap();
        HashMap tempMap = new HashMap();

        // 撤单工单（615）tradeOther台账 modify by wangrj3
        tempMap.put("xDatatype", "NULL");
        tempMap.put("rsrvValueCode", "CLOR");// 撤单标识
        tempMap.put("rsrvValue", msg.get("soNbr").toString());// 待撤单原订单流水号
        tempMap.put("rsrvStr1", "track");
        tempMap.put("rsrvStr2", msg.get("cancelReason") + "");// 撤单原因
        tempMap.put("rsrvStr3", msg.get("cancelType") + "");// 撤单类型
        tempMap.put("rsrvStr4", "30");// TODO:融合撤单先写死。 原订单网别
        tempMap.put("rsrvStr5", "1");// 撤单数量
        tempMap.put("rsrvStr6", msg.get("district") + "");// 订单归属地市
        tempMap.put("rsrvStr7", "DECIDE_IOM");// IOM撤单询问
        tempMap.put("rsrvStr8", "true");// true-询问，false-不询问
        tempMap.put("rsrvStr9", msg.get("soNbr").toString());// 最原始的TRADE_ID,传入用于撤单通知
        tempMap.put("modifyTag", "0");// 订单操作类型：0-新增
        tempMap.put("rsrvStr11", "Y");// 订单对应的next_deal_tag状态
        tempMap.put("rsrvStr12", "1");// 撤单改单标志：0-改单，1-撤单
        tempMap.put("rsrvStr13", "");// 订单对应的业务号码
        tempMap.put("rsrvStr14", "0");
        tempMap.put("rsrvStr15", msg.get("city") + "");// 城市编码
        tempMap.put("rsrvStr20", "10");// 原订单业务类型编码tradeTypeCode，从msg中取不到，暂时写死10（开户单）
        tempMap.put("rsrvStr21", msg.get("soNbr").toString());
        tempMap.put("rsrvStr22", "0");// 原订单状态subscribeState

        otherItem.add(tempMap);

        tradeOtherItem.put("item", otherItem);
        return tradeOtherItem;

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
