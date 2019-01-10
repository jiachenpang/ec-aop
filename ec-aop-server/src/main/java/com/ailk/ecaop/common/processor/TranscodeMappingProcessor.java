/*********************************************************************************************************************
 *********************************** ————文————档————说————明————********************************************************
 * 
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
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;

import com.ailk.ecaop.common.utils.DealInterfaceUtils;
import com.ailk.ecaop.common.utils.MapUtils;

@EcRocTag("TranscodeMapper")
public class TranscodeMappingProcessor extends BaseXml2JsonMapping {

    String[] params = { in };
    static Map retCodeChgMap = MapUtils.asMap("01", "0001", "02", "0002", "03", "0003");

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

        if (!"0000".equals(code) && !"9999".equals(code) && !"2990".equals(code)) {
            throw new EcAopServerSysException(code, dif.dealRespDesc(headMap));
        }
        Map bodyMap = dealBody(body);
        Object respCode = bodyMap.get("code");

        if ("2990".equals(code)) {
            throw new EcAopServerSysException(retCode(respCode), dif.dealRespDesc(bodyMap));
        }
        // 处理报文头为9999，报文体为0000的情形 报文体respCode
        if ("9999".equals(code) && "00".equals(respCode)) {
            throw new EcAopServerSysException("9999", dif.dealRespDesc(headMap));
        }

        if (!"00".equals(respCode)) {
            throw new EcAopServerSysException(retCode(respCode), dif.dealRespDesc(bodyMap));
        }
        dif.removeCodeDetail(bodyMap);
        exchange.getOut().setBody(bodyMap);
    }

    // 返回编码，2位转4位
    private String retCode(Object code) {
        Object retCode = retCodeChgMap.get(code);
        return null == retCode ? "9999" : retCode.toString();
    }

}
