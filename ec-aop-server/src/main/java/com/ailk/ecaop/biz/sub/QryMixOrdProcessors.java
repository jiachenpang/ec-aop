package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("qryMixOrdProcessor")
public class QryMixOrdProcessors extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);

        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trades.qmoq.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.PreOrderSer");
        lan.xml2Json("ecaop.trades.qmoq.template", exchange);

        Map retMap = exchange.getOut().getBody(Map.class);
        List<Map> orderInfo = (List<Map>) retMap.get("orderInfo");
        List<Map> list = new ArrayList<Map>();
        for (Map order : orderInfo) {
            Map outMap = new HashMap();
            outMap.put("orderId", ((Map) JSON.parse(body.get("msg").toString())).get("orderId"));
            outMap.put("cbOrderId", order.get("tradeId"));
            outMap.put("orderState", order.get("statusCode"));
            outMap.put("totalFee", order.get("totalFee"));
            outMap.put("canTotalFee", order.get("cancelTotalFee"));
            outMap.put("orderStaTime", order.get("orderStatusTime"));
            outMap.put("backOrdRea", order.get("backOrderReason"));
            outMap.put("canOrdRea", order.get("cancelOrderReason"));
            outMap.put("feeInfo", order.get("feeInfo"));
            list.add(outMap);
        }

        exchange.getOut().setBody(MapUtils.asMap("orderInfo", list));
    }
}
