/*********************************************************************************************************************
 *********************************** ————文————档————说————明————********************************************************
 * @author Steven Zhang
 * @since 1.0
 * @version 1.0
 * @date 2013-10-24 15:30
 *       <ol>
 *       <li>此Processor用于处理大多数调枢纽的通用接口</li>
 *       <li>此Processor支持的接口包括客户资料校验，北六终端查询，商城卡数据查询</li>
 *       <li>报文头返回0000和9999时，均会继续判断报文体的应答编码，目的：获取报文体中更为详细的错误描述</li>
 *       </ol>
 *********************************************************************************************************************/
package com.ailk.ecaop.common.processor;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.DefaultExchange;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.log.domain.EcAopReqLog;

import com.ailk.ecaop.common.utils.DealInterfaceUtils;

@EcRocTag("Xml2JsonMapper")
public class Xml2JsonMappingProcessor extends BaseXml2JsonMapping {

    String[] params = { in };

    public static void main(String[] args) throws Exception {
        String body = "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><m:QRY_CP_TRADE_STATUS_BY_ORDERID_OUTPUT xmlns:m=\"http://ws.chinaunicom.cn/JSQryCPDataSer/unibssBody\"><h:UNI_BSS_HEAD xmlns:h=\"http://ws.chinaunicom.cn/unibssHead\"><h:ORIG_DOMAIN>UESS</h:ORIG_DOMAIN><h:SERVICE_NAME>JSQryCPDataSer</h:SERVICE_NAME><h:OPERATE_NAME>qryCPTradeStatusByOrderId</h:OPERATE_NAME><h:ACTION_CODE>1</h:ACTION_CODE><h:ACTION_RELATION>0</h:ACTION_RELATION><h:ROUTING><h:ROUTE_TYPE>00</h:ROUTE_TYPE><h:ROUTE_VALUE>34</h:ROUTE_VALUE></h:ROUTING><h:PROC_ID>3418102316323038</h:PROC_ID><h:TRANS_IDO>3418102316323038</h:TRANS_IDO><h:TRANS_IDH>20181023163230</h:TRANS_IDH><h:PROCESS_TIME>20181023163230</h:PROCESS_TIME><h:RESPONSE><h:RSP_TYPE>2</h:RSP_TYPE><h:RSP_CODE>9999</h:RSP_CODE><h:RSP_DESC>无数据</h:RSP_DESC></h:RESPONSE><h:COM_BUS_INFO><h:OPER_ID>A0009194</h:OPER_ID><h:PROVINCE_CODE>34</h:PROVINCE_CODE><h:EPARCHY_CODE>340</h:EPARCHY_CODE><h:CITY_CODE>340338</h:CITY_CODE><h:CHANNEL_ID>34a0563</h:CHANNEL_ID><h:CHANNEL_TYPE>1010200</h:CHANNEL_TYPE><h:ACCESS_TYPE>00</h:ACCESS_TYPE><h:ORDER_TYPE>01</h:ORDER_TYPE></h:COM_BUS_INFO><h:SP_RESERVE><h:TRANS_IDC>3418102316323038</h:TRANS_IDC><h:CUTOFFDAY>20181023</h:CUTOFFDAY><h:OSNDUNS>0400</h:OSNDUNS><h:HSNDUNS>9900</h:HSNDUNS><h:CONV_ID>20181023163230</h:CONV_ID></h:SP_RESERVE><h:TEST_FLAG>0</h:TEST_FLAG><h:MSG_SENDER>0400</h:MSG_SENDER><h:MSG_RECEIVER>1000</h:MSG_RECEIVER></h:UNI_BSS_HEAD><m:UNI_BSS_BODY><n-402300238:QRY_CP_TRADE_STATUS_BY_ORDERID_RSP xmlns:n-402300238=\"http://ws.chinaunicom.cn/JSQryCPDataSer/unibssBody/qryCPTradeStatusByOrderIdRsp\"><n-402300238:RESP_CODE>8888</n-402300238:RESP_CODE><n-402300238:RESP_DESC>无数据</n-402300238:RESP_DESC></n-402300238:QRY_CP_TRADE_STATUS_BY_ORDERID_RSP></m:UNI_BSS_BODY><a:UNI_BSS_ATTACHED xmlns:a=\"http://ws.chinaunicom.cn/unibssAttached\"/></m:QRY_CP_TRADE_STATUS_BY_ORDERID_OUTPUT></SOAP-ENV:Body></SOAP-ENV:Envelope>";
        Exchange exchange = new DefaultExchange();
        Message out = new DefaultMessage();
        out.setBody(body);
        exchange.setOut(out);
        exchange.setProperty(Exchange.REQLOG, new EcAopReqLog());
        Xml2JsonMappingProcessor xml = new Xml2JsonMappingProcessor();
        xml.params = new String[] { "com/ailk/ecaop/biz/template/cp/QryCPTradeStatusByOrderId_Rsp.xml" };
        xml.process(exchange);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Throwable ex = exchange.getException();
        if (ex != null) {
            ex.printStackTrace();
            throw new EcAopServerBizException("9999", "调用接口异常:" + ex.getMessage());
        }
        String body = exchange.getOut().getBody().toString();
        body = new URLDecodeProcessor().doCode(body);
        Map headMap = dealHeader(body);
        DealInterfaceUtils dif = new DealInterfaceUtils();
        String code = dif.dealTimeOutCode(headMap.get("headCode").toString());
        if (!"0000".equals(code) && !"9999".equals(code)) {
            if ("2990".equals(code) && "T2000533".equals(headMap.get("actCode") + "")) {
                Map bodyMap = dealBody(body);
                code = (String) bodyMap.get("code");
                if (null == bodyMap.get("detail")) {
                    String actCode = bodyMap.get("actCode").toString();
                    bodyMap.put("detail", dif.dealDetail(actCode, code));
                    code = dif.dealCode(actCode, code);
                }
                throw new EcAopServerBizException(code, dif.dealRespDesc(bodyMap));
            }
            throw new EcAopServerSysException(code, dif.dealRespDesc(headMap));
        }
        Map bodyMap = dealBody(body);
        code = (String) bodyMap.get("code");
        if (null != code && !"0000".equals(code) && !"00".equals(code)) {
            if (null == bodyMap.get("detail")) {
                String actCode = bodyMap.get("actCode").toString();
                bodyMap.put("detail", dif.dealDetail(actCode, code));
                code = dif.dealCode(actCode, code);
            }

            throw new EcAopServerBizException(code, dif.dealRespDesc(bodyMap));
        }
        dif.removeCodeDetail(bodyMap);
        exchange.getOut().setBody(bodyMap);
    }
}
