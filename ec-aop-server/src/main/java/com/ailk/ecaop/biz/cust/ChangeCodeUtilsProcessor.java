package com.ailk.ecaop.biz.cust;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

@EcRocTag("ChangeCodeUtils")
public class ChangeCodeUtilsProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map bady = exchange.getIn().getBody(Map.class);
        Map msg = (Map) bady.get("msg");
        List<Map> custInfo = (List<Map>) msg.get("custInfo");
        if (null != custInfo && 0 != custInfo.size()) {
            for (Map tempMap : custInfo) {
                String checkType = (String) tempMap.get("checkType");
                if (StringUtils.isNotEmpty(checkType)) {
                    if ("01".equals(checkType)) {
                        tempMap.put("checkType", "2");
                    }
                    else if ("02".equals(checkType)) {
                        tempMap.put("checkType", "1");
                    }
                    else if ("03".equals(checkType)) {
                        tempMap.put("checkType", "0");
                    }
                }
            }
        }
    }
}
