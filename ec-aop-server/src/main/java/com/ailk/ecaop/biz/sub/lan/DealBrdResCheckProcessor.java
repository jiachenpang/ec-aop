package com.ailk.ecaop.biz.sub.lan;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.alibaba.fastjson.JSON;

@EcRocTag("DealBrdResCheck")
public class DealBrdResCheckProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        msg.put("queryMode", msg.get("antiTpye"));
        msg.put("speedLevel", msg.get("brandSpeed"));
        msg.put("npFlag", msg.get("NpFlag"));
        Map shareNbrInfo = (Map) msg.get("shareNbrInfo");
        if (null != shareNbrInfo && !shareNbrInfo.isEmpty()) {
            msg.put("vnId", shareNbrInfo.get("vnId"));
            msg.put("departId", shareNbrInfo.get("departId"));
            msg.put("staffId", shareNbrInfo.get("staffId"));
            msg.put("userName", shareNbrInfo.get("userName"));
        }
        body.put("msg", msg);
        exchange.getIn().setBody(body);

    }

}
