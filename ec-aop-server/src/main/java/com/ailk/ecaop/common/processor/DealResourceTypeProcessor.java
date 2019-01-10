package com.ailk.ecaop.common.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.DealResOccupyAndRlsUtils;

@EcRocTag("dealResourceType")
public class DealResourceTypeProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map msgMap = (Map) exchange.getIn().getBody(Map.class).get("msg");
        new ArrayList<Map>();
        List<Map> userInfo = (List<Map>) msgMap.get("userInfo");
        for (Map userMap : userInfo) {
            List<Map> activityInfo = (List<Map>) userMap.get("activityInfo");
            for (Map activityMap : activityInfo) {
                String resourcesType = (String) activityMap.get("resourcesType");
                DealResOccupyAndRlsUtils rr = new DealResOccupyAndRlsUtils();
                activityMap.put("resourcesType", rr.decodeTerminalType(resourcesType));
            }
        }
    }
}
