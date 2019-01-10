package com.ailk.ecaop.common.processor;

import java.util.HashMap;
import java.util.Map;

import org.dom4j.Element;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.eface.engine.EfaceEngine;
import org.n3r.ecaop.eface.engine.XmlBaseEngine;

public abstract class BaseXml2JsonMapping extends BaseXml2JsonNoBodyMapping {

    /**
     * 处理报文体信息
     * 
     * @param str
     * @return
     */
    protected Map dealBody(String str) {
        Map body = new HashMap();
        Element root = analyseXml(str);
        String xmlBody = root.element("SvcCont").getText();
        String actcode = root.elementText("ActivityCode");
        String transIDO = root.element("TransIDO").getText();
        if (null == xmlBody || "".equals(xmlBody)) {
            throw new EcAopServerBizException("9999", "下游系统:'" + actcode + "'接口返回报文内容为空");
        }
        XmlBaseEngine reader = new EfaceEngine().getReaderEngine();
        reader.parseTemplateFile(in);
        body = (Map) reader.createMap(xmlBody);
        body.put("actCode", actcode);
        body.put("transIDO", transIDO);
        return body;
    }
}
