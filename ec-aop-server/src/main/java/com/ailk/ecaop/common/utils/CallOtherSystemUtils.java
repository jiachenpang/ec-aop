package com.ailk.ecaop.common.utils;

import java.lang.reflect.Method;
import java.util.Map;

import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.alibaba.fastjson.JSON;

public class CallOtherSystemUtils {

    /**
     * Ready to parameters in preparation for the incoming parameters,
     * the necessary parameters of the packet header and packet header.
     * 
     * @param paramPath
     * @param exchange
     */
    public void preData(String paramPath, Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        if (body.get("msg") instanceof String) {
            Map msg = JSON.parseObject((String) body.get("msg"));
            body.put("msg", msg);
        }
        ParametersMappingProcessor pmp = new ParametersMappingProcessor();
        String[] parametersMapper = { paramPath };
        pmp.applyParams(parametersMapper);
        pmp.process(exchange);
    }

    /**
     * XML to JSON
     * 
     * @param xmlPath
     * @param exchange
     * @throws Exception
     */
    public void xml2Json(String xmlPath, Exchange exchange, String xml2JsonType) throws Exception {
        Class clazz = Class.forName(xml2JsonType);
        // String[] chkUsrStr = { xmlPath };
        Method applyParams = clazz.getMethod("applyParams");
        Method exec = clazz.getMethod("process");
        applyParams.invoke(clazz.newInstance(), xmlPath);
        exec.invoke(clazz.newInstance(), exchange);
    }
}
