package com.ailk.ecaop.biz.user;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("checkOweSingle")
public class CheckOweSingleProcessors extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        Map body = (Map) exchange.getIn().getBody();
        Map msg = new HashMap((Map) body.get("msg"));

        lan.preData("ecaop.trades.qcar.checkOwe.ParametersMapping", exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.cbss.services.PayMentFee");
        lan.xml2Json4ONS("ecaop.trades.qcar.checkOwetemplate", exchange);

        body = (Map) exchange.getOut().getBody();
        String fee = String.valueOf(Integer.valueOf((String) body.get("fee")) / 10);
        body.put("fee", fee);
        body.put("city", msg.get("city"));
        exchange.getOut().setBody(body);

    }

}
