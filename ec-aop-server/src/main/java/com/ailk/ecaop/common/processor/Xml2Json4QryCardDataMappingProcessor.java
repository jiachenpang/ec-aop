package com.ailk.ecaop.common.processor;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;

import com.ailk.ecaop.common.utils.DealInterfaceUtils;

@EcRocTag("Xml2Json4QryCardDataMapper")
public class Xml2Json4QryCardDataMappingProcessor extends BaseXml2JsonMapping {

    private static final String ACTIVITYCODE = "T3M00006T3M00028T3M00033";

    @Override
    public void process(Exchange exchange) throws Exception {
        Throwable ex = exchange.getException();
        if (ex != null) {
            ex.printStackTrace();
            throw new EcAopServerBizException("9999", "调用接口异常:" + ex.getMessage());
        }
        String body = exchange.getOut().getBody().toString();
        Map headMap = dealHeader(body);
        DealInterfaceUtils dif = new DealInterfaceUtils();
        if (!ACTIVITYCODE.contains((CharSequence) headMap.get("actCode"))) {
            throw new EcAopServerBizException("Xml2Json4QryCardDataMappingProcessor只供卡数据查询接口使用！");
        }
        String code = dif.dealTimeOutCode(headMap.get("headCode").toString());
        if (!"0000".equals(code) && !"9999".equals(code)) {

            throw new EcAopServerSysException(code, dif.dealRespDesc(headMap));
        }
        Map bodyMap = dealBody(body);
        code = bodyMap.get("code").toString();
        if (!"0000".equals(code)) {
            throw new EcAopServerBizException(code, dif.dealRespDesc(bodyMap));
        }
        dif.removeCodeDetail(bodyMap);
        if ("gdsb".equals(exchange.getAppCode())) {
            String cardData = bodyMap.get("cardData").toString();
            bodyMap.put("cardData", dif.dealCardData(cardData));
        }
        exchange.getOut().setBody(bodyMap);
    }
}
