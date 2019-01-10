package com.ailk.ecaop.biz.cust;

import com.ailk.ecaop.common.utils.CheckCertCodeUtils;
import com.taobao.gecko.core.util.StringUtils;
import org.eclipse.jetty.util.ajax.JSON;
import org.n3r.config.Config;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import java.util.Map;

/**
 * 根据接入系统判断验证类型
 * 
 * @author Lionel
 */

@EcRocTag("CertCheckType")
public class CertCheckTypeProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        String methodCode = exchange.getMethodCode();

        //Map<String, String> typeConfig = (Map) JSON.parse(Config.getStr("ecaop." + methodCode + ".params.config.type"));
        Map<String, String> sysCodeConfig = (Map) JSON.parse(Config.getStr("ecaop." + methodCode
                + ".params.config.syscode"));
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        CheckCertCodeUtils cccu = new CheckCertCodeUtils();
        cccu.CheckCertCode(msg.get("certId").toString());

        /*String type = typeConfig.get(exchange.getAppCode());
        body.put("type", type == null ? "03" : type);*/
        String certType=(String)msg.get("certType");
        if(StringUtils.isEmpty(certType)||"01".equals(certType)){
            body.put("type","03");
        }else{
            body.put("type","02");
        }
        body.put("sysCode", sysCodeConfig.get(exchange.getAppCode()));
    }
}
