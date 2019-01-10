package com.ailk.ecaop.biz.res;

import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.IEcAopKey;

@EcRocTag("PreparePrdInfoForResFace")
public class PreparePrdInfoForResFace extends BaseAopProcessor implements ParamsAppliable {

    @Override
    public void process(Exchange exchange) throws Exception {

        // 调用资源预判接口
        Map resCheckIn = exchange.getIn().getBody(Map.class);

        Map msg = (Map) resCheckIn.get(IEcAopKey.MSG);

        List prdCodes = (List) msg.get("productCode");

        msg.put("productCodes", prdCodes);
        msg.remove("productCode");
        msg.put("productCode", prdCodes.get(0));

        msg.put("shareNumber", (String) msg.get("areaCode") + msg.get("shareNumber"));
        msg.remove("areaCode");
    }

    @Override
    public void applyParams(String[] params) {
        // TODO Auto-generated method stub

    }

}
