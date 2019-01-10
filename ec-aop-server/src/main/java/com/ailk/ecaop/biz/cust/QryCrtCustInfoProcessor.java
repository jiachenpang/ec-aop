package com.ailk.ecaop.biz.cust;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONObject;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.n3r.ecaop.core.processor.TransReqParamsMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.google.common.collect.Maps;

/**
 * 客户资料查询创建接口
 * @author
 */
@EcRocTag("qryCrtCustInfo")
public class QryCrtCustInfoProcessor extends BaseAopProcessor implements ParamsAppliable {

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];
    private static final String[] PARAM_ARRAY = { "ecaop.trades.scco.crtCustInfo.paramtersmapping" }; // 客户资料查询创建接口

    @Override
    public void process(Exchange exchange) throws Exception {
        // 客户资料查询创建接口，目前只支持4G业务，故在此进行写死
        Map<String, Object> body = exchange.getIn().getBody(Map.class);
        Map msg = new HashMap((Map) body.get("msg"));
        String bizkey = exchange.getBizkey();
        String method = exchange.getMethod();
        String methodCode = exchange.getMethodCode();
        Map map = Maps.newHashMap();
        map.put("msg", JSONObject.fromObject(createCustQry(msg)));
        exchange.getIn().setBody(map);
        exchange.setBizkey("TS-3G-001");
        exchange.setMethod("ecaop.trades.query.comm.cust.check");
        exchange.setMethodCode("cuck");
        new TransReqParamsMappingProcessor().process(exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.ec-aop.rest");
        Object isAssure = msg.get("isAssure");
        if ("0".equals(isAssure) && !exchange.getProperty(Exchange.HTTP_STATUSCODE).equals("560")) {
            return;
        }
        Map retMap = JSONObject.fromObject(exchange.getOut().getBody());
        if ("0001".equals(retMap.get("code"))) {
            exchange.setBizkey(bizkey);
            exchange.setMethod(method);
            exchange.setMethodCode(methodCode);
            // 增加黑名单标识
            String chkBlcTag = (String) msg.get("chkBlcTag");
            Map para = new HashMap();
            if ("1".equals(chkBlcTag)) {
                para.put("paraId", "OPERATE_FLAG");
                para.put("paraValue", "1");
            }
            // RHQ2018082800066-增加“港澳台居民居住证”证件类型的需求 by wangmc 20180914
            List<Map> paraList = (List<Map>) msg.get("para");
            if (!IsEmptyUtils.isEmpty(paraList)) {
                paraList = new ArrayList<Map>();
            }
            paraList.add(para);
            msg.put("para", paraList);

            msg = createCustInfo(msg);
            msg.put("orderId", GetSeqUtil.getSeqFromCb(exchange, "seq_trade_id"));
            body.put("msg", msg);
            exchange.getIn().setBody(body);
            LanUtils lan = new LanUtils();
            lan.preData(pmp[0], exchange);// ecaop.trades.scco.crtCustInfo.paramtersmapping
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.CustSer");
            lan.xml2Json("ecaop.trades.scco.crtCustInfo.template", exchange);
        }
    }

    private Map createCustQry(Map message) {
        Map retMap = MapUtils.asMap("opeSysType", MagicNumber.SYS_TYPE_CBSS, "checkType", "0", "serType", "1");
        MapUtils.arrayPut(retMap, message, new String[] { "operatorId", "province", "city", "district", "channelId",
                "channelType", "certType", "certNum" });
        return retMap;
    }

    private Map createCustInfo(Map message) {
        Map retMap = Maps.newHashMap();
        message.put("certType", CertTypeChangeUtils.certTypeMall2Cbss(message.get("certType").toString()));
        retMap.put("custInfo", message);
        MapUtils.arrayPut(retMap, message, new String[] { "operatorId", "province", "city", "district", "channelId",
                "channelType" });
        return retMap;
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
