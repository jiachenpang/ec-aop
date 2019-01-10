package com.ailk.ecaop.common.utils;

import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.n3r.ecaop.core.AopException;
import org.n3r.ecaop.core.exception.request.EcAopRequestBizException;

/**
 * 把不带命名空间和soap头协议的xml 与符合全业务标准的xml进行转化的工具类
 * @author yt
 */
public class SoapUtils {

    /**
     * 将普通xml转成soap报文
     * @param xml
     *            普通的xml字符串
     * @param rootNodeName
     *            xml根节点名称
     * @param bodyNamespaceStr
     *            soap头协议中要求的报文体的命名空间
     * @return
     * @throws Exception
     */
    public static String xmlToSoapXml(String xml, String rootNodeName, String bodyNamespaceStr) throws Exception {
        Document document = SoapUtils.parseText(xml);
        Element root = document.getRootElement();
        if (root.getName().equals(rootNodeName)) {
            root.setName("unib:" + rootNodeName);
        }
        Iterator elementIterator = root.elementIterator();
        while (elementIterator.hasNext()) {
            Element childEle = (Element) elementIterator.next();
            if (childEle.getName().equals("UNI_BSS_HEAD")) {
                childEle.setName("unib1:UNI_BSS_HEAD");
                SoapUtils.toAllChangeName(childEle, "unib1:");
            }
            else if (childEle.getName().equals("UNI_BSS_BODY")) {
                childEle.setName("unib:UNI_BSS_BODY");
                SoapUtils.toAllChangeName(childEle, bodyNamespaceStr + ":");
            }
            else if (childEle.getName().equals("UNI_BSS_ATTACHED")) {
                childEle.setName("unib2:UNI_BSS_ATTACHED");
                SoapUtils.toAllChangeName(childEle, "unib2:");
            }
        }
        String returnXml = document.getRootElement().asXML();
        return returnXml;
    }

    /**
     * 将普通xml转成soap报文CBSS专用
     * @param xml
     *            普通的xml字符串
     * @param rootNodeName
     *            xml根节点名称
     * @param bodyNamespaceStr
     *            soap头协议中要求的报文体的命名空间
     * @return
     * @throws Exception
     */
    public static String xmlToCbssSoapXml(String xml, String rootNodeName, String bodyNamespaceStr,
            String bodyRootNodeName) throws Exception {
        Document document = SoapUtils.parseText(xml);
        Element root = document.getRootElement();
        String serviceName = "";
        String operateName = "";
        Iterator elementIterator = root.elementIterator();
        while (elementIterator.hasNext()) {
            Element childEle = (Element) elementIterator.next();
            if ("".equals(serviceName) || "".equals(operateName)) {
                serviceName = getServiceName(childEle, "SERVICE_NAME");
                operateName = getServiceName(childEle, "OPERATE_NAME") + "Req";
            }
            Map inMap = MapUtils.asMap("serviceName", serviceName, "operateName", operateName, "nameSpace",
                    bodyNamespaceStr);
            if (childEle.getName().equals("UNI_BSS_HEAD")) {
                childEle.setName("m0:UNI_BSS_HEAD xmlns:m0=\"http://ws.chinaunicom.cn/unibssHead\"");
                inMap.put("nameSpace", "m0");
                SoapUtils.toAllChangeName4CB(childEle, inMap, false);
            }
            else if (childEle.getName().equals("UNI_BSS_BODY")) {
                childEle.setName("busi:UNI_BSS_BODY");
                SoapUtils.toAllChangeName4CB(childEle, inMap, true);
            }
            else if (childEle.getName().equals("UNI_BSS_ATTACHED")) {
                childEle.setName("m1:UNI_BSS_ATTACHED xmlns:m1=\"http://ws.chinaunicom.cn/unibssAttached\"");
                inMap.put("nameSpace", "m1");
                SoapUtils.toAllChangeName4CB(childEle, inMap, false);
            }
        }
        if (root.getName().equals(rootNodeName)) {
            root.setName("busi:" + rootNodeName + " xmlns:busi=\"http://ws.chinaunicom.cn/" + serviceName
                    + "/unibssBody\"");
        }
        String returnXml = document.getRootElement().asXML();
        returnXml = returnXml.replaceAll("</busi:" + rootNodeName + " xmlns:busi=\"[a-zA-z]+://[^\\s]*unibssBody\">",
                "</busi:" + rootNodeName + ">");
        returnXml = returnXml.replaceAll("</m0:UNI_BSS_HEAD xmlns:m0=\"[a-zA-z]+://[^\\s]*unibssHead\">",
                "</m0:UNI_BSS_HEAD>");
        returnXml = returnXml.replaceAll("</" + bodyNamespaceStr + ":" + bodyRootNodeName + " xmlns:"
                + bodyNamespaceStr + "=[\"][a-zA-z]+://[^\\s]*>", "</" + bodyNamespaceStr + ":" + bodyRootNodeName
                + "> " + "</busi:UNI_BSS_BODY>");
        returnXml = returnXml.replaceAll("</m1:UNI_BSS_ATTACHED xmlns:m1=\"[a-zA-z]+://[^\\s]*unibssAttached\">",
                "</m1:UNI_BSS_ATTACHED >");
        return returnXml;
    }

    private static String getServiceName(Element e, String getName) {
        Iterator iterator = e.elementIterator();
        while (iterator.hasNext()) {
            Element childEle = (Element) iterator.next();
            if (childEle.getName().equals(getName)) {
                return childEle.getText();
            }
        }
        return "";
    }

    /**
     * 把soap类型的xml解析成不带soap头和命名空间的xml
     * @param soapXml
     *            soap格式的xml字符串
     */
    public static String soapToXml(String soapXml) {

        if (soapXml != null && soapXml.length() > 100) {
            try {
                String spe = "xmlns=[\"][a-zA-z]+://[^\\s]*[\"]";
                String noNameSpeacsoapXml = soapXml.replaceAll(spe, "");
                Document soapDoc = SoapUtils.parseText(noNameSpeacsoapXml);
                Element root = soapDoc.getRootElement();
                root.setName(root.getName().substring(root.getName().indexOf(":") + 1));
                Iterator elementIterator = root.elementIterator();

                while (elementIterator.hasNext()) {
                    Element childElement = (Element) elementIterator.next();
                    SoapUtils.cutAllNamespace(childElement);
                }
                List<Element> elmts = soapDoc.getRootElement().elements("Body");
                // 获取Body的节点 QRY_SIMPLE_CUST_INFO_OUTPUT
                List<Element> elmts1 = elmts.get(0).elements();
                // //获取QRY_SIMPLE_CUST_INFO_OUTPUT中的节点 UNI_BSS_HEAD和UNI_BSS_DOBY
                Document d = SoapUtils.parseText(elmts1.get(0).asXML());
                return d.asXML();

            }
            catch (Exception e) {
                e.printStackTrace();
                throw new EcAopRequestBizException("return xml is not  well-formed");
            }
        }
        return null;
    }

    /**
     * 去掉所有的节点的namespace
     */
    private static void cutAllNamespace(Element childElement) {
        List list = childElement.elements();
        if (list.size() > 0) {
            Iterator elementIterator = childElement.elementIterator();
            while (elementIterator.hasNext()) {
                Element child = (Element) elementIterator.next();
                child.setName(child.getName().substring(child.getName().indexOf(":") + 1));
                SoapUtils.cutAllNamespace(child);
            }
        }

    }

    /**
     * 改变其子节点名称
     * @param childEle
     * @param string
     *            将childEle的所有子节点名称前增加code
     */
    private static void toAllChangeName4CB(Element childEle, Map inMap, boolean isBody) {
        List list = childEle.elements();
        if (list.size() > 0) {
            String commonURL = "=\"http://ws.chinaunicom.cn/";
            String unibssBody = "/unibssBody/";
            Iterator elementIterator = childEle.elementIterator();
            while (elementIterator.hasNext()) {
                Element child = (Element) elementIterator.next();
                Object code = inMap.get("nameSpace");
                if (isBody) {
                    child.setName(code + ":" + child.getName() + " xmlns:" + code + commonURL
                            + inMap.get("serviceName") + unibssBody + inMap.get("operateName") + "\"");
                }
                else {
                    child.setName(code + ":" + child.getName());
                }
                SoapUtils.toAllChangeName4CB(child, inMap, false);
            }
        }
    }

    /**
     * 改变其子节点名称
     * @param childEle
     * @param string
     *            将childEle的所有子节点名称前增加code
     */
    private static void toAllChangeName(Element childEle, String code) {
        List list = childEle.elements();
        if (list.size() > 0) {
            Iterator elementIterator = childEle.elementIterator();
            while (elementIterator.hasNext()) {
                Element child = (Element) elementIterator.next();
                child.setName(code + child.getName());
                SoapUtils.toAllChangeName(child, code);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ORDERSUB_INPUT><UNI_BSS_HEAD><ORIG_DOMAIN>ECIP</ORIG_DOMAIN><SERVICE_NAME>OrdSer</SERVICE_NAME><OPERATE_NAME>orderSub</OPERATE_NAME><ACTION_CODE>0</ACTION_CODE><ACTION_RELATION>0</ACTION_RELATION><ROUTING><ROUTE_TYPE>00</ROUTE_TYPE><ROUTE_VALUE>76</ROUTE_VALUE></ROUTING><PROC_ID>7617120115042001</PROC_ID><TRANS_IDO>7617120115042001</TRANS_IDO><TRANS_IDH>20171201150420</TRANS_IDH><PROCESS_TIME>20171201150420</PROCESS_TIME><COM_BUS_INFO><OPER_ID>hasc-menghy8</OPER_ID><PROVINCE_CODE>76</PROVINCE_CODE><EPARCHY_CODE>768</EPARCHY_CODE><CITY_CODE>762127</CITY_CODE><CHANNEL_ID>76b4ele</CHANNEL_ID><CHANNEL_TYPE>2020100</CHANNEL_TYPE><ACCESS_TYPE>09</ACCESS_TYPE><ORDER_TYPE>00</ORDER_TYPE></COM_BUS_INFO><SP_RESERVE><TRANS_IDC>7617120115042001</TRANS_IDC><CUTOFFDAY>20171201</CUTOFFDAY><OSNDUNS>0002</OSNDUNS><HSNDUNS>0002</HSNDUNS><CONV_ID>20171201150420</CONV_ID></SP_RESERVE><TEST_FLAG>0</TEST_FLAG><MSG_SENDER>9801</MSG_SENDER><MSG_RECEIVER>9800</MSG_RECEIVER></UNI_BSS_HEAD><UNI_BSS_BODY><ORDERSUB_REQ><PROVINCE_ORDER_ID>7617120179112428</PROVINCE_ORDER_ID><ORDER_ID>7617120179112428</ORDER_ID><OPERATION_TYPE>01</OPERATION_TYPE><SEND_TYPE_CODE>0</SEND_TYPE_CODE><NOTE_TYPE>1</NOTE_TYPE><SUB_ORDERSUB_REQ><SUB_PROVINCE_ORDER_ID>7617120179112428</SUB_PROVINCE_ORDER_ID><SUB_ORDER_ID>7617120179112428</SUB_ORDER_ID></SUB_ORDERSUB_REQ><SUB_ORDERSUB_REQ><SUB_PROVINCE_ORDER_ID>7617120179112430</SUB_PROVINCE_ORDER_ID><SUB_ORDER_ID>7617120179112430</SUB_ORDER_ID><FEE_INFO><FEE_MODE>0</FEE_MODE><FEE_TYPE_MODE>8239</FEE_TYPE_MODE><FEE_TYPE_NAME>[营业费用]ONU终端终端销售-低档资费</FEE_TYPE_NAME><OPERATE_TYPE>1</OPERATE_TYPE><OLDFEE>10000</OLDFEE><DERATE_FEE>4000</DERATE_FEE><DERATE_REMARK>系统减免</DERATE_REMARK><FEE>6000</FEE><PAY_TAG>1</PAY_TAG><CALCULATE_TAG>N</CALCULATE_TAG><CALCULATE_ID>8171201150357116</CALCULATE_ID><CALCULATE_DATE>20171201150357</CALCULATE_DATE></FEE_INFO><FEE_INFO><FEE_MODE>0</FEE_MODE><FEE_TYPE_MODE>8227</FEE_TYPE_MODE><FEE_TYPE_NAME>[营业费用]装移机手续费</FEE_TYPE_NAME><OPERATE_TYPE>1</OPERATE_TYPE><OLDFEE>800</OLDFEE><DERATE_FEE>800</DERATE_FEE><DERATE_REMARK>系统减免</DERATE_REMARK><FEE>0</FEE><PAY_TAG>1</PAY_TAG><CALCULATE_TAG>N</CALCULATE_TAG><CALCULATE_ID>8171201150357116</CALCULATE_ID><CALCULATE_DATE>20171201150357</CALCULATE_DATE></FEE_INFO><FEE_INFO><FEE_MODE>0</FEE_MODE><FEE_TYPE_MODE>8236</FEE_TYPE_MODE><FEE_TYPE_NAME>[营业费用]调测费</FEE_TYPE_NAME><OPERATE_TYPE>1</OPERATE_TYPE><OLDFEE>5000</OLDFEE><DERATE_FEE>5000</DERATE_FEE><DERATE_REMARK>系统减&#8;免</DERATE_REMARK><FEE>0</FEE><PAY_TAG>1</PAY_TAG><CALCULATE_TAG>N</CALCULATE_TAG><CALCULATE_ID>8171201150357116</CALCULATE_ID><CALCULATE_DATE>20171201150357</CALCULATE_DATE></FEE_INFO><FEE_INFO><FEE_MODE>2</FEE_MODE><FEE_TYPE_MODE>400000</FEE_TYPE_MODE><FEE_TYPE_NAME>[预存]宽带包一年-包月费)</FEE_TYPE_NAME><OPERATE_TYPE>1</OPERATE_TYPE><OLDFEE>24000</OLDFEE><DERATE_FEE>0</DERATE_FEE><DERATE_REMARK>无</DERATE_REMARK><FEE>24000</FEE><PAY_TAG>1</PAY_TAG><CALCULATE_TAG>N</CALCULATE_TAG><CALCULATE_ID>8171201150357117</CALCULATE_ID><CALCULATE_DATE>20171201150357</CALCULATE_DATE></FEE_INFO></SUB_ORDERSUB_REQ><SUB_ORDERSUB_REQ><SUB_PROVINCE_ORDER_ID>7617120179112616</SUB_PROVINCE_ORDER_ID><SUB_ORDER_ID>7617120179112616</SUB_ORDER_ID><FEE_INFO><FEE_MODE>0</FEE_MODE><FEE_TYPE_MODE>1010</FEE_TYPE_MODE><FEE_TYPE_NAME>[营业费用]SIM卡/USIM卡费</FEE_TYPE_NAME><OPERATE_TYPE>1</OPERATE_TYPE><OLDFEE>3000</OLDFEE><DERATE_FEE>3000</DERATE_FEE><DERATE_REMARK>系统自动减免</DERATE_REMARK><FEE>0</FEE><PAY_TAG>1</PAY_TAG><CALCULATE_TAG>N</CALCULATE_TAG><CALCULATE_ID>8171201150357117</CALCULATE_ID><CALCULATE_DATE>20171201150357</CALCULATE_DATE></FEE_INFO><FEE_INFO><FEE_MODE>2</FEE_MODE><FEE_TYPE_MODE>100005</FEE_TYPE_MODE><FEE_TYPE_NAME>[预存]营业厅收入(营业缴费)_普通预存款(不可清退))</FEE_TYPE_NAME><OPERATE_TYPE>1</OPERATE_TYPE><OLDFEE>10000</OLDFEE><DERATE_FEE>0</DERATE_FEE><DERATE_REMARK>无</DERATE_REMARK><FEE>10000</FEE><PAY_TAG>1</PAY_TAG><CALCULATE_TAG>N</CALCULATE_TAG><CALCULATE_ID>8171201150357117</CALCULATE_ID><CALCULATE_DATE>20171201150357</CALCULATE_DATE></FEE_INFO></SUB_ORDERSUB_REQ><TOTAL_FEE>40000</TOTAL_FEE><CANCLE_TOTAL_FEE>0</CANCLE_TOTAL_FEE><PAY_INFO><SUB_PROVINCE_ORDER_ID>7617120179112428</SUB_PROVINCE_ORDER_ID><PAY_TYPE>00</PAY_TYPE><PAY_MONEY>0</PAY_MONEY></PAY_INFO><PAY_INFO><SUB_PROVINCE_ORDER_ID>7617120179112430</SUB_PROVINCE_ORDER_ID><PAY_TYPE>00</PAY_TYPE><PAY_MONEY>30000</PAY_MONEY></PAY_INFO><PAY_INFO><SUB_PROVINCE_ORDER_ID>7617120179112616</SUB_PROVINCE_ORDER_ID><PAY_TYPE>00</PAY_TYPE><PAY_MONEY>10000</PAY_MONEY></PAY_INFO></ORDERSUB_REQ></UNI_BSS_BODY><UNI_BSS_ATTACHED><MEDIA_INFO>?</MEDIA_INFO></UNI_BSS_ATTACHED></ORDERSUB_INPUT>";
        parseText(xml);
        System.out.println();
    }

    /**
     * 解析字符串成Document
     * @param text
     *            XML串
     * @return org.dom4j.Document
     * @throws MalformedURLException
     * @throws DocumentException
     */
    public static Document parseText(String text) throws Exception {
        if (text == null) {
            throw new IllegalArgumentException("解析串为NULL!");
        }
        Document document = null;
        try {
            document = DocumentHelper.parseText(text);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new AopException("detailXML格式不对!", e);
        }
        if (document == null) {
            throw new IllegalArgumentException("XML格式不对!");
        }
        return document;
    }

}
