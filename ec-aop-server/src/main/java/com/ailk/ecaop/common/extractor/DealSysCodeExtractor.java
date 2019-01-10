package com.ailk.ecaop.common.extractor;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.ParameterValueExtractor;

@EcRocTag("SysCode")
public class DealSysCodeExtractor implements ParameterValueExtractor {

    static Map sysCodeMap = new HashMap() {

        {
            put("elsb", "ESAL");
            put("masb", "EMAL");
            put("mnsb", "MINI");
            put("gdsb", "GDJK");
            put("2bsb", "2BSB");
            put("jkzy", "JKZY");
            put("aple", "APLE");
            put("ussb", "USSB");
            put("cmsb", "CMSB");
            put("saip", "SAIP");
            put("nxpr", "NXPR");
        }
    };

    @Override
    public Object extract(Exchange exchange) {
        String appkey = exchange.getAppCode();
        Object sysCode = sysCodeMap.get(appkey);
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        if (null != sysCode) {
            msg.put("sysCode", sysCode);
        }
        else {
            msg.put("sysCode", appkey.toUpperCase().substring(0,4));
        }
        return msg;
    }

}
