package com.ailk.ecaop.biz.order;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;

/**
 * @auther shenzy
 * @create 20161208
 */
@EcRocTag("isN25ActivateSo")
public class IsN25ActivateSo extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        String orderId = (String) msg.get("orderId");
        msg.put("operType", "0");
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trades.scccs.orderActivateOrCancel.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.RealNameCardSer");
        lan.xml2Json("ecaop.trades.orderActivateOrCancel.template", exchange);
        // 受理成功时，返回总部和省份订单
        Map outMap = new HashMap();
        outMap.put("orderId", orderId);
        exchange.getOut().setBody(outMap);
    }
}
