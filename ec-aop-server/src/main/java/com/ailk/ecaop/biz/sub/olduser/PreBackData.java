package com.ailk.ecaop.biz.sub.olduser;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

@EcRocTag("PreBackData")
public class PreBackData extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map<String, Object> body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        String provOrderId = (String) msg.get("ordersId");
        Map retMap = exchange.getOut().getBody(Map.class);
        retMap.put("essSubscribeId", provOrderId);
        exchange.getOut().setBody(retMap);
    }

}
