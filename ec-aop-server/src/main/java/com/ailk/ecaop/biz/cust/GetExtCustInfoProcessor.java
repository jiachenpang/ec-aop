package com.ailk.ecaop.biz.cust;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.MapUtils;

/**
 * 把枢纽返回的证件类型编码转换成商城需要的证件类型编码(双方规范不一致)
 * 
 * @author yt
 */
@EcRocTag("getExtCustInfo")
public class GetExtCustInfoProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map<String, Object> out = exchange.getOut().getBody(Map.class);
        Object code = out.get("code");
        if (null != code && "0001".equals(code.toString())) {
            throw new EcAopServerBizException("0001", out.get("detail").toString());
        }
        List<Map> custInfoList = (List<Map>) out.get("existedCustomer");
        if (custInfoList == null || custInfoList.isEmpty()) {
            return;
        }
        Map custMap = new HashMap();
        MapUtils.arrayPut(custMap, custInfoList.get(0), new String[] { "certType", "certNum", "certAdress", "custId",
                "customerType", "customerLevel", "customerLoc", "customerName", "certExpireDate", "maxUserFlag",
                "contactPerson", "contactPhone", "arrearageFlag", "arrearageMess", "groupId", "para", "blackListFlag" });
        exchange.getOut().setBody(custMap);
    }

}
