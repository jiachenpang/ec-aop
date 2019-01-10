package com.ailk.ecaop.biz.cust;

import java.util.ArrayList;
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
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("custInfoBindOrderForEss")
public class CustInfoBindOrderForEss extends BaseAopProcessor implements ParamsAppliable {

    LanUtils lan = new LanUtils();
    private static final String[] PARAM_ARRAY = { "ecaop.trades.scob.CustInfoBindOrder.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];

    @SuppressWarnings({ "rawtypes", "static-access", "unchecked" })
    @Override
    public void process(Exchange exchange) throws Exception {
        System.out.println("CustInfoBindOrderForEss###");
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = null;
        if (isString)
        {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else
        {
            msg = (Map) body.get("msg");
        }
        // 证件转换
        List<Map> custInfo = (List) msg.get("custInfo");
        List<Map> newInfo = null;
        if (custInfo != null && custInfo.size() > 0)
        {
            newInfo = new ArrayList();
            for (Map info : custInfo)
            {
                String certType = (String) info.get("certType");
                info.put("certType", new CertTypeChangeUtils().certTypeMall2Fbs(certType));
                newInfo.add(info);
            }
        }
        msg.put("custInfo", newInfo);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        try
        {
            lan.preData(pmp[0], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.RealNameCardSer");
            lan.xml2Json("ecaop.scob.ess.template", exchange);
        }
        catch (EcAopServerBizException e)
        {
            /**
             * 应答编码：
            0000 成功
            1017客户已存在
            8888其他错误
            */
            throw new EcAopServerBizException("8888".equals(e.getCode()) ? "9999" : e.getCode(), e.getMessage());
        }
        catch (EcAopServerSysException e)
        {
            /**返回编码，固定长度4位，取值范围：
            0001：网络超时
            0900：系统异常类
            0901：数据库连接异常。
            9999：其他错误（错误原因））*/
            throw new EcAopServerSysException("8888".equals(e.getCode()) ? "9999" : e.getCode(), e.getMessage());
        }
        catch (Exception e)
        {
            throw new EcAopServerSysException("9999", e.getMessage());
        }
        dealReturn(exchange);
        System.out.println("CustInfoBindOrderForEss###2");
    }

    /**
     * 0001: 客户资料实名制校验未通过
     * 8888：其它错误
     * */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void dealReturn(Exchange exchange) {
        Map body = exchange.getOut().getBody(Map.class);
        System.out.println("CustInfoBindOrderForEss###3"+body.toString());
        List<Map> respInfo = (List) body.get("respInfo");
        Map out = null;
        if (null != respInfo && respInfo.size() > 0)
        {
            String custId = (String) respInfo.get(0).get("custId");
            if (null != custId && !"".equals(custId))
            {
                out = new HashMap();
                out.put("custId", custId);
            }
        }
        if (null == out)
        {
            Map inbody = exchange.getIn().getBody(Map.class);
            throw new EcAopServerBizException("9999", "bindCustAndTrade接口返回:省份未返回custId;TRANSIDO为:" + inbody.get("transIDO"));
        }
        exchange.getOut().setBody(out);
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++)
        {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
