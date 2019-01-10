package com.ailk.ecaop.common.extractor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.n3r.config.Config;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.core.util.ParamsUtils;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.ParameterValueExtractor;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.eface.engine.XmlWriterEngine;
import org.n3r.freemarker.FreemarkerTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ailk.ecaop.common.Itarget;
import com.ailk.ecaop.common.utils.SoapUtils;
import com.alibaba.fastjson.JSON;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

import freemarker.template.Template;

@EcRocTag("json2xml")
public class Json2XmlParamterValueExtractor implements ParameterValueExtractor, ParamsAppliable {

    private final Pattern compile = Pattern.compile("^json2xmlConfigKey:.+$");
    private String xmlTemplatePath;
    private String systemTag;
    private String serviceName;
    private String operatorName;
    private String bodyNameSpaceStr;
    private String rootNodeName;
    private String SOAP_END = "</soapenv:Body></soapenv:Envelope>";
    private final String NAME_SAPCE = "ecaop.soap.bodyns";

    private Logger log = LoggerFactory.getLogger(Json2XmlParamterValueExtractor.class);

    @Override
    public Object extract(Exchange exchange) {

        Map<String, Object> root = exchange.getIn().getBody(Map.class);
        serviceName = (String) root.get("serviceName");
        operatorName = (String) root.get("operateName");

        String templateName = "xmlTemplte." + systemTag;

        String template = "";

        // log.info("START TO USE ONS BAO WEN:!!!!!!!!!!!!!!!!!!!!!!!!!!!");

        if (systemTag.equals(Itarget.SYSTEM_TAG_ONS)) {
            template = Itarget.REQ_JSON_ONS;
            exchange.setProperty("bipCode", root.get("bipCode"));
            exchange.setProperty("activityCode", root.get("activityCode"));
        }
        else if (systemTag.equals(Itarget.SYSTEM_TAG_3GE)) {
            template = Itarget.REQ_JSON;
            exchange.setProperty("bipCode", root.get("bipCode"));
            exchange.setProperty("activityCode", root.get("activityCode"));
        }
        else if (systemTag.equals(Itarget.SYSTEM_TAG_FPAY)) {
            template = Itarget.REQ_JSON_FPAY;
            exchange.setProperty("bipCode", root.get("serviceName"));
            exchange.setProperty("activityCode", root.get("operateName"));
        }
        else if (systemTag.equals(Itarget.SYSTEM_TAG_FBS)) {
            template = Itarget.REQ_JSON_FBS;
            exchange.setProperty("bipCode", root.get("serviceName"));
            exchange.setProperty("activityCode", root.get("operateName"));
        }
        else if (systemTag.equals(Itarget.SYSTEM_TAG_ESS)) {
            template = Itarget.REQ_JSON_ESS;
            exchange.setProperty("bipCode", root.get("bipCode"));
            exchange.setProperty("activityCode", root.get("activityCode"));
        }
        else {
            throw new EcAopServerBizException("报文模板配置错误 : " + systemTag);
        }
        Template putTemplate = FreemarkerTemplateEngine.putTemplate(templateName, template);
        String freemarkerStr = null;
        try {
            freemarkerStr = FreemarkerTemplateEngine.process(root, putTemplate);
        }
        catch (Exception e) {
            System.out.println("information:" + root);
            throw new EcAopServerBizException("Template process error!" + e.getMessage(), e);
        }

        // log.debug("msg after template parse:" + freemarkerStr);

        Map inputMap = null;
        try {
            inputMap = JSON.parseObject(freemarkerStr);
            Object o = inputMap.get("testIp");
            if (o == null || "".equals(o)) {
                inputMap.remove("testIp");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        XmlWriterEngine writer = new XmlWriterEngine();
        writer.parseTemplateFile(xmlTemplatePath);
        String write = writer.createMsg(inputMap);

        // 存放请求XML,以后记录做分析
        //exchange.setProperty("aopreqXml", write);

        if (systemTag.equals("FBS") || systemTag.equals("FPAY")) {
            if (bodyNameSpaceStr == null) {
                bodyNameSpaceStr = Config.getStr(NAME_SAPCE + "." + exchange.getMethodCode());
            }
            try {
                write = SoapUtils.xmlToSoapXml(write, rootNodeName, bodyNameSpaceStr);
            }
            catch (Exception e) {
                e.printStackTrace();
                throw new EcAopServerBizException("to soapxml failed", e);
            }
            write = prepareEnvlopStr(serviceName, operatorName, bodyNameSpaceStr) + write + SOAP_END;
        }

        // log.info("request xml:" + write);
        return write;
    }

    public String prepareEnvlopStr(String serviceName, String operatorName, String bodyNameSpaceStr) {

        return "<soapenv:Envelope xmlns:soapenv='http://schemas.xmlsoap.org/soap/envelope/' "
                + "xmlns:unib='http://ws.chinaunicom.cn/" + serviceName + "/unibssBody'"
                + " xmlns:unib1='http://ws.chinaunicom.cn/unibssHead' " + "xmlns:" + bodyNameSpaceStr
                + "='http://ws.chinaunicom.cn/" + serviceName + "/unibssBody/" + operatorName + "Req'"
                + " xmlns:unib2='http://ws.chinaunicom.cn/unibssAttached'> " + "<soapenv:Header />" + " <soapenv:Body>";
    }

    @Override
    public void applyParams(String[] params) {
        String param = ParamsUtils.getStr(params, 0, null);
        Matcher matcher = compile.matcher(param);
        xmlTemplatePath = matcher.matches() ? Config.getStr(splitParamToFtl(param).split("@")[0]) + Itarget.REQ_SUFFIX
                : param.split("@")[0];
        systemTag = param.split("@")[1];
        if (param.split("@").length > 2) {
            rootNodeName = param.split("@")[2];
        }
        if (param.split("@").length > 3) {
            bodyNameSpaceStr = param.split("@")[3];
        }

    }

    private String splitParamToFtl(String param) {
        Iterable<String> split = Splitter.on(':').trimResults().omitEmptyStrings().split(param);
        String[] array = Iterables.toArray(split, String.class);
        return array[1];
    }

}
