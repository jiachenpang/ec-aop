package com.ailk.ecaop.biz.sub.lan;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.impl.DefaultMessage;

import com.ailk.ecaop.common.utils.YearPayEcsCheck;

@EcRocTag("yearPayEcsSub")
public class YearPayEcsSubProcessor extends YearPayEcsCheckProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        String apptx = (String) body.get("apptx");
        Map msg = (Map) body.get("msg");
        Object opeSysType = msg.get("opeSysType");
        checkInputParam(msg, opeSysType);
        String province = msg.get("province").toString();
        Map preSubmitReturnMap = new HashMap();
        YearPayEcsCheck check = "2".equals(opeSysType) ? new YearPayEcs4CBSSSub()
                : "11|17|18|76|91|97".contains(province) ? new YearPayEcs4N6Sub(pmp) : new YearPayEcs4South25Sub(
                        pmp4N25);
        System.out.println("zsqtest1  " + apptx + msg);
        preSubmitReturnMap = check.yearPayEcsCheck(exchange, msg);
        System.out.println("zsqtest2  " + apptx + msg);
        Message out = new DefaultMessage();
        Map outMap = new HashMap();
        // 宽带趸交流程，沿用现有流程
        if ("0".equals(preSubmitReturnMap.get("isNoChangeProduct"))) {
            preSubmitReturnMap.remove("isNoChangeProduct");
            outMap = preSubmitReturnMap;
        }
        else {
            System.out.println("zsqtest3  " + apptx + msg);
            outMap = check.yearPayEcsSub(exchange, preSubmitReturnMap, msg);
        }
        out.setBody(outMap);
        exchange.setOut(out);
    }
}
