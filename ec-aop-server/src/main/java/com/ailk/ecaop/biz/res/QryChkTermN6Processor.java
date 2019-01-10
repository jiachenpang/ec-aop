package com.ailk.ecaop.biz.res;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("qryChkTermN6")
public class QryChkTermN6Processor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        long start = System.currentTimeMillis();
        Map body = exchange.getIn().getBody(Map.class);
        Map tempBody = new HashMap(body);
        Map msg = (Map) body.get("msg");
        List<Map> userInfo = (List<Map>) msg.get("userInfo");
        if (null == userInfo || 0 == userInfo.size()) {
            return;
        }
        ArrayList<Map> resourcesInfo = new ArrayList<Map>();
        for (Map user : userInfo) {
            String bipType = (String) user.get("bipType");
            List<Map> activityInfo = (List<Map>) user.get("activityInfo");
            if (null == activityInfo || 0 == activityInfo.size()) {
                continue;
            }
            for (Map activity : activityInfo) {
                Map res = new HashMap();
                Object resourceCode = activity.get("resourcesCode");
                if (null == resourceCode || "".equals(resourceCode)) {
                    continue;
                }
                Object resourcesType = activity.get("resourcesType");
                if (null == resourcesType || "".equals(resourcesType)) {
                    continue;
                }
                res.put("resourcesType", decodeTerminalType(resourcesType.toString()));
                res.put("resourcesCode", resourceCode.toString());
                // res.put("isTest", activity.get("isTest"));
                res.put("operType", "0");
                res.put("useType", "0");
                res.put("activeType", "03");
                res.put("occupiedFlag", "0");
                if ("6".equals(user.get("bipType")) || "cmsb".equals(exchange.getAppCode())) {
                    res.put("isSelf", "2");
                }
                resourcesInfo.add(res);
            }
        }
        if (null == resourcesInfo || 0 == resourcesInfo.size()) {
            return;
        }
        msg.put("accessType", "01");
        msg.put("resourcesInfo", resourcesInfo);
        body.put("msg", msg);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.gdjk.checkres.ParametersMapping", exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
        lan.xml2Json4Res("ecaop.gdjk.checkres.template", exchange);
        Map retMap = exchange.getOut().getBody(Map.class);
        if (200 != (Integer) exchange.getProperty(Exchange.HTTP_STATUSCODE)) {
            throw new EcAopServerBizException(retMap.get("code").toString(), retMap.get("detail").toString());
        }
        ArrayList<Map> res = (ArrayList<Map>) retMap.get("resourcesRsp");
        if (0 != res.size()) {
            for (Map resMap : res) {
                String rscStateCode = resMap.get("rscStateCode").toString();
                if (!"0000".equals(rscStateCode)) {
                    throw new EcAopServerBizException(rscStateCode, resMap.get("rscStateDesc").toString());
                }
                Object cost = resMap.get("cost");
                if (null == cost || "".equals(cost)) {
                    resMap.put("cost", "0");
                }
                Object salePrice = resMap.get("salePrice");
                if (null == salePrice || "".equals(salePrice)) {
                    resMap.put("salePrice", "0");
                }
                Object cardPrice = resMap.get("cardPrice");
                if (null == cardPrice || "".equals(cardPrice)) {
                    resMap.put("cardPrice", "0");
                }
                Object reservaPrice = resMap.get("reservaPrice");
                if (null == reservaPrice || "".equals(reservaPrice)) {
                    resMap.put("reservaPrice", "0");
                }
            }
            msg.put("resourcesInfo", res);
        }
        exchange.getIn().setBody(tempBody);
        ELKAopLogger.logStr("QryChkTermN6Processor in:" + GetDateUtils.getDate() + ",cost:"
                + (System.currentTimeMillis() - start) + "ms,apptx:" + body.get("apptx"));
    }

    /**
     * 終端類型轉碼
     * @param resourcesType
     * @return
     */
    private String decodeTerminalType(String resourcesType) {
        if ("04".equals(resourcesType)) {
            return "02";
        }
        else if ("05".equals(resourcesType)) {
            return "03";
        }
        return "01";
    }
}
