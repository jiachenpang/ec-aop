package com.ailk.ecaop.biz.cust;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.alibaba.fastjson.JSON;

@EcRocTag("CustInfoCreate")
public class CustInfoCreateProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map retMap = new HashMap();
        Map bodyMap = new HashMap();
        Object body = exchange.getOut().getBody();
        if (body instanceof String) {
            bodyMap = JSON.parseObject((String) body);
        }
        else {
            bodyMap = exchange.getOut().getBody(Map.class);
        }
        List<Map> respInfoList = (List<Map>) bodyMap.get("respInfo");
        if (null != respInfoList && !respInfoList.isEmpty()) {

            String custId = (String) respInfoList.get(0).get("custId");
            Map respInfo = new HashMap();
            respInfo.put("custId", custId);
            retMap.put("respInfo", respInfo);
            exchange.getOut().setBody(retMap);
        }
        else {
            exchange.getOut().setBody(bodyMap);
        }
    }
}
