/***********************************************************************************************************************
 * 此Processor用于调用改撤单校验接口
 */
package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("orderChgChk")
public class OrderCHGCHKProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        String methodCode = exchange.getMethodCode();
        exchange.setMethodCode("occk");
        Map msg = (Map) body.get("msg");
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.masb.occk.orderCHGCHKParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.ordser");
        lan.xml2Json("ecaop.masb.occk.orderCHGCHKTemplate", exchange);
        exchange.setMethodCode(methodCode);

        Map retMap = exchange.getOut().getBody(Map.class);
        if (200 == (Integer) exchange.getProperty(Exchange.HTTP_STATUSCODE)) {
            dealReturn4sbcs(retMap, msg.get("oldBssOrderId").toString());
        }
    }

    /**
     * 对返回参数进行处理,由于请求只会有一个省份订单,返回允许有多个,因此进行判断
     * 
     * @param inMap
     * @param oldBssOrderId
     */
    private void dealReturn4sbcs(Map inMap, String oldBssOrderId) {
        ArrayList<Map> respInfo = (ArrayList<Map>) inMap.get("respInfo");
        if (null == respInfo || 0 == respInfo.size()) {
            throw new EcAopServerBizException("9999", "省分'改撤单校验[orderCHGCHK]'未返回订单");
        }
        Map resp = null;
        for (int i = 0; i < respInfo.size(); i++) {
            if (oldBssOrderId.equals(respInfo.get(i).get("respOrderId"))) {
                resp = respInfo.get(i);
                break;
            }
        }
        if (null == resp) {
            throw new EcAopServerBizException("9999", "省分'改撤单校验[orderCHGCHK]'未返回与商城提供订单[oldProvOrderId]:'"
                    + oldBssOrderId + "'匹配的数据");
        }
        if (!"2".startsWith(resp.get("respResult").toString())) {
            throw new EcAopServerBizException("1802", "省分'改撤单校验[orderCHGCHK]'返回:'订单" + oldBssOrderId
                    + "':'" + resp.get("respResult") + "'");
        }
    }
}
