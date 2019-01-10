package com.ailk.ecaop.biz.sub.lan;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.request.EcAopRequestParamException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

@EcRocTag("FeeParamsProcessor")
public class FeeParamsProcessor extends BaseAopProcessor implements ParamsAppliable {

    private Map<String, Object> body;
    private Map map;

    @Override
    public void process(Exchange exchange) throws Exception {
        String methodCode = exchange.getMethodCode();
        body = exchange.getIn().getBody(Map.class);
        map = (Map) body.get("msg");
        if ("odsb".equals(methodCode)) {
            dealFeeInfo4odsb(exchange);
        }
        else if ("cfsb".equals(methodCode)) {
            dealFeeInfo4cfsb(exchange);
        }

    }

    private void dealFeeInfo4cfsb(Exchange exchange) {
        if (null != map.get("feeInfo")) {
            List<Map> list = (List<Map>) map.get("feeInfo");
            for (Map temp : list) {
                temp.put("operateType", 2 + "");
                temp.put("realFee", temp.get("origFee"));
            }
            nodeChange(list);
        }
        body.put("msg", map);
    }

    private void dealFeeInfo4odsb(Exchange exchange) {
        String value = (String) ((Map) map.get("payInfo")).get("payMode");

        map.put("sendTypeCode", value);
        String origTotalFee = (String) map.get("origTotalFee");
        map.put("origTotalFee", feeExchang(origTotalFee));
        if (null != map.get("feeInfo")) {

            List<Map> list = (List<Map>) map.get("feeInfo");

            for (Map temp : list) {
                String origFee = (String) temp.get("origFee");
                String reliefFee = (String) temp.get("reliefFee");
                String realFee = (String) temp.get("realFee");
                temp.put("origFee", feeExchang(origFee));
                temp.put("reliefFee", feeExchang(reliefFee));
                temp.put("realFee", feeExchang(realFee));
                temp.put("isPay", value);
                temp.put("payTag", value);
            }
            nodeChange(list);
        }
        body.put("msg", map);
    }

    private void nodeChange(List<Map> list) {
        map.remove("feeInfo");
        Map m1 = new HashMap();
        m1.put("feeInfo", list);
        map.put("subOrderSubReq", m1);
    }

    private String feeExchang(String str) {
        String value1 = null;
        try {
            int v1 = Integer.parseInt(str);
            if (v1 % 10 != 0) {
                throw new EcAopRequestParamException("费用从厘转换为分失败");
            }
            v1 = v1 / 10;
            value1 = v1 + "";
            return value1;
        }
        catch (Exception e) {
            throw new EcAopRequestParamException(e.getMessage());
        }
    }

    @Override
    public void applyParams(String[] params) {
    }
}
