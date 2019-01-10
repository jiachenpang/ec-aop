package com.ailk.ecaop.biz.query;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *查询融合业务订单编码和宽带号码
 *
 */
@EcRocTag("QryCPTradeIdAndNumber")
public class QryCPTradeIdAndNumbeProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = {"ecaop.ecsb.qtnq.ParametersMapping"};
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];

    @Override
    public void process(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        Map msg = dealReq(exchange);

        lan.preData(pmp[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.JSQryCPDataSer");
        lan.xml2Json("ecaop.trades.qtnq.template", exchange);
    }
    /**
     * 处理请求
     *
     */
    private Map dealReq(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        msg.put("provinceCode", msg.get("province"));
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        return msg;
    }
    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[]{PARAM_ARRAY[i]});
        }
    }
}
