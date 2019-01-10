package com.ailk.ecaop.biz.sub.gdjk;

import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.res.ResourceStateChangeUtils;
import com.ailk.ecaop.common.utils.DealResOccupyAndRlsUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("CheckResProcessor")
public class CheckResProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        String methodCode = exchange.getMethodCode();
        DealResOccupyAndRlsUtils dror = new DealResOccupyAndRlsUtils();
        Map tempBody = dror.copyMap(body);
        boolean isString = body.get("msg") instanceof String;
        Map map = isString ? JSON.parseObject((String) body.get("msg")) : (Map) body.get("msg");
        map.put("methodCode", methodCode);

        // 判断用不用资源预占
        boolean needOccupied = dror.isExistTerminal(map);
        if (!needOccupied) {
            return;
        }
        // 资源预占
        dror.preTerminalInfo(map, true);
        body.put("msg", map);
        exchange.getIn().setBody(body);
        // 割接省份需要调用新零售中心
        if (EcAopConfigLoader.getStr("ecaop.global.param.resources.aop.province")
                .contains((String) map.get("province"))) {
            ResourceStateChangeUtils resourceStateChangeUtils = new ResourceStateChangeUtils();
            List<Map> resourceInfo = (List<Map>) map.get("resourcesInfo");
            if ("2".equals(resourceInfo.get(0).get("isSelf"))) {
                // 自备机逻辑
                resourceStateChangeUtils.getSelfResourceInfo(exchange);
            }
            else {
                String occupiedFlag = (String) resourceInfo.get(0).get("occupiedFlag");
                if ("0,1".contains(occupiedFlag)) {
                    resourceStateChangeUtils.getResourceInfo(exchange);
                }
                else if ("2".equals(occupiedFlag)) {
                    throw new EcAopServerBizException("9999", "新资源中心不支持预定终端");
                }
                else if ("3".equals(occupiedFlag)) {
                    // 终端释放
                    resourceStateChangeUtils.bathReleaseResourceInfo(exchange);
                }
                else if ("4".equals(occupiedFlag)) {
                    // 终端故障改空闲
                    throw new EcAopServerBizException("9999", "新资源中心不支持故障改空闲操作");
                }
            }
            if ("qrtc".equals(exchange.getMethodCode())) {
                Map retMap = exchange.getOut().getBody(Map.class);
                checkReturn(retMap);
                exchange.getIn().setBody(tempBody);
            }
            return;
        }
        // 资源预占
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.gdjk.checkres.ParametersMapping", exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
        lan.xml2Json4Res("ecaop.gdjk.checkres.template", exchange);
        if ("qrtc".equals(exchange.getMethodCode())) {
            Map retMap = exchange.getOut().getBody(Map.class);
            checkReturn(retMap);
            exchange.getIn().setBody(tempBody);
        }

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
}
