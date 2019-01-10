/*********************************************************************************************************************
 *********************************** ————文————档————说————明————********************************************************
 * 
 * @author Steven Zhang
 * @since 1.0
 * @version 1.0
 * @date 2013-10-24 15:27
 *       <ol>
 *       <li>此Processor用于处理北六资源校验接口</li>
 *       <li>此Processor目前仅用于处理北六资源校验接口（T3000001），其他接口使用会报错</li>
 *       <li>由于调用枢纽接口，需下发BIPCode，目前默认使用BIP3F001</li>
 *       <li>报文头返回0000和9999时，均会继续判断报文体的应答编码，目的：获取报文体中更为详细的错误描述</li>
 *       <li>不进行报文体正确与否的判断</li>
 *       </ol>
 *********************************************************************************************************************/
package com.ailk.ecaop.common.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;

import com.ailk.ecaop.common.utils.DealInterfaceUtils;

@EcRocTag("Xml2Json4Res")
public class Xml2Json4ResMappingProcessor extends BaseXml2JsonMapping {

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
        if (!"0000".equals(code) && !"9999".equals(code)) {
            throw new EcAopServerSysException(code, dif.dealRespDesc(headMap));
        }
        Map bodyMap = dealBody(body);
        Object respCode = bodyMap.get("code");
        if (null == respCode || "".equals(respCode)) {
            respCode = "0000";
        }

        // 处理报文头为9999，报文体为0000的情形
        if ("9999".equals(code) && "0000".equals(respCode)) {
            throw new EcAopServerSysException("9999", dif.dealRespDesc(headMap));
        }
        String methodCode = exchange.getMethodCode().toString();
        bodyMap.put("methodCode", methodCode);

        dif.removeCodeDetail(bodyMap);
        ArrayList<Map> res = dealReturn(bodyMap, headMap.get("actCode"));
        if (null == res || 0 == res.size()) {
            throw new EcAopServerBizException(respCode.toString(), "其他错误");
        }
        Map msg = new HashMap();
        msg.put("resourcesRsp", res);
        exchange.getOut().setBody(msg);
    }

    /**
     * 处理返回信息
     * 
     * @param retMap
     * @return
     */
    private ArrayList<Map> dealReturn(Map retMap, Object activityCode) {
        ArrayList<Map> resourcesRsp = (ArrayList<Map>) retMap.get("resourcesRsp");
        ArrayList<Map> resourcesInfo = new ArrayList<Map>();
        for (Map resource : resourcesRsp) {
            String rscStateCode = resource.get("rscStateCode").toString();
            if ("0005".equals(rscStateCode) && "qrta".equals(retMap.get("methodCode"))) {
                dealChannel(resource);
            }
            else if (!"0000".equals(rscStateCode) && "T4000001".equals(activityCode)) {
                throw new EcAopServerBizException(rscStateCode, (String) resource.get("rscStateDesc"));
            }
            resourcesInfo.add(resource);
        }
        return resourcesInfo;
    }

    private void dealChannel(Map resource) {
        String rscStateDesc = resource.get("rscStateDesc").toString();
        String[] rscStateDescArray = rscStateDesc.split("\\|");
        if (2 == rscStateDescArray.length) {
            resource.put("channelId", rscStateDescArray[1]);
            resource.put("channelName", rscStateDescArray[0]);
        }
    }
}
