package com.ailk.ecaop.base;

import javax.servlet.http.HttpServletRequest;

import org.apache.axis2.context.MessageContext;
import org.apache.axis2.transport.http.HTTPConstants;
import org.n3r.ecaop.core.servlet.EcAopMainService;
import org.n3r.ecaop.core.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

public class EcAopService {

    public String ecaopwebsrv(String reqxml) {
        Logger log = LoggerFactory.getLogger(EcAopService.class);
        log.info("\nWebService调用方式之请求XML：" + reqxml);
        EcAopMainService ec = new EcAopMainService();
        JSONObject rspMsp = ec.doService(reqxml, getClientIpAxis());
        JSONObject response = new JSONObject();
        response.put("response", rspMsp);
        String rspxml = JsonUtils.json2xml(response);
        log.info("\nWebService调用方式之返回XML：" + rspxml);
        return rspxml;
    }

    /**
     * 获取请求地址IP
     */
    private String getClientIpAxis() {
        MessageContext mc = null;
        HttpServletRequest request = null;
        try {
            mc = MessageContext.getCurrentMessageContext();
            if (mc == null) {
                throw new Exception("无法获取到MessageContext");
            }
            request = (HttpServletRequest) mc.getProperty(HTTPConstants.MC_HTTP_SERVLETREQUEST);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return request.getRemoteAddr();
    }
}
