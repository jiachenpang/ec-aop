package com.ailk.ecaop.biz.sub.olduser;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("OldUserRenActivityN6Sub")
public class OldUserRenActivityN6Sub extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        // TODO Auto-generated method stub
        Map body = exchange.getIn().getBody(Map.class);
        LanUtils lan = new LanUtils();
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) exchange.getIn().getBody(Map.class).get("msg"));
        }
        else {
            msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        }

        String provOrderId = String.valueOf(msg.get("provOrderId"));
        lan.preData("ecaop.trades.mpsb.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.OrderSub" + "." + msg.get("province"));
        lan.xml2Json("ecaop.trades.mpsb.template", exchange);
        Map retMap = exchange.getOut().getBody(Map.class);
        retMap.put("provOrderId", provOrderId);
        exchange.getOut().setBody(retMap);

    }

}
