package com.ailk.ecaop.biz.brdband;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("QueryBoard4GInfo")
public class QueryBoard4GInfo extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.trades.qubq.queryBoard4GInfo.ParametersMapping" };
    private final ParametersMappingProcessor[] PMP = new ParametersMappingProcessor[PARAM_ARRAY.length];

    @Override
    public void process(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        lan.preData(PMP[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.Broadband4GSer");
        lan.xml2Json("ecaop.trade.qubq.template", exchange);
        Map outMap = exchange.getOut().getBody(Map.class);
        Map boardUserInfo = (Map) outMap.get("boardUserInfo");
        Map retMap = new HashMap();
        Map broadeInfo = new HashMap();
        broadeInfo.put("certType", boardUserInfo.get("psptTypeCode"));
        broadeInfo.put("certNum", boardUserInfo.get("psptId"));
        broadeInfo.put("certName", boardUserInfo.get("custName"));
        broadeInfo.put("certId", boardUserInfo.get("custId"));
        broadeInfo.put("productId", boardUserInfo.get("productId"));
        broadeInfo.put("productName", boardUserInfo.get("productName"));
        broadeInfo.put("serialNumber", boardUserInfo.get("serialNumber"));
        broadeInfo.put("accountNet", boardUserInfo.get("accountNet"));
        broadeInfo.put("serialNbr", boardUserInfo.get("serialNbr"));
        broadeInfo.put("installAddress", boardUserInfo.get("installAddress"));
        broadeInfo.put("accessType", boardUserInfo.get("accessYype"));
        broadeInfo.put("inDate", transDate(boardUserInfo.get("inDate").toString()));
        Map iptvInfo = (Map) boardUserInfo.get("iptvInfo");
        if (!IsEmptyUtils.isEmpty(iptvInfo)) {
            if (!IsEmptyUtils.isEmpty(iptvInfo.get("startDate"))) {
                iptvInfo.put("startDate", transDate(iptvInfo.get("startDate").toString()));
            }
            if (!IsEmptyUtils.isEmpty(iptvInfo.get("endDate"))) {
                iptvInfo.put("endDate", transDate(iptvInfo.get("endDate").toString()));
            }
            if (!IsEmptyUtils.isEmpty(iptvInfo.get("operDate"))) {
                iptvInfo.put("operDate", transDate(iptvInfo.get("operDate").toString()));
            }
            broadeInfo.put("iptvInfo", boardUserInfo.get("iptvInfo"));
        }
        retMap.put("broadeInfo", broadeInfo);
        exchange.getOut().setBody(retMap);
    }

    private String transDate(String date) {
        return date.replace("-", "").replace(":", "").replace(" ", "");
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            PMP[i] = new ParametersMappingProcessor();
            PMP[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
