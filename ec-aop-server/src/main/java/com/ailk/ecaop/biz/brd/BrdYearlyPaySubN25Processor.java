package com.ailk.ecaop.biz.brd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.impl.DefaultMessage;

import com.ailk.ecaop.common.utils.YearPayUtils;

/**
 * 南25省宽带趸交提交
 * 
 * @author Steven Zhang
 */
@EcRocTag("brdYearlyPaySubN25")
public class BrdYearlyPaySubN25Processor extends BrdYearlyPayCheckN25Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        YearPayUtils ypu = new YearPayUtils();
        ypu.callGetBroadbandAcctInfo(exchange);
        Map threePartMap = ypu.callCheckUserInfo(exchange, "0090");
        Map userProduct = ypu.callQryUserProInfo(exchange);
        Map preSubmitReqMap = preData4PreSubmit(exchange.getProvince(), userProduct, threePartMap);
        Map preSubmitRetMap = ypu.callSProductChg(exchange, preSubmitReqMap);
        Object orderNo = ((Map) exchange.getIn().getBody(Map.class).get("msg")).get("orderNo");
        Object provOrderId = ((Map) preSubmitRetMap.get("rspInfo")).get("provOrderId");
        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        ypu.callOrder(exchange, preOrderSubMap(preSubmitRetMap, orderNo, msg));
        Map outMap = dealReturn(orderNo, provOrderId);
        Message out = new DefaultMessage();
        out.setBody(outMap);
        exchange.setOut(out);
    }

    @Override
    public void process(Exchange exchange, Map threePartMap, Map userProduct, Object province) throws Exception {
        YearPayUtils ypu = new YearPayUtils();
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map preSubmitReqMap = preData4PreSubmit(province, userProduct, threePartMap);
        Map preSubmitRetMap = ypu.callSProductChg(exchange, preSubmitReqMap);
        Object orderNo = msg.get("orderNo");
        Object provOrderId = ((Map) preSubmitRetMap.get("rspInfo")).get("provOrderId");
        ypu.callOrder(exchange, preOrderSubMap(preSubmitRetMap, orderNo, msg));
        Map outMap = dealReturn(orderNo, provOrderId);
        Message out = new DefaultMessage();
        out.setBody(outMap);
        exchange.setOut(out);
    }

    public Map preOrderSubMap(Map preSubmitRetMap, Object orderNo, Map msg) {
        Map orderSubMap = new HashMap();
        orderSubMap.put("operationType", "01");// 02-订单取消
        Map rspInfo = (Map) preSubmitRetMap.get("rspInfo");
        orderSubMap.put("provOrderId", rspInfo.get("provOrderId"));
        List<Map> subOrderSubReq = new ArrayList<Map>();
        Map subOrderSub = new HashMap();
        subOrderSub.put("subProvinceOrderId", rspInfo.get("provOrderId"));
        subOrderSub.put("subOrderId", orderNo);
        List<Map> fee = new ArrayList<Map>();
        List<Map> feeInfo = (List<Map>) msg.get("feeInfo");
        for (Map feeMap : feeInfo) {
            Map temp = new HashMap();
            temp.put("feeCategory", feeMap.get("feeMode"));
            temp.put("feeId", feeMap.get("feeTypeCode"));
            temp.put("feeDes", feeMap.get("feeTypeName"));
            temp.put("operateType", "1");
            temp.put("origFee", feeMap.get("fee"));
            temp.put("realFee", feeMap.get("fee"));
            temp.put("isPay", feeMap.get("isPay"));
            temp.put("payTag", feeMap.get("payTag"));
            fee.add(temp);
        }
        subOrderSub.put("feeInfo", fee);
        subOrderSub.put("subProvinceOrderId", rspInfo.get("provOrderId"));
        subOrderSubReq.add(subOrderSub);
        orderSubMap.put("subOrderSubReq", subOrderSubReq);
        orderSubMap.put("origTotalFee", rspInfo.get("totalFee"));
        return orderSubMap;
    }

    public Map dealReturn(Object orderNo, Object provOrderId) {
        Map retMap = new HashMap();
        retMap.put("code", "0000");
        retMap.put("detail", "TradeOK");
        retMap.put("orderNo", orderNo);
        retMap.put("provOrderId", provOrderId);
        return retMap;
    }
}
