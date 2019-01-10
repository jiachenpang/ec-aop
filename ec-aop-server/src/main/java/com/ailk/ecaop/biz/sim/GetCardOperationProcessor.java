package com.ailk.ecaop.biz.sim;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.extractor.TransIdFromRedisValueExtractor;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("GetCardOperation")
public class GetCardOperationProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        Object transIDO = new TransIdFromRedisValueExtractor().extract(exchange);
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        msg.put("transIDO", transIDO);
        lan.preData("ecaop.trades.mccr.ParametersMapping", exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.osn.syncreceive.0400");
        lan.xml2Json4ONS("ecaop.trades.mccr.template", exchange);
        Map result = exchange.getOut().getBody(Map.class);
        result.put("procId", transIDO);
        exchange.getOut().setBody(result);
    }

}
