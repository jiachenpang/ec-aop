/*********************************************************************************************************************
 **************************************** 号 码 资 源 释 放 ******************************************************************
 * 说明：
 * 此processor用于处理开户处理申请及开户处理提交接口受理失败时，对号码进行释放操作的情景。
 * 适用接口：
 * 开户处理申请接口-ecaop.trades.sell.mob.newu.open.appnumchg
 * 开户处理提交接口-ecaop.trades.sell.mob.newu.open.sub
 * 
 * @author Steven Zhang
 * @version 1.0.0
 * @date 2013-10-12
 *********************************************************************************************************************/
package com.ailk.ecaop.biz.res;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.base.IReleaseRes;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("numberRelease")
public class ReleaseNumberProcessor extends BaseAopProcessor implements IReleaseRes {

    @Override
    public void process(Exchange exchange) {
        String methodcode = exchange.getMethodCode();
        if ("opnc".equals(methodcode)) {
            return;
        }
        else if ("qrtc".equals(methodcode)) {
            return;
        }
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        if (null == msg.get("numId")) {
            return;
        }
        ArrayList<Map> resourcesInfo = new ArrayList<Map>();
        List<Map> numId = (List<Map>) msg.get("numId");
        List<Map> userInfo = (List<Map>) msg.get("userInfo");
        for (int i = 0; i < numId.size(); i++) {
            resourcesInfo.add(preParams(userInfo.get(i), numId.get(i)));
        }
        if (0 == resourcesInfo.size()) {
            return;
        }
        msg.put("resourcesInfo", resourcesInfo);
        // body.put("msg", msg);
        // exchange.getIn().setBody(body);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trades.numcheck.ParametersMapping", exchange);
        try {
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
            lan.xml2Json4ONS("ecaop.trades.numcheck.template", exchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "号码释放失败");
        }
        Map retMap = exchange.getOut().getBody(Map.class);
        dealReturn(retMap);
    }

    /**
     * 处理号码信息，准备参数
     * 
     * @param userMap
     * @param numMap
     * @return
     */
    private Map preParams(Map userMap, Map numMap) {
        Map res = new HashMap();
        res.put("proKey", numMap.get("proKey"));
        res.put("resourcesType", dealResourceType(userMap.get("serType").toString()));
        res.put("resourcesCode", numMap.get("serialNumber"));
        res.put("packageTag", userMap.get("packageTag"));
        res.put("occupiedFlag", "4");
        return res;
    }

    /**
     * 判断号码释放接口返回结果是否成功
     * 
     * @param inPutMap
     */
    @Override
    public void dealReturn(Map inPutMap) {
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

    /**
     * 付费类型转码
     * 
     * @param type
     * @return
     */
    @Override
    public String dealResourceType(String type) {
        return "1".equals(type) ? "01" : "02";
    }
}
