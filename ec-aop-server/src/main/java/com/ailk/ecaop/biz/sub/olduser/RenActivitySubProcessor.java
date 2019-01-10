package com.ailk.ecaop.biz.sub.olduser;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.alibaba.fastjson.JSON;

@EcRocTag("RenActivitySub")
public class RenActivitySubProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) exchange.getIn().getBody(Map.class).get("msg"));
        }
        else {
            msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        }
        String essSubscribeId = (String) msg.get("essSubscribeId");
        Map retMap = new HashMap();
        retMap.put("SubscribeId", essSubscribeId);
        exchange.getOut().setBody(retMap);

    }

}
