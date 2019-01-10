/*********************************************************************************************************************
 *********************************** ————文————档————说————明————********************************************************
 * 
 * @author Steven Zhang
 * @since 1.0
 * @version 1.0
 * @date 2013-10-24 15:17
 *       <ol>
 *       <li>此Processor用于处理客户资料校验接口</li>
 *       <li>此Processor目前仅用于处理客户资料校验接口（T2000501），其他接口使用会报错</li>
 *       <li>由于调用枢纽接口，需下发BIPCode，目前默认使用BIP2F036（客户资料返档），</br>因此报文头返回2990：客户资料已返档时，不进行报错处理</li>
 *       <li>报文头返回0000和9999时，均会继续判断报文体的应答编码，目的：获取报文体中更为详细的错误描述</li>
 *       <li>报文体0000,0001和0092按成功处理</li>
 *       <li>0001:表示是新客户;0092:是报文头2990时对应的应答码</li>
 *       </ol>
 *********************************************************************************************************************/
package com.ailk.ecaop.common.processor;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;

import com.ailk.ecaop.common.utils.DealInterfaceUtils;

@EcRocTag("Xml2Json4CustMapper")
public class Xml2Json4CustMappingProcessor extends BaseXml2JsonMapping {

    String[] params = { in };

    @Override
    public void process(Exchange exchange) throws Exception {

        Throwable ex = exchange.getException();
        if (ex != null) {
            ex.printStackTrace();
            throw new EcAopServerBizException("9999", "调用接口异常:" + ex.getMessage());
        }

        String body = exchange.getOut().getBody().toString();
        Map headMap = dealHeader(body);
        DealInterfaceUtils dif = new DealInterfaceUtils();
        String code = dif.dealTimeOutCode(headMap.get("headCode").toString());
        String actCode = headMap.get("actCode").toString();
        if (!"T2000501".equals(actCode)) {
            throw new EcAopServerBizException("Xml2Json4CustMappingProcessor只供客户资料校验接口使用！");
        }
        if (!"0000".equals(code) && !"9999".equals(code) && !"2990".equals(code) && !"0109".equals(code)) {
            throw new EcAopServerSysException(code, dif.dealRespDesc(headMap));
        }
        Map bodyMap = dealBody(body);
        if (null == bodyMap || null == bodyMap.get("code")) {
            throw new EcAopServerSysException(code, dif.dealRespDesc(headMap));
        }
        String respCode = bodyMap.get("code").toString();
        if ("0001".equals(respCode)) {
            Map dataMap = new HashMap();
            dataMap.put("code", respCode);
            dataMap.put("detail", bodyMap.get("detail"));
            exchange.getOut().setBody(dataMap);
            return;
        }
        if (!"0000".equals(respCode) && !"0092".equals(respCode)) {
            if (!"0900".equals(respCode) && !"0901".equals(respCode)) {
                throw new EcAopServerBizException(respCode, dif.dealRespDesc(bodyMap));
            }
            if (null == bodyMap.get("existedCustomer")) {
                throw new EcAopServerBizException(respCode, dif.dealRespDesc(bodyMap));
            }
        }
        dif.removeCodeDetail(bodyMap);
        exchange.getOut().setBody(bodyMap);
    }
}
