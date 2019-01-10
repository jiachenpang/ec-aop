package com.ailk.ecaop.biz.cust;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.request.EcAopRequestBizException;
import org.n3r.ecaop.core.processor.AopCall;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ailk.ecaop.common.processor.Xml2Json4CustMappingProcessor;
import com.alibaba.fastjson.JSON;

@EcRocTag("callCoreSys4CustInfo")
public class CallCoreSysProcessor extends BaseAopProcessor implements ParamsAppliable {

    private String paraKey;
    private final String FBS_URL = "ecaop.comm.conf.url.osn.syncreceive.0002";
    private Logger log = LoggerFactory.getLogger(CallCoreSysProcessor.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        // 获取外围传进来的请求参数
        Map headers = exchange.getIn().getHeader("strParams", Map.class);
        headers.put("msg", JSON.parseObject((String) headers.get("msg")));
        exchange.getIn().setBody(headers);
        if ("masb".equals(exchange.getAppCode())) {// 只有商城才返回sysType，其他系统上线后需放开限制
            Map body = exchange.getIn().getBody(Map.class);
            Map msgMap = (Map) body.get("msg");
            if (msgMap.get("existedCustomer") == null) {// 省份系统没有查询到数据才会再调用核心系统
                msgMap.put("route", "99");
                preParam(exchange);
                aopCall(exchange);
                urlDecode(exchange);
                Xml2Json4Cust(exchange);
                exchange.getOut().getBody(Map.class).put("sysType", "2");
            }
            else {
                exchange.getOut().getBody(Map.class).put("sysType", "1");
            }
        }

    }

    private void urlDecode(Exchange exchange) {
        String urlString = (String) exchange.getOut().getBody();
        try {
            urlString = URLDecoder.decode(urlString, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new EcAopRequestBizException(e.getMessage());
        }
        exchange.getOut().setBody(urlString);
    }

    private void Xml2Json4Cust(Exchange exchange) throws Exception {
        Xml2Json4CustMappingProcessor X2J = new Xml2Json4CustMappingProcessor();
        String[] chkUsrStr = { "ecaop.masb.cuck.template" };
        X2J.applyParams(chkUsrStr);
        X2J.process(exchange);
    }

    private void aopCall(Exchange exchange) throws Exception {
        AopCall call = new AopCall();
        call.applyParams(new String[] { FBS_URL });
        call.process(exchange);

    }

    private void preParam(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Object msg = body.get("msg");
        if (msg instanceof String) {
            Map msgMap = JSON.parseObject((String) body.get("msg"));
            body.put("msg", msgMap);
        }

        ParametersMappingProcessor pmp = new ParametersMappingProcessor();
        paraKey = "ecaop.masb.cuck.ParametersMapping";
        String[] parametersMapper = { paraKey };
        pmp.applyParams(parametersMapper);
        pmp.process(exchange);

    }

    @Override
    public void applyParams(String[] params) {

    }

}
