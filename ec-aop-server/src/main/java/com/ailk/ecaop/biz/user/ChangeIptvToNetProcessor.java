package com.ailk.ecaop.biz.user;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

/**
 * @author cuij
 * @version 创建时间：2016-9-7 下午4:49:12
 *          调cbssiptv/互联网电视转换为宽带/固话号码
 */
@EcRocTag("changeIptvToNet")
public class ChangeIptvToNetProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.gxzh.spff.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Object operateType = msg.get("operateType");
        Object serialNumber = null;
        // 判断号码类型
        if ("2".equals(operateType) || "3".equals(operateType)) {
            serialNumber = queryNumber(exchange, operateType);
        }
        else {
            serialNumber = msg.get("serialNumber");
        }
        msg.put("serialNumber", serialNumber);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
    }

    private Object queryNumber(Exchange exchange, Object operateType) throws Exception {
        LanUtils lan = new LanUtils();
        Map body = (Map) exchange.getIn().getBody();
        Map msg = (Map) body.get("msg");
        msg = MapUtils.asMap("province", msg.get("province"), "city", msg.get("city"), "district",
                msg.get("district"), "iptvNumber", msg.get("serialNumber"));
        if ("2".equals(operateType)) {
            msg.put("operateType", "1");
        }
        if ("3".equals(operateType)) {
            msg.put("operateType", "2");
        }
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        lan.preData(pmp[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.IptvToNumber");
        lan.xml2Json("ecaop.gxzh.spff.template", exchange);
        // 处理返回
        Map rspMap = (Map) exchange.getOut().getBody();
        return rspMap.get("acctNumber");
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }

    }

}
