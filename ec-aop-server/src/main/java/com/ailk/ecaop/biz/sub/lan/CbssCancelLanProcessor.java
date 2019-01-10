package com.ailk.ecaop.biz.sub.lan;

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
import org.n3r.ecaop.core.extractor.TransIdFromRedisValueExtractor;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.n3r.ecaop.dao.base.DaoEngine;
import org.n3r.esql.Esql;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.CallProcessorUtils;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("cbsscancelLan")
public class CbssCancelLanProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.masb.occk.isAllowCancelSoParametersMapping",
            "ecaop.trades.sccc.cancelPre.paramtersmapping", "ecaop.trades.sccc.cancel.paramtersmapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[3];
    LanUtils lan = new LanUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        // oldBssOrderId = msg.get("oldBssOrderId").toString();
        msg.put("tradeId", creatTransIDO(exchange));
        if ("83".equals(msg.get("province"))) {
            msg.put("soNbr", msg.get("oldBssOrderId"));
        }
        Exchange tempExchange = ExchangeUtils.ofCopy(exchange, msg);
        CallProcessorUtils call = CallProcessorUtils.newCallIFaceUtils(tempExchange);
        call.preData("ecaop.masb.occk.isAllowCancelSoParametersMapping");
        call.wsCall("ecaop.comm.conf.url.cbss.services.orderChgandCleSer");
        call.xml2Json("ecaop.masb.occk.isAllowCancelSoTemplate");
        Map outMap = tempExchange.getOut().getBody(Map.class);
        if (outMap.get("code") != null && !"0".equals(outMap.get("code"))) {
            return;
        }
        String cancelFlag = (String) outMap.get("cancelFlag");
        if (StringUtils.isEmpty(cancelFlag)) {
            throw new EcAopServerBizException("9999", "省份返回是否允许撤单标志为空");
        }
        if ("N".equals(cancelFlag)) {
            throw new EcAopServerBizException("1802", "省分不允许撤单");
        }
        // 预提交

        msg.putAll(preData(msg));
        msg.put("base", preBaseData(msg, exchange.getAppCode()));
        lan.preData(pmp[1], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.sccc.cancelPre.template", exchange);

        Map retMap = exchange.getOut().getBody(Map.class);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (null == rspInfoList || 0 == rspInfoList.size()) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }

        // 这两个信息要最终返回给接入系统，如不在此设置，可能会被后面的返回覆盖
        Map rspInfo = rspInfoList.get(0);
        String provOrderId = rspInfo.get("provOrderId").toString();
        String bssOrderId = rspInfo.get("bssOrderId").toString();
        Map orderMap = preOrderSubParam(rspInfo, msg);
        orderMap.put("orderNo", provOrderId);
        orderMap.put("provOrderId", bssOrderId);
        msg.putAll(orderMap);
        body.put("msg", msg);
        exchange.getIn().setBody(body);

        // 正式提交
        lan.preData(pmp[2], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        lan.xml2Json("ecaop.trades.sccc.cancel.template", exchange);
        // 受理成功时，返回总部和省份订单
        Map out = new HashMap();
        out.put("provOrderId", provOrderId);
        out.put("bssOrderId", bssOrderId);
        exchange.getOut().setBody(out);

        // 判断要不要调用退费接口
        if ("1".equals(msg.get("refundTag"))) {
            if (200 != (Integer) exchange.getProperty(Exchange.HTTP_STATUSCODE)) {
                return;
            }
            body.put("msg", msg);
            exchange.getIn().setBody(body);
            lan.preData(pmp[2], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
            lan.xml2Json("ecaop.trades.sccc.cancel.template", exchange);
        }
    }

    private Map preOrderSubParam(Map inMap, Map msg) {
        Map outMap = new HashMap();
        List<Map> provinceOrderInfo = (ArrayList<Map>) inMap.get("provinceOrderInfo");
        if (null != provinceOrderInfo && 0 != provinceOrderInfo.size()) {
            outMap.put("subOrderSubReq", dealSubOrder(provinceOrderInfo, msg));
        }
        outMap.put("origTotalFee", "0");
        outMap.put("operationType", "01");
        outMap.put("cancleTotalFee", provinceOrderInfo.get(0).get("totalFee"));
        return outMap;
    }

    private Map dealSubOrder(List<Map> provinceOrderInfo, Map msg) {
        String oldBssOrderId = (String) msg.get("oldBssOrderId");
        Map retMap = new HashMap();
        for (Map tempMap : provinceOrderInfo) {
            retMap.put("subOrderId", tempMap.get("subOrderId"));
            retMap.put("subProvinceOrderId", tempMap.get("subProvinceOrderId"));
            if ("1" == msg.get("payMode")) {
                return retMap;// 施工支付时只准备订单
            }
            else if (oldBssOrderId.equals(tempMap.get("subProvinceOrderId"))) {
                List<Map> feeList = (ArrayList<Map>) tempMap.get("preFeeInfoRsp");
                List<Map> retFee = new ArrayList<Map>();
                if (null != feeList && 0 != feeList.size()) {
                    for (Map fee : feeList) {
                        Map tempFee = dealFeeInfo(fee);
                        retFee.add(tempFee);
                    }
                    retMap.put("feeInfo", retFee);
                }
            }
        }

        if (null == retMap.get("feeInfo")) {
            throw new EcAopServerBizException("9999", "省分'撤单预提交接口[ordCle]'未返回与商城提供订单[oldProvOrderId]:'" + oldBssOrderId
                    + "'匹配的数据");
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

    private Map preBaseData(Map msg, String appCode) {
        Map base = new HashMap();
        base.put("subscribeId", msg.get("oldBssOrderId"));
        base.put("tradeId", msg.get("tradeId"));
        base.put("startDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("tradeTypeCode", "0615");// 宽带默认订单类型0615 chg by wangmc 20181212
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("areaCode", msg.get("areaCode"));
        base.put("foregift", "0");
        base.put("execTime", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("acceptDate", RDate.currentTimeStr("yyyyMMddHHmmss"));
        base.put("chkTag", "0");
        base.put("operFee", "0");
        base.put("cancelTag", "0");
        base.put("endAcycId", "203701");
        base.put("startAcycId", RDate.currentTimeStr("yyyyMM"));
        base.put("acceptMonth", RDate.currentTimeStr("MM"));
        base.put("netTypeCode", "40");// 宽带默认40 chg by wangmc 20181212
        base.put("advancePay", "0");
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
        base.put("custId", "-1");
        base.put("acctId", "-1");
        base.put("serinalNamber", "-1");
        base.put("productId", "-1");
        base.put("tradeStaffId", msg.get("operatorId"));
        base.put("userDiffCode", "00");
        base.put("custName", "-1");
        base.put("brandCode", "GZKD");// 宽带默认GZKD chg by wangmc 20181212
        base.put("usecustId", "-1");
        base.put("checktypeCode", "8");
        base.put("userId", "-1");
        base.put("termIp", "132.35.81.217");
        base.put("eparchyCode", msg.get("areaCode"));
        base.put("cityCode", msg.get("district"));
        return base;
    }

    private Object creatTransIDO(Exchange exchange) {
        String str[] = { "@50" };
        TransIdFromRedisValueExtractor transId = new TransIdFromRedisValueExtractor();
        transId.applyParams(str);
        return transId.extract(exchange);
    }

    private Map preData(Map msg) {
        try {
            Esql dao = DaoEngine.getMySqlDao("/com/ailk/ecaop/sql/cbss/CbssAreaChangeQuery.esql");
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
        Map ext = preExtData(msg);
        msg.put("ordersId", msg.get("provOrderId"));
        msg.put("operTypeCode", "0");
        msg.put("ext", ext);
        return msg;
    }

    private Map preExtData(Map msg) {
        Map ext = new HashMap();
        ext.put("tradeItem", preDataForTradeItem(msg));
        ext.put("tradeOther", preTradeOther(msg));
        return ext;
    }

    private Map preTradeOther(Map msg) {
        Map tradeOther = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("rsrvValueCode", "CLOR");
        item.put("rsrvValue", msg.get("oldProvOrderId"));
        item.put("rsrvStr1", "track");
        item.put("rsrvStr2", "1");
        item.put("rsrvStr3", "");
        item.put("rsrvStr4", "40");
        item.put("rsrvStr5", "1");
        item.put("rsrvStr6", ChangeCodeUtils.changeCityCode(msg));
        item.put("rsrvStr7", "DECIDE_IOM");
        item.put("rsrvStr8", "false");
        item.put("rsrvStr9", "");
        item.put("modifyTag", "0");
        item.put("rsrvStr11", "Y");
        item.put("rsrvStr12", "1");
        item.put("rsrvStr13", msg.get("provOrderId"));// 订单号码
        item.put("rsrvStr14", "0");
        item.put("rsrvStr15", msg.get("district"));
        item.put("rsrvStr20", "10");
        item.put("rsrvStr21", msg.get("oldProvOrderId"));
        item.put("rsrvStr22", "0");
        item.put("rsrvStr23", msg.get("district"));
        itemList.add(item);
        tradeOther.put("item", itemList);
        return tradeOther;
    }

    private Map preDataForTradeItem(Map msg) {
        List<Map> itemList = new ArrayList<Map>();
        Map tradeItem = new HashMap();
        itemList.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", "1010600"));
        itemList.add(LanUtils.createTradeItem("CANCELTRACK_REASON", ""));
        itemList.add(LanUtils.createTradeItem("IS_ORDER_TRACK", msg.get("oldProvOrderId")));
        tradeItem.put("item", itemList);
        return tradeItem;
    }

    @Override
    public void applyParams(String[] params) {
        // TODO Auto-generated method stub
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
