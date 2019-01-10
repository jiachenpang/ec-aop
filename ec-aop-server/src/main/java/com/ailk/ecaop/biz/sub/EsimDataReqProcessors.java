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
import com.ailk.ecaop.common.utils.LanUtils;

/**
 * 一号多卡，eSIM资源申请接口
 *
 * @auther maly
 * @create 2017_01_09
 */
@EcRocTag("EsimDataReq")
public class EsimDataReqProcessors extends BaseAopProcessor implements ParamsAppliable {

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];
    private static final String[] PARAM_ARRAY = { "ecaop.trades.srne.paramtersmapping" };// 全业务接口

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = (Map) exchange.getIn().getBody();
        Map msg = (Map) body.get("msg");
        msg.put("iccid", msg.get("eiccid"));
        msg.put("msisdnA", msg.get("serialNumA"));
        msg.put("msisdnB", msg.get("serialNumB"));
        // 20170413 苏光耀要求该值从请求里取,商城会传,默认为1
        msg.put("releaseFlag", "1");
        if (null != msg.get("releaseFlag")) {
            msg.put("releaseFlag", msg.get("releaseFlag"));
        }
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        LanUtils lan = new LanUtils();

        lan.preData(pmp[0], exchange);// ecaop.trades.srne.paramtersmapping
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.RioEsimSer");
        lan.xml2Json("ecaop.trades.srne.template", exchange);

        Map rspMap = (Map) exchange.getOut().getBody();
        Map retMap = new HashMap();
        String smdpaddress = "";
        String conformCode = "";
        String activationcode = "";
        String eid = "";
        String matchingid = "";
        List<Map> respInfo = (List<Map>) rspMap.get("respInfo");
        if (null != respInfo) {
            for (Map respInfos : respInfo) {
                smdpaddress = (String) respInfos.get("smdpaddress");
                conformCode = (String) respInfos.get("conformCode");
                activationcode = (String) respInfos.get("activationcode");
                if (null != (String) respInfos.get("eid")) {
                    eid = (String) respInfos.get("eid");
                }
                if (null != (String) respInfos.get("matchingid")) {
                    matchingid = (String) respInfos.get("matchingid");
                }
            }
        }
        retMap.put("provOrderId", msg.get("ordersId"));
        retMap.put("eid", eid);
        retMap.put("matchingId", matchingid);
        retMap.put("smdpAddress", smdpaddress);
        retMap.put("conformCode", conformCode);
        retMap.put("activationCode", activationcode);
        exchange.getOut().setBody(retMap);

    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}