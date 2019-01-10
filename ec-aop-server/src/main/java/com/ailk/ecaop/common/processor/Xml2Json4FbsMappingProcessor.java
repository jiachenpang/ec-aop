package com.ailk.ecaop.common.processor;

import java.util.HashMap;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.n3r.config.Config;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.DefaultExchange;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.log.domain.EcAopReqLog;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.util.EcharsUtils;
import org.n3r.ecaop.eface.engine.EfaceEngine;
import org.n3r.ecaop.eface.engine.XmlBaseEngine;

import com.ailk.ecaop.common.utils.DealInterfaceUtils;
import com.ailk.ecaop.common.utils.SoapUtils;

@EcRocTag("Xml2JsonMapper4Fbs")
public class Xml2Json4FbsMappingProcessor extends BaseAopProcessor implements ParamsAppliable {

    private String xmlTemplatePath;
    private String xmlBodyContent;

    @Override
    public void process(Exchange exchange) throws Exception {
        String downApp = EcharsUtils.getDownAppUseIP(exchange);
        long now = System.currentTimeMillis();
        long startTime = System.currentTimeMillis();

        EcAopReqLog reqLog = exchange.getProperty(Exchange.REQLOG, EcAopReqLog.class);
        Throwable ex = exchange.getException();
        if (ex != null) {
            ex.printStackTrace();
            throw new EcAopServerBizException("9999", "系统-" + downApp + "调用接口异常:" + ex.getMessage());
        }

        String body = exchange.getOut().getBody().toString();
        // 把soap协议的xml格式去掉namespace，并且获取到包含UNI_BSS_HEAD UNI_BSS_BODY的报文
        body = SoapUtils.soapToXml(body);

        Document doc = DocumentHelper.parseText(body);
        Element root = doc.getRootElement();

        // 分别获取头和体，获取头主要来判断是否成功
        Element uniBssHead = root.element("UNI_BSS_HEAD");
        Element uniBssBody = root.element("UNI_BSS_BODY");

        if (null == uniBssHead || null == uniBssBody) {
            int length = Math.min(200, body.length());
            throw new EcAopServerBizException("9999", "下游系统-" + downApp + "接口 返回报文格式有误:" + body.substring(0, length));
        }

        // 定义操作名
        String operatorName = "";
        if (null != uniBssHead.element("OPERATE_NAME")) {
            operatorName = uniBssHead.element("OPERATE_NAME").getText();
        }
        String transIDO = uniBssHead.element("TRANS_IDO").getText();
        Element response = null;

        // 获取response节点
        response = uniBssHead.element("RESPONSE");
        if (null == response) {
            throw new EcAopServerSysException("9999", "下游系统-" + downApp + "报文异常，未返回应答;TRANSIDO为:" + transIDO);
        }

        // 根据头信息是返回detail 和 code还是商城需要的信息
        Map tempMap = new HashMap();
        tempMap.put("operatorName", operatorName);
        tempMap.put("transIDO", transIDO);
        tempMap.put("methodCode", exchange.getMethodCode());
        tempMap.put("appCode", exchange.getAppCode());
        tempMap.put("downApp", downApp);
        Map dataMap = processAsHeadCode(uniBssBody, response, tempMap, reqLog.getApptx());
        exchange.getOut().setBody(dataMap);
    }

    public static void main(String[] args) throws Exception {
        String body = "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><m:QRY_CP_TRADE_STATUS_BY_ORDERID_OUTPUT xmlns:m=\"http://ws.chinaunicom.cn/JSQryCPDataSer/unibssBody\"><h:UNI_BSS_HEAD xmlns:h=\"http://ws.chinaunicom.cn/unibssHead\"><h:ORIG_DOMAIN>UESS</h:ORIG_DOMAIN><h:SERVICE_NAME>JSQryCPDataSer</h:SERVICE_NAME><h:OPERATE_NAME>qryCPTradeStatusByOrderId</h:OPERATE_NAME><h:ACTION_CODE>1</h:ACTION_CODE><h:ACTION_RELATION>0</h:ACTION_RELATION><h:ROUTING><h:ROUTE_TYPE>00</h:ROUTE_TYPE><h:ROUTE_VALUE>34</h:ROUTE_VALUE></h:ROUTING><h:PROC_ID>3418102316323038</h:PROC_ID><h:TRANS_IDO>3418102316323038</h:TRANS_IDO><h:TRANS_IDH>20181023163230</h:TRANS_IDH><h:PROCESS_TIME>20181023163230</h:PROCESS_TIME><h:RESPONSE><h:RSP_TYPE>2</h:RSP_TYPE><h:RSP_CODE>9999</h:RSP_CODE><h:RSP_DESC>无数据</h:RSP_DESC></h:RESPONSE><h:COM_BUS_INFO><h:OPER_ID>A0009194</h:OPER_ID><h:PROVINCE_CODE>34</h:PROVINCE_CODE><h:EPARCHY_CODE>340</h:EPARCHY_CODE><h:CITY_CODE>340338</h:CITY_CODE><h:CHANNEL_ID>34a0563</h:CHANNEL_ID><h:CHANNEL_TYPE>1010200</h:CHANNEL_TYPE><h:ACCESS_TYPE>00</h:ACCESS_TYPE><h:ORDER_TYPE>01</h:ORDER_TYPE></h:COM_BUS_INFO><h:SP_RESERVE><h:TRANS_IDC>3418102316323038</h:TRANS_IDC><h:CUTOFFDAY>20181023</h:CUTOFFDAY><h:OSNDUNS>0400</h:OSNDUNS><h:HSNDUNS>9900</h:HSNDUNS><h:CONV_ID>20181023163230</h:CONV_ID></h:SP_RESERVE><h:TEST_FLAG>0</h:TEST_FLAG><h:MSG_SENDER>0400</h:MSG_SENDER><h:MSG_RECEIVER>1000</h:MSG_RECEIVER></h:UNI_BSS_HEAD><m:UNI_BSS_BODY><n-402300238:QRY_CP_TRADE_STATUS_BY_ORDERID_RSP xmlns:n-402300238=\"http://ws.chinaunicom.cn/JSQryCPDataSer/unibssBody/qryCPTradeStatusByOrderIdRsp\"><n-402300238:RESP_CODE>8888</n-402300238:RESP_CODE><n-402300238:RESP_DESC>无数据</n-402300238:RESP_DESC></n-402300238:QRY_CP_TRADE_STATUS_BY_ORDERID_RSP></m:UNI_BSS_BODY><a:UNI_BSS_ATTACHED xmlns:a=\"http://ws.chinaunicom.cn/unibssAttached\"/></m:QRY_CP_TRADE_STATUS_BY_ORDERID_OUTPUT></SOAP-ENV:Body></SOAP-ENV:Envelope>";
        Exchange exchange = new DefaultExchange();
        Message out = new DefaultMessage();
        out.setBody(body);
        exchange.setOut(out);
        exchange.setProperty(Exchange.REQLOG, new EcAopReqLog());
        Xml2Json4FbsMappingProcessor xml = new Xml2Json4FbsMappingProcessor();
        xml.xmlTemplatePath = "com/ailk/ecaop/biz/template/cp/QryCPTradeStatusByOrderId_Rsp.xml";
        xml.process(exchange);
    }

    private Map processAsHeadCode(Element uniBssBody, Element response, Map inMap, String apptx) {
        long now = System.currentTimeMillis();
        String headCode = response.element("RSP_CODE").getText();
        Map dataMap = new HashMap();
        DealInterfaceUtils dif = new DealInterfaceUtils();
        if ("0000".equals(headCode) || "9999".equals(headCode)) {
            xmlBodyContent = uniBssBody.asXML();
            try {
                XmlBaseEngine reader = new EfaceEngine().getReaderEngine();
                reader.parseTemplateFile(xmlTemplatePath);
                dataMap = (Map) reader.createMap(xmlBodyContent);
            }
            catch (Exception e) {
                throw new EcAopServerBizException("9999", dif.dealRespDesc4FBS(inMap, e.getMessage()));
            }
            String code = (String) dataMap.get("code");
            if (!"0000".equals(code) && !"0".equals(code)) {
                throw new EcAopServerBizException(code, dif.dealRespDesc4FBS(inMap, dataMap.get("detail")));
            }
            if (!"cbrc|pocs|qbnq".contains((String) inMap.get("methodCode"))) {
                dataMap.remove("code");
                dataMap.remove("detail");
            }
        }
        else {
            String rspDesc = response.element("RSP_DESC").getText();
            if ("ecsb".equals(inMap.get("appCode")) && "ccpq|mcps|".contains((String) inMap.get("methodCode"))
                    && "6666|7777".contains(headCode)) {
                if ("6666".equals(headCode)) {
                    rspDesc = "此用户为23转4预生效用户，该时间段不能办理该业务";
                }
            }
            else {
                headCode = dif.dealTimeOutCode(headCode);
            }

            throw new EcAopServerSysException(headCode, dif.dealRespDesc4FBS(inMap, rspDesc));
        }
        return dataMap;
    }

    @Override
    public void applyParams(String[] params) {
        long now = System.currentTimeMillis();
        this.xmlTemplatePath = Config.getStr(params[0]) + "_Rsp.xml";

    }
}
