package com.ailk.ecaop.biz.cust;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("crpcustinfoCreate")
public class GrpcustinfoCreateProcessor extends BaseAopProcessor implements ParamsAppliable {

    LanUtils lan = new LanUtils();
    private static final String[] PARAM_ARRAY = { "ecaop.curt.grcr.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];

    @Override
    public void process(Exchange exchange) throws Exception {
        preParam(exchange.getIn().getBody(Map.class));
        try {
            lan.preData(pmp[0], exchange);// ecaop.curt.grcr.ParametersMapping
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.grpCustInfoAOPSer");
            lan.xml2Json("ecaop.fbs.grcr.template", exchange);
        }
        catch (EcAopServerBizException e) {
            e.printStackTrace();
            /**
             * 应答编码
             * 0001: 客户资料实名制校验未通过
             * 8888：其它错误
             */
            throw new EcAopServerBizException("0001".equals(e.getCode()) ? "0001" : "8888", e.getMessage());
        }
        catch (EcAopServerSysException e) {
            /**
             * 返回编码，固定长度4位，取值范围：ecaop.fbs.grcr.template
             * 0001：网络超时
             * 0900：系统异常类
             * 0901：数据库连接异常。
             * 9999：其他错误（错误原因）
             */
            e.printStackTrace();
            throw new EcAopServerSysException("0001".equals(e.getCode()) || "0900".equals(e.getCode())
                    || "0901".equals(e.getCode()) ? e.getCode() : "9999", e.getMessage());
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerSysException("9999", e.getMessage());
        }
        dealReturn(exchange);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void preParam(Map body) {
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        System.out.println("msg:" + msg);
        Map custInfo = null;
        boolean isList = msg.get("custInfo") instanceof List;
        if (isList) {
            if (((List) msg.get("custInfo")).size() != 1) {
                throw new EcAopServerBizException("9999", "客户信息custInfo有且只能有一个");
            }
            custInfo = (Map) ((List) msg.get("custInfo")).get(0);
        }
        else {
            custInfo = (Map) msg.get("custInfo");
        }
        custInfo.put("province", msg.get("province"));
        custInfo.put("city", msg.get("city"));
        if (null != msg.get("district")) {
            custInfo.put("district", msg.get("district"));
        }
        msg.put("custInfo", custInfo);
        body.put("msg", msg);
    }

    /**
     * 0001: 客户资料实名制校验未通过
     * 8888：其它错误
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void dealReturn(Exchange exchange) {
        Map body = exchange.getOut().getBody(Map.class);
        body.remove("moreDetail");
        exchange.getOut().setBody(body);
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
