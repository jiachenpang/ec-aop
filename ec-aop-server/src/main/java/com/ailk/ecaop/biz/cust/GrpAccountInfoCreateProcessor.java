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

@EcRocTag("GrpAccountInfoCreate")
public class GrpAccountInfoCreateProcessor extends BaseAopProcessor implements ParamsAppliable {

    LanUtils lan = new LanUtils();
    private static final String[] PARAM_ARRAY = { "ecaop.curt.grar.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void process(Exchange exchange) throws Exception {

        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        } else {
            msg = (Map) body.get("msg");
        }

        Map accInfo = null;
        boolean isList = msg.get("accInfo") instanceof List;
        if (isList) {
            if (((List) msg.get("accInfo")).size() != 1) {
                throw new EcAopServerBizException("9999", "帐户信息accInfo有且只能有一个");
            }
            accInfo = (Map) ((List) msg.get("accInfo")).get(0);
        } else {
            accInfo = (Map) msg.get("accInfo");
        }
        //System.out.println("accInfo.size():"+accInfo.size());
        if (null == accInfo || accInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "未传帐户信息accInfo");
        }
        accInfo.put("province", msg.get("province"));
        accInfo.put("depId", msg.get("depId"));
        accInfo.put("operatorId", msg.get("operatorId"));
        if (null != msg.get("city")) {
            accInfo.put("city", msg.get("city"));
        }
        if (null != msg.get("district")) {
            accInfo.put("district", msg.get("district"));
        }
        msg.put("accInfo", accInfo);
        msg.put("channelId", msg.get("depId"));
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        try {
            lan.preData(pmp[0], exchange);//ecaop.curt.grar.ParametersMapping
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.grpAccountAOPSer");
            lan.xml2Json("ecaop.fbs.grar.template", exchange);
        } catch (EcAopServerBizException e) {
            /**
             * 应答编码
            0001: 账户已存在
            8888：其它错误 
             * 
             * */
            throw new EcAopServerBizException("0001".equals(e.getCode()) ? "0001" : "8888", getErrorMsg(exchange, e));
        } catch (EcAopServerSysException e) {
            /**返回编码，固定长度4位，取值范围：
            0001：网络超时
            0900：系统异常类
            0901：数据库连接异常。
            9999：其他错误（错误原因）*/
            throw new EcAopServerSysException("0001".equals(e.getCode()) || "0900".equals(e.getCode())
                    || "0901".equals(e.getCode()) ? e.getCode() : "9999", getErrorMsg(exchange, e));
        } catch (Exception e) {
            throw new EcAopServerSysException("9999", getErrorMsg(exchange, e));
        }
        Map out = exchange.getOut().getBody(Map.class);
        String code = (String) out.get("code");
        if (!"0000".equals(code) && !"0001".equals(code)) {
            code = "8888";
        }
        String detail = out.get("detail") + "";
        out.put("detail", detail.length() > 200 ? detail.substring(0, 200) : detail);
        out.put("code", code);
        //Map respInfo=(Map) out.get("respInfo");
        //Map newRespInfo=new HashMap();
        //newRespInfo.put("accId", respInfo.get("accId"));
        out.remove("moreDetail");
        out.put("respInfo", new HashMap().put("accId", ((Map) out.get("respInfo")).get("accId")));
        exchange.getOut().setBody(out);
    }

    private String getErrorMsg(Exchange exchange, Exception e) {
        Map out = exchange.getOut().getBody(Map.class);
        String detail = "";
        if (null != out && out.size() > 0) {
            detail = out.get("detail") + "";
        }
        return "".equals(detail) ? e.getMessage() : detail;
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
