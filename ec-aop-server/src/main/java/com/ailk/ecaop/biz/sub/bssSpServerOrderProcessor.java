package com.ailk.ecaop.biz.sub;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

/**
 * sp流量包订购，支持N25省，透传给全业务
 * @author zhaok
 *
 */
@EcRocTag("bssSpServerOrder")
public class bssSpServerOrderProcessor extends BaseAopProcessor implements ParamsAppliable {

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];
    private static final String[] PARAM_ARRAY = {"ecaop.bss.msoa.ParametersMapping"};

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        // bss产品信息
        List<Map> productInfoBssList = (List<Map>) msg.get("productInfoBss");
        if (IsEmptyUtils.isEmpty(productInfoBssList)) {
            return;
        }
        Map productInfoBss = productInfoBssList.get(0);

        String productId = String.valueOf(productInfoBss.get("productId"));
        String updateType = String.valueOf(productInfoBss.get("updateType"));
        String spId = String.valueOf(productInfoBss.get("spId"));
        String recordSequenceId = "3900" + GetDateUtils.getDate() + "0001"; // 系统编码+YYYYMMDDHH24MISS +0001
        String subscriptionTime = GetDateUtils.getDate();
        Map tempMap = new HashMap();
        // 处理请求参数
        tempMap = MapUtils.asMap("productId", productId, "updateType", updateType, "spId", spId,
                "recordSequenceId", recordSequenceId, "subscriptionTime", subscriptionTime);
        msg.putAll(tempMap);
        body.put("msg", msg);
        exchange.getIn().setBody(body);

        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.SpProductSubSer");
        lan.xml2Json("ecaop.template.bss.msoa.terminfo", exchange);

    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[]{PARAM_ARRAY[i]});
        }
    }

    public static void main(String[] args) {
        /*Map<String, String> map = new HashMap<String, String>();

        String name = String.valueOf(map.get("name"));
        System.out.println(name instanceof String);
        String num = "";
        if ("null".equals(name)) {
            System.out.println("num是空的！");
        }
        if (!num.isEmpty()) {
            System.out.println(name + "name不是空的");
        }*/

    }
}
