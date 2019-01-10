package com.ailk.ecaop.common.processor;

import java.util.ArrayList;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.MapUtils;

@EcRocTag("divideParam")
public class Divide2ParaProcess extends BaseAopProcessor implements ParamsAppliable {

    String orgStr, tarStr;
    int num;

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = (Map) exchange.getIn().getBody();
        String[] orgFloor = orgStr.split("\\.");
        Map msg = (Map) body.get("msg");
        for (int i = 1; i < orgFloor.length - 1; i++) {
            msg = (Map) msg.get(orgFloor[i]);
            if (null == msg) {
                return;
            }
        }
        orgStr = (String) msg.get(orgFloor[orgFloor.length - 1]);
        orgStr = orgStr.replace(" ", "+");
        if (null == orgStr) {
            return;
        }
        int orgLen = orgStr.length();
        ArrayList<Map> para = null == msg.get("para") ? new ArrayList<Map>() : (ArrayList<Map>) msg.get("para");
        for (int i = 0; i < num - 1; i++) {
            para.add(MapUtils.asMap("paraId", tarStr, "paraValue",
                    orgStr.substring(i * orgLen / num, (i + 1) * orgLen / num)));
        }
        para.add(MapUtils.asMap("paraId", tarStr, "paraValue", orgStr.substring(orgLen * (num - 1) / num, orgLen)));
        msg.put("para", para);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
    }

    @Override
    public void applyParams(String[] params) {
        if (params.length < 2 || params.length > 3) {
            throw new EcAopServerBizException("9999", "入参个数不正确 ");
        }
        orgStr = params[0];
        num = Integer.valueOf(params[1]);
        tarStr = 2 == params.length ? "PHOTO" : tarStr;
    }
}
