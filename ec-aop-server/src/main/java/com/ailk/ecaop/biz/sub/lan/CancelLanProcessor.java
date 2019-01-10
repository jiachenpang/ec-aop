package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;
import com.alibaba.fastjson.JSONObject;

@EcRocTag("cancelLan")
public class CancelLanProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        String oldBssOrderId = msg.get("oldBssOrderId").toString();

        // 准备改撤单校验接口请求参数
        msg = preInterFaceInput4OrderCHGCHK(msg);
        body.put("msg", msg);
        exchange.getIn().setBody(body);

        // 调用改撤单校验接口
        OrderCHGCHKProcessor occk = new OrderCHGCHKProcessor();
        occk.process(exchange);

        if (200 != (Integer) exchange.getProperty(Exchange.HTTP_STATUSCODE)) {
            return;
        }

        // 准备撤单预提交接口请求参数
        msg = preInterFaceInput4OrderCle(msg);
        body.put("msg", msg);
        exchange.getIn().setBody(body);

        // 调用撤单预提交接口
        OrdCleProcessor ococ = new OrdCleProcessor();
        ococ.process(exchange);
        if (200 != (Integer) exchange.getProperty(Exchange.HTTP_STATUSCODE)) {
            return;
        }

        // 准备订单提交接口请求参数
        Map retMap = exchange.getOut().getBody(Map.class);
        ArrayList<Map> cancelTradeInfo = (ArrayList<Map>) retMap.get("cancelTradeInfo");
        msg.put("provinceOrderId", cancelTradeInfo.get(0).get("provinceOrderId"));
        msg = preInterFaceInput4OrderSub(msg);
        JSONObject orderSub = new JSONObject();
        orderSub.putAll(msg);
        orderSub.remove("feeInfo");
        body.put("msg", orderSub);

        exchange.getIn().setBody(body);

        // 调用订单提交接口
        LanUtils lan = new LanUtils();
        String methodCode = exchange.getMethodCode();
        exchange.setMethodCode("odsb");
        lan.preData("ecaop.masb.odsb.ActivityAryParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.ordser");
        exchange.setMethodCode(methodCode);
        lan.xml2Json("ecaop.masb.odsb.template", exchange);

        // 判断要不要调用退费接口
        if ("1".equals(msg.get("refundTag"))) {
            if (200 != (Integer) exchange.getProperty(Exchange.HTTP_STATUSCODE)) {
                return;
            }
            msg.put("provOrderId", oldBssOrderId);
            body.put("msg", msg);
            exchange.getIn().setBody(body);
            methodCode = exchange.getMethodCode();
            exchange.setMethodCode("cfsb");
            FeeParamsProcessor feePP = new FeeParamsProcessor();
            feePP.process(exchange);
            lan.preData("ecaop.masb.cfsb.ParametersMapping", exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.ordser");
            exchange.setMethodCode(methodCode);
            lan.xml2Json("ecaop.masb.cfsb.template", exchange);
        }
    }

    private Map preInterFaceInput4OrderCHGCHK(Map msg) {
        msg.put("orderId", msg.get("oldProvOrderId"));
        msg.put("checkOrderId", msg.get("oldBssOrderId"));
        msg.put("operId", msg.get("operatorId"));
        msg.put("checkType", "1");
        return msg;
    }

    private Map preInterFaceInput4OrderCle(Map msg) {
        msg.put("orderId", msg.get("provOrderId"));
        msg.put("provinceOrderId", msg.get("oldBssOrderId"));
        msg.put("orgOrderId", msg.get("oldProvOrderId"));
        msg.put("operType", "0");
        return msg;
    }

    private Map preInterFaceInput4OrderSub(Map inputMap) {
        JSONObject msg = (JSONObject) inputMap;
        msg.put("tempNo", msg.get("provinceOrderId"));
        msg.put("orderNo", msg.get("provOrderId"));
        msg.put("provOrderId", msg.get("tempNo"));
        msg.put("operationType", "01");
        msg.put("noteType", "1");

        msg.put("cancelTotalFee", "0");
        msg.put("origTotalFee", "0");
        return msg;
    }
}
