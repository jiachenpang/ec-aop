package com.ailk.ecaop.biz.order;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("isN6ActivateSo")
public class IsN6ActivateSo extends BaseAopProcessor {

    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map mmsg = (Map) body.get("msg");
        String orderId = (String) mmsg.get("orderId");
        mmsg.put("operType", "0");
        body.put("msg", mmsg);
        exchange.getIn().setBody(body);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trades.scccs.orderActivateOrCancelForN6.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.RealNameCardSer");
        lan.xml2Json("ecaop.trades.orderActivateOrCancel.template", exchange);
        // 受理成功时，返回总部和省份订单
        Map outMap = new HashMap();
        outMap.put("orderId", orderId);
        exchange.getOut().setBody(outMap);
    }

}
