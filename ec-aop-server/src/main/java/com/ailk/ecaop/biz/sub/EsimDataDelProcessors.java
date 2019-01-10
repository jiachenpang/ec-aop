package com.ailk.ecaop.biz.sub;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("EsimDataDel")
public class EsimDataDelProcessors extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();

        lan.preData("ecaop.trades.rned.paramtersmapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.RioEsimSer");
        lan.xml2Json("ecaop.trades.rned.template", exchange);

        Map rspMap = (Map) exchange.getOut().getBody();
        if (!IsEmptyUtils.isEmpty(rspMap)) {
            Map retMap = new HashMap();
            String resultCode = "";
            List<Map> respInfo = (List<Map>) rspMap.get("respInfo");
            if (null != respInfo) {
                for (Map respInfos : respInfo) {
                    resultCode = (String) respInfos.get("resultCode");
                    if ("0".equals(resultCode)) {
                        retMap.put("resultCode", resultCode);
                        retMap.put("resultName", "资源释放成功");
                    }
                    if ("1".equals(resultCode)) {
                        retMap.put("resultCode", resultCode);
                        retMap.put("resultName", "资源释放失败");
                    }
                    if ("2".equals(resultCode)) {
                        retMap.put("resultCode", resultCode);
                        retMap.put("resultName", "资源已下载，不能释放资源");
                    }
                }
            }
            exchange.getOut().setBody(retMap);
        }
        else {
            throw new EcAopServerBizException("9999", "返回应答信息为空");
        }
    }

}
