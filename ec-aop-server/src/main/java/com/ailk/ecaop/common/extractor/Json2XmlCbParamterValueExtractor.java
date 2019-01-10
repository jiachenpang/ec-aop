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

@EcRocTag("json2xmlcb")
public class Json2XmlCbParamterValueExtractor implements ParameterValueExtractor, ParamsAppliable {

    private final Pattern compile = Pattern.compile("^json2xmlConfigKey:.+$");
    private String xmlTemplatePath;
    private String systemTag;
    private String bodyNameSpaceStr;
    private String rootNodeName;
    private String bodyRootNodeName;
    private final String SOAP_END = "</SOAP-ENV:Body></SOAP-ENV:Envelope>";
    private final String NAME_SAPCE = "ecaop.soap.bodyns";

    private final Logger log = LoggerFactory.getLogger(Json2XmlCbParamterValueExtractor.class);

    @Override
    public Object extract(Exchange exchange) {
        Long start = System.currentTimeMillis();

        Map<String, Object> root = exchange.getIn().getBody(Map.class);

        String templateName = "xmlTemplte." + systemTag;

        String template = "";

        // log.info("START TO USE ONS BAO WEN:!!!!!!!!!!!!!!!!!!!!!!!!!!!");

        if (systemTag.equals(Itarget.SYSTEM_TAG_ONS)) {
            template = Itarget.REQ_JSON_ONS;

        }
        else if (systemTag.equals(Itarget.SYSTEM_TAG_3GE)) {
            template = Itarget.REQ_JSON;

        }
        else if (systemTag.equals(Itarget.SYSTEM_TAG_FPAY)) {
            template = Itarget.REQ_JSON_FPAY;

        }
        else if (systemTag.equals(Itarget.SYSTEM_TAG_FBS)) {
            template = Itarget.REQ_JSON_FBS;

        }
        else if (systemTag.equals(Itarget.SYSTEM_TAG_FBS2)) {
            template = Itarget.REQ_JSON_FBS2;

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

        if ("FBS2|FPAY|FBS".contains(systemTag)) {
            try {
                write = SoapUtils.xmlToCbssSoapXml(write, rootNodeName, bodyNameSpaceStr, bodyRootNodeName);
            }
            catch (Exception e) {
                e.printStackTrace();
                throw new EcAopServerBizException("to soapxml failed", e);
            }
            write = "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/>"
                    + "<SOAP-ENV:Body>"
                    + write + SOAP_END;
        }
        System.out.println("Json2XmlCbParamterValueExtractor.extract cost:" + (System.currentTimeMillis() - start));
        return write;
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
        if (param.split("@").length > 4) {
            bodyRootNodeName = param.split("@")[4];
        }
    }

    private String splitParamToFtl(String param) {
        Iterable<String> split = Splitter.on(':').trimResults().omitEmptyStrings().split(param);
        String[] array = Iterables.toArray(split, String.class);
        return array[1];
    }

}
