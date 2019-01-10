package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.n3r.ecaop.core.processor.TransReqParamsMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;

public class YearPayEcs4N6Sub extends YearPayEcs4N6Check {

    public YearPayEcs4N6Sub(ParametersMappingProcessor[] pmp) {
        super(pmp);
    }

    @Override
    public Map yearPayEcsCheck(Exchange exchange, Map msg) throws Exception {
        Map threePartInfo = callThreePart(exchange, msg);
        if (!"2".equals(msg.get("changeTag"))) {
            msg.remove("custName");// 姓名乱码，导致传到北六格式有误
            new TransReqParamsMappingProcessor().process(exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.ec-aop.rest");
            Map result = JSON.parseObject(exchange.getOut().getBody(String.class));
            result.put("isNoChangeProduct", "0");
            return result;
        }
        Map preSubmitMap = preSubmit(threePartInfo, exchange);
        MapUtils.arrayPut(preSubmitMap, msg, MagicNumber.COPYARRAY);
        Exchange preSubmitExchange = ExchangeUtils.ofCopy(exchange, preSubmitMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], preSubmitExchange);
        CallEngine.wsCall(preSubmitExchange, "ecaop.comm.conf.url.OrdForNorthSer." + msg.get("province"));
        lan.xml2Json("ecaop.trades.spec.sUniTrade.template", preSubmitExchange);
        return preSubmitExchange.getOut().getBody(Map.class);
    }

    @Override
    public Map yearPayEcsSub(Exchange exchange, Map preSubmitReturn, Map msg) throws Exception {
        Map submitMap = new HashMap();
        MapUtils.arrayPut(submitMap, msg, MagicNumber.COPYARRAY);
        Object orderNo = msg.get("orderNo");
        submitMap.put("orderNo", orderNo);
        List<Map> rspInfoList = (List<Map>) preSubmitReturn.get("rspInfo");
        if (IsEmptyUtils.isEmpty(rspInfoList)) {
            throw new EcAopServerBizException("8888", "北六预提交接口未返回订单编号");
        }
        Map rspInfo = rspInfoList.get(0);
        dealSubmit(submitMap, orderNo, rspInfo);
        Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[4], submitExchange);
        CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.osn.services.ordser");
        lan.xml2Json("ecaop.masb.odsb.template", submitExchange);
        Map outMap = new HashMap();
        outMap.put("code", "0000");
        outMap.put("detail", "OK");
        outMap.put("orderNo", orderNo);
        outMap.put("provOrderId", submitMap.get("provOrderId"));
        return outMap;
    }

    private Map dealSubmit(Map submitMap, Object orderNo, Map rspInfo) {
        Map retMap = new HashMap();
        Object provOrderId = rspInfo.get("bssOrderId");
        submitMap.put("provOrderId", provOrderId);
        submitMap.put("orderNo", rspInfo.get("provOrderId"));
        List<Map> subOrderSubReq = new ArrayList<Map>();
        Map subOrderSub = new HashMap();
        subOrderSub.put("subProvinceOrderId", rspInfo.get("provOrderId"));
        subOrderSub.put("subOrderId", orderNo);
        List<Map> provinceOrderInfo = (List<Map>) rspInfo.get("provinceOrderInfo");
        if (IsEmptyUtils.isEmpty(provinceOrderInfo)) {
            subOrderSub.put("subProvinceOrderId", provOrderId);
            subOrderSubReq.add(subOrderSub);
            submitMap.put("subOrderSubReq", subOrderSubReq);
            submitMap.put("origTotalFee", "0");
        }
        else {
            List<Map> fee = new ArrayList<Map>();
            List<Map> retFee = new ArrayList<Map>();
            int totalFee = 0;
            for (Map provinceOrder : provinceOrderInfo) {
                List<Map> feeInfo = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                if (IsEmptyUtils.isEmpty(feeInfo)) {
                    continue;
                }
                for (Map feeMap : feeInfo) {
                    Map temp = new HashMap();
                    temp.put("feeCategory", feeMap.get("feeMode"));
                    temp.put("feeId", feeMap.get("feeTypeCode"));
                    temp.put("feeDes", feeMap.get("feeTypeName"));
                    temp.put("operateType", "1");
                    temp.put("origFee", feeMap.get("fee"));
                    temp.put("realFee", feeMap.get("fee"));
                    temp.put("payTag", "1");
                    temp.put("calculateTag", "N");
                    temp.put("calculateId", provOrderId);
                    fee.add(temp);
                }
                retFee.addAll(feeInfo);
                totalFee += Integer.valueOf(provinceOrder.get("totalFee").toString());
            }
            subOrderSub.put("feeInfo", fee);
            subOrderSub.put("subProvinceOrderId", provOrderId);
            subOrderSubReq.add(subOrderSub);
            submitMap.put("subOrderSubReq", subOrderSubReq);
            submitMap.put("origTotalFee", totalFee);
        }
        submitMap.put("cancleTotalFee", "0");
        return retMap;
    }
}
