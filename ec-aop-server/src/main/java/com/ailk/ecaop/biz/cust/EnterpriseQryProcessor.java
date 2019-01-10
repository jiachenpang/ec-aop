package com.ailk.ecaop.biz.cust;

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

@EcRocTag("EnterpriseQry")
public class EnterpriseQryProcessor extends BaseAopProcessor implements ParamsAppliable {

    LanUtils lan = new LanUtils();
    private static final String[] PARAM_ARRAY = { "ecaop.query.qceq.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void process(Exchange exchange) throws Exception {

        try {
            lan.preData(pmp[0], exchange);//ecaop.query.qceq.ParametersMapping
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.inquiryEnterpriseInfoSer");
            lan.xml2Json("ecaop.fbs.qceq.template", exchange);
        } catch (EcAopServerBizException e) {
            /**
             * 应答编码
            0001: 应商名称、组织机构代码、注册号，都为空
            0002：接收供应商名称、注册号或组织机构代码有误
            0003：接收该组织机构未核查到
            0005：组织代码中心返回错误
            8888：其它错误
             * */
            if ("0004".equals(e.getCode())) {
                throw new EcAopServerSysException("0001", e.getMessage());
            }
            throw new EcAopServerBizException("0001".equals(e.getCode()) || "0002".equals(e.getCode())
                    || "0003".equals(e.getCode()) || "0005".equals(e.getCode()) ? e.getCode() : "8888", e.getMessage());
        } catch (EcAopServerSysException e) {
            /**
             * 返回编码，固定长度4位，取值范围：
            0001：网络超时
            0900：系统异常类
            0901：数据库连接异常。
            9999：其他错误（错误原因）
             * 
             * */
            throw new EcAopServerSysException("0001".equals(e.getCode()) || "0900".equals(e.getCode())
                    || "0901".equals(e.getCode()) ? e.getCode() : "9999", e.getMessage());
        } catch (Exception e) {
            throw new EcAopServerSysException("9999", e.getMessage());
        }
        Map out = exchange.getOut().getBody(Map.class);
        String detail = out.get("detail") + "";
        out.put("detail", detail.length() > 200 ? detail.substring(0, 200) : detail);
        out.remove("moreDetail");
        String code = (String) out.get("code");
        if ("0004".equals(code)) {
            throw new EcAopServerSysException("0001", detail);
        }
        if (!("0001".equals(code) || "0002".equals(code) || "0003".equals(code) || "0005".equals(code))) {
            code = "8888";
        }
        out.put("code", code);
        exchange.getOut().setBody(out);
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
