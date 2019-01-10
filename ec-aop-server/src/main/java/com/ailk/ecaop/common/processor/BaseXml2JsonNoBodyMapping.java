package com.ailk.ecaop.common.processor;

import java.util.HashMap;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.n3r.config.Config;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

public abstract class BaseXml2JsonNoBodyMapping extends BaseAopProcessor implements ParamsAppliable {

    String in;

    /**
     * 解析XMl
     * 
     * @param str
     * @return
     */
    protected Element analyseXml(String str) {
        Document doc = null;
        try {
            doc = DocumentHelper.parseText(str.replace("xmlmsg", ""));
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "返回的报文格式有错，请检查:" + str);
        }
        return doc.getRootElement();
    }

    /**
     * 处理报文头信息
     * 
     * @param str
     * @return
     * @throws Exception
     */
    protected Map dealHeader(String str) throws Exception {
        Element root = analyseXml(str);
        Element response = root.element("Response");
        Object transIDO = root.element("TransIDO").getText();
        if (null == response) {
            throw new EcAopServerSysException("9999", "下游系统报文异常，未返回应答;TRANSIDO为:" + transIDO);
        }
        Map dataMap = new HashMap();
        dataMap.put("headCode", response.element("RspCode").getText());
        dataMap.put("actCode", root.elementText("ActivityCode"));
        dataMap.put("bipCode", root.elementText("BIPCode"));
        dataMap.put("detail", response.element("RspDesc").getText());
        dataMap.put("transIDO", transIDO);
        return dataMap;
    }

    @Override
    public void applyParams(String[] params) {
        this.in = Config.getStr(params[0]) + "_Rsp.xml";
    }
}
