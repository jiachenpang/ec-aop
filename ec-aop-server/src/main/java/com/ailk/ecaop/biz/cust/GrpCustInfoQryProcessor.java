package com.ailk.ecaop.biz.cust;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import java.util.Map;

@EcRocTag("grpCustInfoQry")
public class GrpCustInfoQryProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = {"ecaop.trades.scgq.qryGrpCustInfo.paramtersmapping"};
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[PARAM_ARRAY.length];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        msg.put("provinceCode", msg.get("province"));
        msg.put("eparchyCode", msg.get("city"));
        if ("2".equals(msg.get("optType"))) {
            String qryId = msg.get("qryId").toString();
            msg.put("groupId", CertTypeChangeUtils.certTypeMall2Fbs(qryId.substring(0, 2)) + qryId.substring(2));
        }
        else {
            msg.put("groupId", msg.get("qryId"));
        }

        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.GrpCustInfoAOPSer");
        lan.xml2Json("ecaop.trades.grpCustInfoAOPSer.qryGrpCustInfo.template", exchange);

        // 处理返回
        Map out = exchange.getOut().getBody(Map.class);
        out.put("certTypeCode", CertTypeChangeUtils.certTypeFbs2Mall(out.get("certTypeCode").toString()));
        Map retMap = MapUtils.asMap("respInfo", out);
        exchange.getOut().setBody(retMap);
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[]{PARAM_ARRAY[i]});
        }
    }
}
