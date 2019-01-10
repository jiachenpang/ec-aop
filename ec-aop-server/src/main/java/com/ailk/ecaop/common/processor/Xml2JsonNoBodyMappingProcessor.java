package com.ailk.ecaop.common.processor;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;

import com.ailk.ecaop.common.utils.DealInterfaceUtils;

@EcRocTag("Xml2JsonNobody")
public class Xml2JsonNoBodyMappingProcessor extends BaseXml2JsonNoBodyMapping {

    String[] params = { in };

    @Override
    public void process(Exchange exchange) throws Exception {
        Throwable ex = exchange.getException();
        if (ex != null) {
            ex.printStackTrace();
            throw new EcAopServerBizException("9999", "调用接口异常:" + ex.getMessage());
        }
        String body = exchange.getOut().getBody().toString();
        body = new URLDecodeProcessor().doCode(body);
        Map dataMap = dealHeader(body);
        DealInterfaceUtils dif = new DealInterfaceUtils();
        String code = dif.dealTimeOutCode(dataMap.get("headCode").toString());
        if (!"0000".equals(code)) {
            throw new EcAopServerSysException(code, dif.dealRespDesc(dataMap));
        }
        exchange.getOut().setBody(new HashMap());
    }
}
