package com.ailk.ecaop.common.processor;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.alibaba.fastjson.JSONObject;

@EcRocTag("getOpeSysType")
public class GetOpeSysTypeProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = (Map) exchange.getIn().getBody();
        Object msgObject = body.get("msg");
        Map map = null;
        if (msgObject instanceof String) {
            map = JSONObject.parseObject(msgObject.toString());
        }
        else {
            map = (Map) msgObject;
        }
        if (null == map.get("opeSysType") || "".equals(map.get("opeSysType"))) {
            map.put("opeSysType", "2");
        }
        body.put("msg", map);
        exchange.getIn().setBody(body);

    }

}
