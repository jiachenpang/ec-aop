package com.ailk.ecaop.biz.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 免填单查询<br/>
 * 
 * @author: crane[yuanxingnepu@gmail.com]
 * @date: 2017-2-17 下午02:04:20
 */
@EcRocTag("QueryCommDoc")
@SuppressWarnings(value = { "unchecked", "rawtypes", "serial" })
public class QueryCommDocProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.trades.qcmd.ParametersMapping" };
    private final ParametersMappingProcessor[] PMP = new ParametersMappingProcessor[PARAM_ARRAY.length];

    @Override
    public void process(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        dealReq(exchange);

        lan.preData(PMP[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.QueryCommDocSer");
        // CallEngine.wsCall(exchange, "MockProcessor");
        lan.xml2Json("ecaop.trades.qcmd.template", exchange);

        dealRsp(exchange);
    }

    /**
     * 处理请求
     *
     * @author: YangZG
     * @date: 2017-05-09 上午10:42:21
     */
    private void dealReq(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        final Map msg = (Map) body.get("msg");

        msg.put("serviceProvideDomain", "0");

        List<Map> paraList = new ArrayList<Map>();
        // 根据qrySysType判断走单业务还是融合
        String qrySysType = msg.get("qrySysType").toString();

        paraList.add(new HashMap() {

            {
                put("PARA_ID", "DESIGN_PAPER");
                put("PARA_VALUE", "1");
            }
        });
        paraList.add(new HashMap() {

            {
                put("PARA_ID", "TEMPLATE_KIND");
                put("PARA_VALUE", msg.get("templateKind"));
            }
        });
        paraList.add(new HashMap() {

            {
                put("PARA_ID", "SYS_TYPE");
                put("PARA_VALUE", "AOP");
            }
        });
        if ("1".equals(qrySysType)) {

            paraList.add(new HashMap() {

                {
                    put("PARA_ID", "QUERYTYPE");
                    put("PARA_VALUE", "1");
                }
            });
            paraList.add(new HashMap() {

                {
                    put("PARA_ID", "IN_TAG");
                    put("PARA_VALUE", "1");
                }
            });
            paraList.add(new HashMap() {

                {
                    put("PARA_ID", "TRADE_ID");
                    put("PARA_VALUE", msg.get("tradeId"));
                }
            });
            paraList.add(new HashMap() {

                {
                    put("PARA_ID", "LCU_NAME");
                    put("PARA_VALUE", "TCS_GeneTradeReceiptInfoOnly");
                }
            });
        } else if ("2".equals(qrySysType)) {
            paraList.add(new HashMap() {

                {
                    put("PARA_ID", "PARA_CODE1");
                    put("PARA_VALUE", "3");
                }
            });
            paraList.add(new HashMap() {

                {
                    put("PARA_ID", "SUBSCRIBE_ID");
                    put("PARA_VALUE", msg.get("tradeId"));
                }
            });
            paraList.add(new HashMap() {

                {
                    put("PARA_ID", "LCU_NAME");
                    put("PARA_VALUE", "TCS_GeneTradePrintCompAll");
                }
            });
        }

        msg.put("para", paraList);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
    }

    /**
     * 处理返回
     *
     * @author: YangZG
     * @date: 2017-05-09 上午10:42:18
     */
    private void dealRsp(Exchange exchange) {
        Map body = exchange.getOut().getBody(Map.class);

        if (IsEmptyUtils.isEmpty(body)) {
            throw new EcAopServerBizException("9999", "CBSS免填单返回结果为空");
        }
        if ("0000".equals(body.get("code")) || body.get("code") == null) {
            List<Map> paraList = new ArrayList<Map>();
            if (!IsEmptyUtils.isEmpty(body.get("para"))) {
                if (body.get("para") instanceof Map) {
                    paraList.add((Map) body.get("para"));
                } else {
                    paraList = (List) body.get("para");
                }
                List<Map> itemList = new ArrayList<Map>();
                for (Map paraMap : paraList) {
                    itemList.addAll((List<Map>) paraMap.get("ITEM"));
                }
                // 存储返回回来的RECEIPT_INFO1、RECEIPT_INFO2、RECEIPT_INFO3、RECEIPT_INFO4四个节点的内容，为后期转换xml做准备。
                StringBuilder infoStr = new StringBuilder();
                for (int i = 1; i <= 20; i++) {
                    for (Map itemMap : itemList) {
                        String tempStr = (String) itemMap.get("PARA_ID");

                        if (("RECEIPT_INFO" + i + "").equals(tempStr)) {
                            infoStr.append(itemMap.get("PARA_VALUE"));
                        }
                    }
                }
                if (null != infoStr && 0 != infoStr.length()) {
                    // 字符串转换
                    String infoXmlTemp = infoStr.toString();
                    String infoXml = infoXmlTemp.replace("lt;", "<").replace("gt;", ">").replace("zt;", "/").replace("#$", " ");

                    JSONObject msgJson = new JSONObject();
                    try {
                        msgJson = documentToJSONObject(infoXml);
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new EcAopServerBizException("9999", "XML转JSON错误，请检查返回！");
                    }
                    JSONObject newJson = JSONObject.parseObject(msgJson.toString().replace("[]", "\"\""));// 之前的xml2Json方法会将空转成[]，现在的方法不会这样，暂时保留此行代码

                    Map bodyNew = new HashMap();
                    // 正式进行返回数据的装载
                    bodyNew.put("systemType", newJson.get("SYSTEM_TYPE"));
                    bodyNew.put("provinceCode", newJson.get("PROVINCE_CODE"));
                    bodyNew.put("orderType", newJson.get("ORDER_TYPE"));
                    bodyNew.put("eparchyCode", newJson.get("EPARCHY_CODE"));
                    bodyNew.put("orderId", newJson.get("ORDER_ID"));

                    // 拼装custInfo,并将身份证转码处理
                    JSONObject custJson = new JSONObject();
                    List custInfoList = new ArrayList<Map>();
                    if (newJson.get("CUST_INFO") instanceof JSONObject) {
                        custJson = (JSONObject) newJson.get("CUST_INFO");
                        String cetrType = custJson.get("CERT_TYPE").toString();
                        custJson.put("CERT_TYPE", certTypeCbss2Mall(cetrType));
                        custInfoList.add(custJson);
                    } else {
                        List custInfoListTemp = newJson.getJSONArray("CUST_INFO");
                        if (null != custInfoListTemp && 0 != custInfoListTemp.size()) {
                            for (int i = 0; i < custInfoListTemp.size(); i++) {
                                Map busiLv1Map = (Map) custInfoListTemp.get(i);
                                String cetrType = busiLv1Map.get("CERT_TYPE").toString();
                                busiLv1Map.put("CERT_TYPE", certTypeCbss2Mall(cetrType));
                                custInfoList.add(custInfoListTemp.get(i));
                            }
                        }
                    }
                    JSONArray custInfoJson = JSONArray.parseArray(lineToHump(custInfoList.toString()));
                    List custInfoListTemp = JSONArray.parseArray(custInfoJson.toString());
                    bodyNew.put("custInfo", custInfoListTemp);

                    // busiInfo信息拼装
                    JSONObject bodyInfo = new JSONObject();
                    if (newJson.get("BODY_INFO") instanceof JSONObject) {
                        bodyInfo = (JSONObject) newJson.get("BODY_INFO");
                    } else {
                        List bodyInfoTemp = newJson.getJSONArray("BODY_INFO");
                        bodyInfo = (JSONObject) bodyInfoTemp.get(0);
                    }

                    JSONObject busiInfo = new JSONObject();
                    List<Map> busiLv1 = new ArrayList<Map>();
                    List<Map> lv1 = new ArrayList<Map>();
                    JSONObject busiList = new JSONObject();
                    List list_LV2 = new ArrayList();
                    if (bodyInfo.get("BUSI_LIST") instanceof JSONObject) {
                        busiList = (JSONObject) bodyInfo.get("BUSI_LIST");
                    } else {
                        List busiListTemp = bodyInfo.getJSONArray("BUSI_LIST");
                        busiList = (JSONObject) busiListTemp.get(0);
                    }
                    if (busiList.get("BUSI_LV1") instanceof JSONObject) {
                        busiLv1.add((JSONObject) busiList.get("BUSI_LV1"));
                    } else {
                        busiLv1 = (List) busiList.get("BUSI_LV1");
                    }
                    // 遍历LV1，取出其中的LV2放入BUSI_LIST，并去除LV1中无关项后将LV1放入BUSI_LIST
                    if (null != busiLv1 && 0 != busiLv1.size()) {
                        for (int i = 0; i < busiLv1.size(); i++) {
                            Map busiLv1Map = busiLv1.get(i);
                            if (null != busiLv1Map.get("BUSI_LV2")) {
                                if (busiLv1Map.get("BUSI_LV2") instanceof JSONArray) {
                                    List<Map> lv2List = (List<Map>) busiLv1Map.get("BUSI_LV2");
                                    for (int j = 0; j < lv2List.size(); j++) {
                                        list_LV2.add(lv2List.get(j));
                                    }
                                } else {
                                    JSONObject objLv2 = (JSONObject) busiLv1Map.get("BUSI_LV2");
                                    list_LV2.add(objLv2);
                                }
                            }
                            busiList.put("BUSI_LV2", list_LV2);
                            // 去除LV1中与规范无关项
                            if (busiLv1Map.containsKey("BUSI_LV2") || busiLv1Map.containsKey("ACTIVITY_INFO") || busiLv1Map.containsKey("BUSI_CUST_NAME") || busiLv1Map.containsKey("BUSI_PARAM1") || busiLv1Map.containsKey("BUSI_PARAM2") || busiLv1Map.containsKey("BUSI_PARAM3") || busiLv1Map.containsKey("BUSI_PARAM4") || busiLv1Map.containsKey("NET_TYPE") || busiLv1Map.containsKey("SPECIAL_NO_BUSINESS_AMOUNT") || busiLv1Map.containsKey("SPECIAL_NO_EFFECT_DATE") || busiLv1Map.containsKey("SPECIAL_NO_EXIST") || busiLv1Map.containsKey("SPECIAL_NO_INVALID_DATE") || busiLv1Map.containsKey("SPECIAL_NO_MINIMUM") || busiLv1Map.containsKey("SPECIAL_NO_PRESTORE")) {
                                busiLv1Map.remove("BUSI_LV2");
                                busiLv1Map.remove("ACTIVITY_INFO");
                                busiLv1Map.remove("BUSI_CUST_NAME");
                                busiLv1Map.remove("BUSI_PARAM1");
                                busiLv1Map.remove("BUSI_PARAM2");
                                busiLv1Map.remove("BUSI_PARAM3");
                                busiLv1Map.remove("BUSI_PARAM4");
                                busiLv1Map.remove("NET_TYPE");
                                busiLv1Map.remove("SPECIAL_NO_BUSINESS_AMOUNT");
                                busiLv1Map.remove("SPECIAL_NO_EFFECT_DATE");
                                busiLv1Map.remove("SPECIAL_NO_EXIST");
                                busiLv1Map.remove("SPECIAL_NO_INVALID_DATE");
                                busiLv1Map.remove("SPECIAL_NO_MINIMUM");
                                busiLv1Map.remove("SPECIAL_NO_PRESTORE");
                            }
                            lv1.add(busiLv1Map);
                        }
                    }
                    busiList.put("BUSI_LV1", lv1);
                    busiInfo.put("BUSI_LIST", busiList);
                    List list_acct = new ArrayList();
                    List list_agent = new ArrayList();
                    List list_assure = new ArrayList();
                    Map body_map = JSON.parseObject(bodyInfo.toString());
                    list_acct = (List) body_map.get("ACCT_INFO");
                    list_agent = (List) body_map.get("AGENT_INFO");
                    list_assure = (List) body_map.get("ASSURE_INFO");
                    busiInfo.put("ACCT_INFO", list_acct);
                    busiInfo.put("OP_INFO", body_map.get("OP_INFO"));
                    busiInfo.put("BUSI_NOTE", body_map.get("BUSI_NOTE"));
                    busiInfo.put("AGENT_INFO", list_agent);
                    busiInfo.put("ASSURE_INFO", list_assure);
                    String busiString = lineToHump(busiInfo.toString());
                    Map bodyNewTemp = JSON.parseObject(busiString);
                    bodyNew.put("busiInfo", bodyNewTemp);
                    bodyNew.put("orgId", newJson.get("ORG_ID"));
                    bodyNew.put("orgInfo", newJson.get("ORG_INFO"));
                    bodyNew.put("workerNo", newJson.get("WORKER_NO"));
                    bodyNew.put("workerName", newJson.get("WORKER_NAME"));
                    exchange.getOut().setBody(bodyNew);
                } else {
                    throw new EcAopServerBizException("9999", "CB未返回免填单内容，请检查请求参数是否正确！！");
                }
            }
        } else {
            throw new EcAopServerBizException("9999", "号卡中心返回其他错误," + body.get("detail") + "");
        }
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            PMP[i] = new ParametersMappingProcessor();
            PMP[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

    /**
     * 将字符串转成「驼峰命名法」样式
     * 
     * @author: YangZG
     * @date: 2017-05-10 15:29:50
     */
    private static Pattern linePattern = Pattern.compile("_(\\w)");

    public static String lineToHump(String str) {
        str = str.toLowerCase();
        Matcher matcher = linePattern.matcher(str);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, matcher.group(1).toUpperCase());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 证件类型转换
     * 
     * @author: YangZG
     * @date: 2017-05-10 15:29:50
     */
    private static String[] cert_Cbss = {"01", "02", "05", "06", "08", "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "F", "G"};
    private static String[] cert_Mall = {"02", "08", "04", "07", "09", "12", "10", "11", "01", "99", "13", "14", "15", "16", "17", "18", "34", "35"};

    public static String certTypeCbss2Mall(String certType) {

        // 默认值为0,即:证件类型为18位身份证
        int index = 0;
        for (int i = 0; i < cert_Cbss.length; i++) {
            if (certType.equals(cert_Cbss[i])) {
                index = i;
                break;
            }
        }
        return cert_Mall[index];
    }

    /**
     * dom4j xml2Json
     * 
     * @author: YangZG
     * @date: 2017-07-08 14:29:32
     */
    public static Document strToDocument(String xml) throws DocumentException {
        return DocumentHelper.parseText(xml);
    }

    public static JSONObject documentToJSONObject(String xml) throws DocumentException {
        return elementToJSONObject(strToDocument(xml).getRootElement());
    }

    public static JSONObject elementToJSONObject(Element node) {
        JSONObject result = new JSONObject();
        // 当前节点的名称、文本内容和属性
        List<Attribute> listAttr = node.attributes();// 当前节点的所有属性的list
        for (Attribute attr : listAttr) {// 遍历当前节点的所有属性
            result.put(attr.getName(), attr.getValue());
        }
        // 递归遍历当前节点所有的子节点
        List<Element> listElement = node.elements();// 所有一级子节点的list
        if (!listElement.isEmpty()) {
            for (Element e : listElement) {// 遍历所有一级子节点
                if (e.attributes().isEmpty() && e.elements().isEmpty()) // 判断一级节点是否有属性和子节点
                    result.put(e.getName(), e.getTextTrim());// 沒有则将当前节点作为上级节点的属性对待
                else {
                    if (!result.containsKey(e.getName())) // 判断父节点是否存在该一级节点名称的属性
                        result.put(e.getName(), new JSONArray());// 没有则创建
                    ((JSONArray) result.get(e.getName())).add(elementToJSONObject(e));// 将该一级节点放入该节点名称的属性对应的值中
                }
            }
        }
        return result;
    }

}