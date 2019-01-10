/**********************************************************************************************************************
 * 此工具类用于释放广东集客预占的号码和终端
 * 
 * @author Steven Zhang
 * @date 2013-06-05
 *********************************************************************************************************************/
package com.ailk.ecaop.common.utils;

import java.util.ArrayList;
import java.util.Map;

import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

import com.ailk.ecaop.base.CallEngine;
import com.alibaba.fastjson.JSON;

public class ReleaseResUtils {

    /**
     * 释放号码资源
     * 商城侧号码自行释放
     * 
     * @param exchange
     * @throws Exception
     */
    public void releaseNumber(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = isString ? JSON.parseObject((String) body.get("msg")) : (Map) body.get("msg");
        if (null == msg.get("numId")) {
            return;
        }
        if ("opap".equals(exchange.getMethodCode())) {
            return;
        }
        DealResOccupyAndRlsUtils dr = new DealResOccupyAndRlsUtils();
        dr.preNumberInfo(msg, false);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trades.numcheck.ParametersMapping", exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
        lan.xml2Json4ONS("ecaop.trades.numcheck.template", exchange);
        Map retMap = exchange.getOut().getBody(Map.class);
        dealReturn4Number(retMap);
    }

    /**
     * 释放终端资源
     * 
     * @param exchange
     * @throws Exception
     */
    public void releaseTerminal(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = isString ? JSON.parseObject((String) body.get("msg")) : (Map) body.get("msg");
        DealResOccupyAndRlsUtils dr = new DealResOccupyAndRlsUtils();
        msg.put("methodCode", exchange.getMethodCode());
        boolean needRelease = dr.isExistTerminal(msg);
        if (!needRelease) {
            return;
        }
        // 准备数据
        String methodCode = exchange.getMethodCode();
        msg.put("methodCode", methodCode);
        dr.preTerminalInfo(msg, false);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trades.checkres.ParametersMapping", exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
        lan.xml2Json4Res("ecaop.trades.checkres.template", exchange);
    }

    /**
     * 判断号码释放接口返回结果是否成功
     * 
     * @param inPutMap
     */
    private void dealReturn4Number(Map inPutMap) {
        Object code = inPutMap.get("code");
        if (null == code || "0000".equals(code.toString())) {
            return;
        }
        if ("0001".equals(code.toString())) {
            ArrayList<Map> resourcesRsp = (ArrayList<Map>) inPutMap.get("resourcesRsp");
            for (Map resource : resourcesRsp) {
                String rscStateCode = resource.get("rscStateCode").toString();
                if (!"0000".equals(rscStateCode) || !"0006".equals(rscStateCode)) {
                    throw new EcAopServerBizException(rscStateCode, "[号码资源状态变更接口]返回:"
                            + resource.get("rscStateDesc"));
                }
            }
        }
        else {
            throw new EcAopServerBizException(code.toString(), "[号码资源状态变更接口]返回:" + inPutMap.get("detail"));
        }
    }
}
