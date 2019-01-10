package com.ailk.ecaop.biz.sub.gdjk;

import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.request.EcAopRequestParamException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.DealResOccupyAndRlsUtils;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("NumCheckProcessor")
public class NumCheckProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        DealResOccupyAndRlsUtils dror = new DealResOccupyAndRlsUtils();
        Map tempBody = dror.copyMap(body);
        Map map = (Map) body.get("msg");
        if (null == map.get("numId")) {
            return;
        }
        if (!"opnc".equals(exchange.getMethodCode())) {
            return;
        }
        checkInputParam(map);

        // 准备号码预占所需的参数
        dror.preNumberInfo(map, true);

        // 号码预占
        LanUtils lan = new LanUtils();
        body.put("msg", map);
        exchange.getIn().setBody(body);
        lan.preData("ecaop.trades.numcheck.ParametersMapping", exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
        lan.xml2Json4Res("ecaop.trades.numcheck.template", exchange);
        Map retMap = exchange.getOut().getBody(Map.class);
        checkReturn(retMap);
        exchange.getIn().setBody(tempBody);
    }

    private void checkReturn(Map retMap) {
        List<Map> resourcesRsp = (List<Map>) retMap.get("resourcesRsp");
        for (Map res : resourcesRsp) {
            String rscStateCode = res.get("rscStateCode").toString();
            if (!"0000".equals(rscStateCode)) {
                throw new EcAopServerBizException(rscStateCode, res.get("rscStateDesc").toString());
            }
        }
    }

    private void checkInputParam(Map map) {
        if (null == map.get("customerInfo")) {
            return;
        }
        List<Map> customerInfo = (List<Map>) map.get("customerInfo");
        for (Map m : customerInfo) {// 仅当是老客户时才强检验custId
            if ("1".equals(m.get("custType")) && null == m.get("custId")) {
                throw new EcAopRequestParamException("老客户的custId是必传的！");
            }
        }
        List<Map> numId = (List<Map>) map.get("numId");
        List<Map> userInfo = (List<Map>) map.get("userInfo");
        if (numId.size() != userInfo.size()) {
            throw new EcAopRequestParamException("用户信息和号码无法匹配！");
        }
    }
}
