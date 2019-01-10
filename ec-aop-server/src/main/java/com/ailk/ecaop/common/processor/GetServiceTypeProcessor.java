package com.ailk.ecaop.common.processor;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
@EcRocTag("getServiceType")
public class GetServiceTypeProcessor extends BaseAopProcessor{

    @Override
    public void process(Exchange exchange) throws Exception {
        Map msgMap = (Map) exchange.getIn().getBody(Map.class).get("msg");
        String serviceType = (String) msgMap.get("serviceClassCode");
        if("0000".equals(serviceType)) {
            msgMap.put("serviceType", "01");
        }else {
            msgMap.put("serviceType", "02");
        }

    }

}
