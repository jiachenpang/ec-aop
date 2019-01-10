package com.ailk.ecaop.biz.res;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;

/**
 * 用户产品查询
 * @author maly
 * @create 2017_03_22
 */
@EcRocTag("QryUserProduct")
public class QryUserProductProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {

        LanUtils lan = new LanUtils();

        lan.preData("ecaop.masb.qupq.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.UserProductInfoAOPSer");
        lan.xml2Json("ecaop.masb.qupq.template", exchange);

        Map rspMap = exchange.getOut().getBody(Map.class);
        Message out = new DefaultMessage();
        Map retMap = new HashMap();

        if (null != rspMap) {
            List<Map> productInfo = (List<Map>) rspMap.get("productInfo");
            List<Map> activityInfo = (List<Map>) rspMap.get("activityInfo");
            List<Map> discntInfo = (List<Map>) rspMap.get("discntInfo");
            List<Map> svcInfo = (List<Map>) rspMap.get("svcInfo");
            List<Map> spInfo = (List<Map>) rspMap.get("spInfo");
            if (activityInfo != null && activityInfo.size() > 0) {
                retMap.put("activityInfo", activityInfo);
            }
            if (productInfo != null && productInfo.size() > 0) {
                retMap.put("productInfo", productInfo);
            }
            if (discntInfo != null && discntInfo.size() > 0) {
                retMap.put("discntInfo", discntInfo);
            }
            if (svcInfo != null && svcInfo.size() > 0) {
                retMap.put("svcInfo", svcInfo);
            }
            if (spInfo != null && spInfo.size() > 0) {
                retMap.put("spInfo", spInfo);
            }
            out.setBody(retMap);
            exchange.setOut(out);
        }
        else {
            throw new EcAopServerBizException("9999", "返回应答信息为空");
        }
    }
}
