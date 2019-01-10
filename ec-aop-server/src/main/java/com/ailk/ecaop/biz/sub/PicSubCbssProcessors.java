package com.ailk.ecaop.biz.sub;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.DividePic2ParaUtil;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("picSubCbss")
public class PicSubCbssProcessors extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");

        msg.put("para", new DividePic2ParaUtil().divide(msg.get("pic1").toString()));

        msg.put("psptTypeCode", CertTypeChangeUtils.certTypeMall2Cbss(null == msg.get("psptTypeCode") ? "02" : msg
                .get("psptTypeCode").toString()));
        LanUtils lan = new LanUtils();

        lan.preData("ecaop.trades.cbss.services.uploadCustInfo", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.CustForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.uploadCustInfo.template", exchange);

    }
}
