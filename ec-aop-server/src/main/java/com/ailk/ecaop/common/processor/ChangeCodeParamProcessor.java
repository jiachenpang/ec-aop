package com.ailk.ecaop.common.processor;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("ChangeCodeParam")
public class ChangeCodeParamProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) {
        Map msg = new HashMap();
        Map<String, Object> body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        msg.put("city", ChangeCodeUtils.changeEparchy(msg));
        body.put("msg", msg);
        exchange.getIn().setBody(body);

    }

}
