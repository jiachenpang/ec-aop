package com.ailk.ecaop.biz.brd;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.biz.sub.lan.YearPayEcs4South25Check;

@EcRocTag("yearPayEcs4South25Check")
public class YearPayEcs4South25CheckProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.masb.spec.sProductChgParametersMapping",
            "ecaop.masb.odsb.ActivityAryParametersMapping" };
    protected final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[2];
    private String apptx;

    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        apptx = (String) body.get("apptx");
        Map msg = (Map) body.get("msg");
        Object opeSysType = msg.get("opeSysType");
        String province = msg.get("province").toString();
        Map preSubmitReturnMap = new HashMap();
        ELKAopLogger.logStr("准备进老流程的msg: appTx: " + apptx + ", msg= " + msg);
        YearPayEcs4South25Check check = new YearPayEcs4South25Check(pmp);
        preSubmitReturnMap = check.yearPayEcsCheck(exchange, msg);
        Map outMap = new HashMap();
        Message out = new DefaultMessage();
        if ("0".equals(preSubmitReturnMap.get("isNoChangeProduct"))) {// 宽带趸交流程，沿用现有流程，不需要调用订单提交接口回滚
            preSubmitReturnMap.remove("isNoChangeProduct");
            outMap = preSubmitReturnMap;
        }
        else {
            outMap = check.yearPayEcsSub(exchange, preSubmitReturnMap, msg);
            if (!"2".equals(opeSysType) && !"11|17|18|76|91|97".contains(province)) {
                outMap.put("custName", preSubmitReturnMap.get("custName"));
                outMap.put("productType", preSubmitReturnMap.get("productType"));
                outMap.put("productName", preSubmitReturnMap.get("productName"));
                outMap.put("discntName", preSubmitReturnMap.get("discntName"));
                outMap.put("startDate", preSubmitReturnMap.get("startDate"));
                outMap.put("endDate", preSubmitReturnMap.get("endDate"));
            }
        }
        out.setBody(outMap);
        exchange.setOut(out);
    }

    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
